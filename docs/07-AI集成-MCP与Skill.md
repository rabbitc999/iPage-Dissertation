# 07 · AI 集成：MCP 与 Skill

> AI 是**可选**能力：不配置 `IMS_AI_KEY` 时整个模块静默关闭，其余功能不受影响。
> 核心设计原则只有一句：**AI 与页面走同一套权限，AI 不能绕过任何校验。**

---

## 1. 架构

```
浏览器  AiView.vue
   │ POST /api/ai/chat  {message, allowTools}
   ▼
AiController（校验 ai:use）
   ▼
AiEngineManager
   ├─ sessions: Map<userId, Session>        ← 按用户隔离，串行处理
   └─ Session
        ├─ EmbedAiChat.Engine              ← 上游框架
        │    ├─ 8 个 ims_* 工具（本仓库注册）
        │    ├─ MCP 工具（外部进程，stdio JSON-RPC）
        │    └─ Skill（纯文本指令，不执行脚本）
        └─ McpClient × N                   ← 每个启用的 MCP 一个子进程
   ▼
CrudService（用当前 LoginUser）
   ▼
权限 / 数据范围 / 审计  ← 与页面完全一致
```

---

## 2. 开关与配置

```yaml
ims:
  ai:
    enabled: ${IMS_AI_ENABLED:false}
    provider: ${IMS_AI_PROVIDER:openai}
    base-url: ${IMS_AI_BASE_URL:https://api.deepseek.com/v1}
    model: ${IMS_AI_MODEL:deepseek-chat}
    api-key: ${IMS_AI_KEY:}
    max-tokens: 2048
    context-budget: 16000
    allow-tools: true
    work-dir: ./data/ai
```

| 项 | 说明 |
| --- | --- |
| `provider` | 目前按 OpenAI 协议发送；换 DeepSeek / 通义 / 本地 vLLM 只改 `base-url` + `model` |
| `api-key` | 空则 `ready()` 为 false，接口返回 501 并给出中文原因 |
| `context-budget` | 历史消息裁剪预算（token），超出时丢最早的 |
| `allow-tools` | 全局工具开关；单个请求还可以再关（`allowTools:false`） |
| `work-dir` | 内置 Skill 释放目录，默认 `server/data/ai` |

---

## 3. 内置工具一览

8 个工具全部注册在 `AiEngineManager.registerTools()`，全部转调 `CrudService`：

| 工具 | 参数 | 作用 |
| --- | --- | --- |
| `ims_list_models` | — | 列出当前用户可访问的模型 |
| `ims_model_schema` | `model` | 取字段定义（类型、必填、枚举选项） |
| `ims_query` | `model`, `page`, `size` | 分页查询 |
| `ims_get` | `model`, `id` | 按主键取一条 |
| `ims_create` | `model`, `values` | 新增 |
| `ims_update` | `model`, `id`, `values` | 修改（只改传入字段） |
| `ims_delete` | `model`, `id` | 删除 |
| `ims_stats` | — | 各模型可见行数 |

`id` 参数在 schema 里声明为 **string（UUID）**，工具调用时用 `args.get("id").getAsString()` 读取。

### 为什么把 CRUD 做成工具

1. **不用为 AI 另写一套数据访问**，避免两套逻辑不一致；
2. 权限、数据范围、审计自动生效——AI 用 staff 账号登录，就只看得见 staff 该看的数据；
3. 错误信息（"没有编辑「员工」的权限"）能原样回给模型，模型会如实转述而不是编造。

---

## 4. MCP：挂接外部工具

### 4.1 配置界面

「智能助手 → MCP」新增一行：

| 字段 | 说明 | 示例 |
| --- | --- | --- |
| `name` | 显示名 | 示例 MCP 服务 |
| `prefix` | 工具名前缀，避免冲突 | `demo` |
| `command` | **JSON 数组**形式的启动命令 | `["python","mcp/demo_server.py"]` |
| `timeoutMs` | 单次调用超时 | `15000` |
| `enabled` | 是否启用 | `false`（演示默认关） |

### 4.2 命令为什么是 JSON 数组

命令**不经过 shell**，直接 `exec` argv。好处：
- 没有命令注入风险；
- 路径带空格也不用转义；
- 行为在 Windows / Linux 上一致。

所以 `["sh","-c","cd x && python y.py"]` 这种写法虽然能用，但不推荐——把逻辑放进脚本里更清晰。

### 4.3 协议与生命周期

- 传输：stdio，JSON-RPC 2.0；
- 协议版本协商：`2025-11-25` / `2025-06-18` / `2024-11-05`；
- 握手：`initialize` → `notifications/initialized` → `tools/list`；
- 每个启用的 MCP 在每个用户会话里起一个子进程；会话关闭时 `close()`；
- 启动失败不影响其他服务，失败原因写进 `/api/ai/status` 的 `mcp[].error`。

### 4.4 自带的演示服务

`server/mcp/demo_server.py`，实现 `initialize` / `tools/list` / `tools/call`，提供三个工具：
`ping`、`add`、`now`。启用后工具名变成 `demo_ping`、`demo_add`、`demo_now`。

启用步骤：
1. 确认本机有 `python`（Windows 用 `python`，Linux 常用 `python3`）；
2. 把 `command` 改成 `["python","mcp/demo_server.py"]`；
3. 勾选启用，保存 → 引擎自动作废旧会话；
4. 回到「工具」标签页应能看到 `demo_*`。

### 4.5 配置热生效

`AiConfigHook` 监听 MCP/Skill 表的变化，发出 `AiConfigChangedEvent`；
`AiEngineManager.onConfigChanged` 调 `invalidateAll()`：`generation + 1`，关闭所有会话的子进程，
下次对话时按新配置重建。**不需要重启服务。**

---

## 5. Skill：给模型的操作手册

### 5.1 是什么

Skill 是**纯文本指令**（`SKILL.md`），加载后注入上下文，告诉模型"在这个系统里该怎么做事"。
它**不执行脚本**、不带代码，只是一个结构化的提示词包。

为什么需要它：模型默认不知道"改数据前要先看 schema""删除要二次确认"，
这些规则写进 Skill 比写进每次对话更可靠。

### 5.2 目录结构

```
<work-dir>/skills/ims-assistant/SKILL.md
```

`work-dir` 默认 `server/data/ai`。内置 Skill 在打包时放在
`server/src/main/resources/ai/skills/ims-assistant/SKILL.md`，
启动时由 `prepareWorkDir()` 释放到磁盘（已存在则跳过），所以首次启动开箱就能用。

### 5.3 数据库配置

`ai_skill` 表：

| 字段 | 说明 |
| --- | --- |
| `name` | 名称（也用于激活时引用） |
| `directory` | SKILL.md 所在目录的**绝对路径** |
| `enabled` | 是否加载 |
| `autoActivate` | 加载后是否立即激活 |

### 5.4 内置规则（节选）

`ims-assistant` 的 7 条操作规则：

1. 先看结构再动手（`ims_model_schema`）；
2. 不编造主键，找不到就如实说；
3. 删除前必须二次确认；
4. 改完回读一次确认；
5. 批量操作拆成多次单条；
6. 权限不足时如实说明，不要绕过；
7. 不泄露敏感字段（密码等）。

> 写自己的 Skill 时，规则要**可执行、可验证**（"先调 X 再调 Y"），
> 而不是"要谨慎"这种无法判定的话。

---

## 6. 扩展工具

见 [AGENTS.md 第 3 节 G](../AGENTS.md)。要点：

- 工具名 `ims_` 前缀；
- 描述用英文，参数用 JSON Schema，`additionalProperties: false`；
- 必须用传入的 `user` 调 `CrudService`，**不要构造管理员身份**；
- 工具返回 JSON 字符串，失败时抛出可读的错误信息。

---

## 7. 安全模型

| 风险 | 处置 |
| --- | --- |
| 越权读写 | 工具走 `CrudService` + 当前 `LoginUser`，无旁路 |
| 危险操作 | Skill 规则要求删除前确认；`ims_delete` 仍需服务端删除权限 |
| 密钥泄漏 | `api-key` 只从环境变量读，不写进代码、不进 `/api/ai/status`（只回 `hasKey`） |
| MCP 命令注入 | argv 数组直连 `exec`，不过 shell |
| 上下文污染 | 每用户独立会话，`reset` 可清空 |
| 资源泄漏 | 会话作废时关闭所有 MCP 子进程 |

**注意**：AI 的输出**不是可信输入**。如果将来让 AI 生成的 SQL/脚本被执行，必须再过一层白名单——
当前的 `ims_*` 工具只传递结构化参数给服务层，没有这个问题。

---

## 8. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `/api/ai/status` 返回 `enabled:false` | 没设 `IMS_AI_ENABLED` | 设环境变量后重启 |
| `ready:false` 且 `reason` 提示缺 Key | `IMS_AI_KEY` 为空 | 填 Key |
| `mcp[].ok:false` | 命令错、Python 不存在、脚本路径不对 | 看 `error`；命令里用绝对路径更稳 |
| 模型说"没有工具" | `allow-tools:false` 或本轮 `allowTools:false` | 检查两处开关 |
| 改了 MCP 配置没生效 | 保存后未触发事件（直接改库） | 通过界面改，或调 `POST /api/ai/reload` |
| 回答很慢 | 用了大模型 + 长上下文 | 调小 `max-tokens` / `context-budget` |
