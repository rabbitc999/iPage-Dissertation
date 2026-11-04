# 07 · AI 集成：MCP 与 Skill

> AI 是**可选**能力：新安装时模块默认关闭，其余功能不受影响。
> 核心设计原则只有一句：**AI 与页面走同一套权限，AI 不能绕过任何校验。**

---

## 1. 架构

```
浏览器  AiView.vue
   │ POST /api/ai/chat/stream  {message, model, toolAccess, ...}
   ▼
AiController（校验 ai:use）
   ▼
AiEngineManager
   ├─ sessions: Map<userId, Session>        ← 按用户隔离，串行处理
   └─ Session
        ├─ EmbedAiChat.Engine              ← 上游框架
        │    ├─ ims_* 宿主工具（业务和文件）
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

管理员打开「系统管理 → AI 模型管理」，为每个模型填写供应商模型 ID、OpenAI 兼容或 Anthropic 协议、服务地址、API Key 和最低角色等级。保存模型后点击「启用助手」。更新、停用或删除模型都会使旧会话失效，无需重启。

- 管理员可为密钥命名，例如「研发部专用」。可保留默认范围、仅允许所选部门或员工账号，也可禁止所选对象。部门和账号任一匹配即命中名单，`ai:use` 和模型最低等级仍须满足。对话下拉框、聊天与历史接口都按该范围检查。
- 新增模型时可选供应商预设自动填写协议与服务地址，再填写账号可用的模型 ID 和密钥。
- API Key 仅接受写入，编辑留空可保留旧密钥。AES-256-GCM 加密后入库，`sk-` 密钥保留前缀并加密后半段，其他令牌整体加密。直接填写完整密钥，认证请求头按模型协议生成。主密钥存放在 `ims.ai.work-dir/model-keys.bin`，默认 `./data/ai/model-keys.bin`。
- 管理页「关闭助手」隐藏入口并禁用对话接口。部署配置 `ims.ai.available: false` 还会移除模型管理入口及接口。
- `ims.ai.max-tokens`、`ims.ai.context-budget` 和 `ims.ai.work-dir` 仍是部署参数。

---

### 通过 MCP 上传业务附件

网页对话中，选择图片或文件、启用工具并选择「可添加」或更高权限，打开「文件写入」，然后指定要关联的工单或资产。更新字段需要「可添加编辑」或「所有权限」。内置工具 `ims_attach_record_file` 可同时修改经授权的记录字段。上传结果也能在业务列表对应记录的「附件」中查看、下载或解除关联。

外部 MCP 客户端可通过 stdio 启动仓库中的 `clients/cli/ims_mcp_attachments.py`。先用 `ims_cli.py login` 登录，或在客户端启动环境中传入当前用户的 `IMS_TOKEN`，然后配置命令：

```json
{
  "command": "python",
  "args": [
    "/path/to/iPage-IMS/clients/cli/ims_mcp_attachments.py",
    "--base", "http://127.0.0.1:8080",
    "--directory", "/path/to/approved-uploads",
    "--model-id", "IMS 模型配置 UUID",
    "--tool-access", "ADD_EDIT"
  ]
}
```

工具 `ims_upload_record_file` 参数为 `model`（`ticket` 或 `asset`）、`recordId`、相对于上传目录的 `path`，以及可选 `values`。例如 `{"model":"ticket","recordId":"工单 UUID","path":"现场图片.png","values":{"description":"已上传现场图"}}`。附件单个上限 10MB，服务端验证模型使用权限、工具权限上限、记录编辑权限和数据范围。文件写入、关联及字段更新在同一事务提交。

### 工具权限档位

管理员为模型设置工具权限上限。对话默认「仅读取」，可显式选择「可添加」「可添加编辑」或「所有权限」，最高档才允许删除，并继续按业务权限检查。越界时会显示黄色提示，说明本轮档位和所需档位。无法确认读写性质的外部 MCP 工具只在管理员以「所有权限」运行时挂载。旧模型的权限上限初始为「仅读取」。

## 3. 内置工具一览

主要宿主工具在 `AiEngineManager.registerTools()` 注册。业务记录读写走 `CrudService`，文件工具走附件或个人设置服务。

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
| `ims_upload_text_file` | `name`, `text` | 生成当前用户可下载的私有文本文件 |
| `ims_attach_record_file` | `model`, `recordId`, `fileId`, `values` | 将本轮附件关联到工单或资产，可同时更新记录 |
| `ims_update_my_avatar` | `id` | 使用本轮上传的图片更新当前用户头像 |

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
初始化 AI 会话时由 `prepareWorkDir()` 释放到磁盘，已有文件会保留。空库需要在 Skill 页面添加该目录并启用，才能加载该 Skill。

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
| 密钥存储 | 管理端写入密钥并加密入库，读取接口返回 `hasKey` 等元数据 |
| MCP 命令注入 | argv 数组直连 `exec`，不过 shell |
| 上下文污染 | 每用户独立会话，`reset` 可清空 |
| 资源泄漏 | 会话作废时关闭所有 MCP 子进程 |

**注意**：AI 的输出**不是可信输入**。如果将来让 AI 生成的 SQL/脚本被执行，必须再过一层白名单——
当前的 `ims_*` 工具只传递结构化参数给服务层，没有这个问题。

---

## 8. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `/api/ai/status` 返回 `enabled:false` | 模块未启用 | 检查部署开关，并在模型管理页启用助手 |
| `ready:false` 且提示没有可用模型 | 模型未启用或角色等级不足 | 在模型管理页检查可用模型与角色权限 |
| `mcp[].ok:false` | 命令错、Python 不存在、脚本路径不对 | 看 `error`；命令里用绝对路径更稳 |
| 模型说没有工具 | 模型禁用工具或本轮关闭工具 | 检查模型设置、对话开关和当前权限档位 |
| 改了 MCP 配置没生效 | 保存后未触发事件（直接改库） | 通过界面改，或调 `POST /api/ai/reload` |
| 回答很慢 | 用了大模型 + 长上下文 | 调小 `max-tokens` / `context-budget` |
