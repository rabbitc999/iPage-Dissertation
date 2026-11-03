# 05 · API 参考

全部接口以 `/api` 为前缀，返回 `application/json`，字符集 UTF-8。

- 除 `POST /api/auth/login` 与 `GET /api/health` 外，**都需要** `Authorization: Bearer <token>`；
- 错误统一为 `{"message": "中文提示", "status": 403}`；
- 主键一律是 UUID 字符串。

---

## 1. 认证

### 1.1 登录

```http
POST /api/auth/login
Content-Type: application/json

{ "username": "admin", "password": "admin123" }
```

```json
{
  "token": "eyJ1aWQiOiI...",
  "user": {
    "id": "0a488ea4-718e-494c-985e-64d8f8b1f318",
    "username": "admin",
    "name": "林一",
    "level": 4,
    "dataScope": "ALL",
    "deptId": "8aa3106f-a14e-4eff-9390-1c633815496e",
    "isAdmin": true,
    "permissions": ["ai:manage", "ai:use", "system:audit:view", "system:role:manage", "system:user:manage"]
  }
}
```

失败：`401 {"message":"账号或密码不正确"}`；停用账号：`403 {"message":"账号已停用，请联系管理员"}`。

### 1.2 当前用户

```http
GET /api/auth/me
```
返回结构同上 `user`。每次从库里重建，权限变更后刷新即可看到。

### 1.3 修改自己的密码

```http
POST /api/auth/password

{ "oldPassword": "admin123", "newPassword": "新密码至少6位" }
```
成功返回 `{"token": "..."}`（**旧令牌已失效，必须换成新令牌**）。

### 1.4 退出登录

```http
POST /api/auth/logout
```
令该用户所有已签发令牌失效（`token_version + 1`）。

### 1.5 健康检查

```http
GET /api/health          # 免认证
→ {"status":"ok"}
```

---

## 2. 元数据

### 2.1 侧边菜单

```http
GET /api/nav
```
```json
[
  { "key": "aiMcpServer", "name": "MCP 服务", "icon": "fa-plug", "group": "智能助手" },
  { "key": "employee",    "name": "员工",     "icon": "fa-user-tie", "group": "业务数据" }
]
```
只包含当前用户**有查看权限**的模型；分组由表名前缀推导。

### 2.2 模型 schema

```http
GET /api/models/{key}/schema
```
```json
{
  "key": "department",
  "name": "部门",
  "table": "ims_department",
  "icon": "fa-sitemap",
  "group": "业务数据",
  "idField": "id",
  "permissions": { "view": true, "create": true, "edit": true, "delete": true },
  "fields": [
    {
      "name": "code", "column": "dept_code", "text": "编码", "type": "STRING",
      "order": 2, "fixed": false, "required": true, "maxLength": 32, "pattern": "",
      "table": true, "form": true, "sortable": true
    },
    {
      "name": "status", "column": "status", "text": "状态", "type": "ENUM",
      "options": { "ENABLED": "启用", "DISABLED": "停用" }, "table": true, "form": true
    },
    {
      "name": "deptId", "column": "dept_id", "text": "部门", "type": "ASSOCIATION_ID",
      "target": "department", "options": { "8ea8...": "财务部", "c1b8...": "技术部" }
    },
    {
      "name": "employeeCount", "type": "AGGREGATION", "text": "人数",
      "table": true, "form": false, "sortable": true
    }
  ],
  "searchFields": ["code", "name", "leader", "phone", "remark"],
  "defaultSort": { "field": "id", "direction": "desc" }
}
```

字段属性说明：

| 属性 | 含义 |
| --- | --- |
| `type` | `MODEL_ID / STRING / NUMBER / ENUM / DATE / BOOLEAN / ASSOCIATION_ID / ASSOCIATION_MODEL / AGGREGATION`，另有虚拟的 `PASSWORD` / `MULTI_SELECT` |
| `required` | 有 `@ValidatorNotNull` |
| `maxLength` | `-1` 表示不限 |
| `pattern` | 正则字符串，空表示无 |
| `table` / `form` | 是否出现在表格列 / 表单 |
| `sortable` | 是否可排序 |
| `options` | 枚举或关联下拉的选项 `{值: 中文}` |
| `target` | 关联字段指向的模型 key |

---

## 3. 通用 CRUD

所有模型共用（`{key}` 为模型 key）。

### 3.1 分页查询

```http
GET /api/models/{key}?page=1&size=20&sort=name&order=asc&kw=陈&f_status=ENABLED&op_status=EQ
```

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `page` | 1 | 页码 |
| `size` | 20 | 每页条数，上限 200 |
| `sort` | 主键 | 排序字段（Java 字段名，非列名） |
| `order` | `desc` | `asc` / `desc` |
| `kw` | — | 关键字，匹配所有可见字符串字段 |
| `f_{字段}` | — | 精确/模糊过滤值 |
| `op_{字段}` | 由字段类型推断 | 操作符，见 3.2 |

```json
{
  "rows": [
    {
      "id": "95131f9c-4be7-43d6-bb18-7dcb59c73be1",
      "_display": "周然",
      "empNo": "E1006",
      "name": "周然",
      "deptId": "8ea8ddf5-f7ea-4cf4-b591-ea1147a9cd36",
      "dept": { "id": "8ea8ddf5-...", "label": "财务部" },
      "hireDate": "2019-11-04",
      "status": "ENABLED",
      "statusText": "启用"
    }
  ],
  "total": 6, "page": 1, "size": 20, "pages": 1
}
```

行数据的约定：

- `id`：主键；
- `_display`：`@ModelClass(text=...)` 渲染出的显示名；
- `{字段}Text`：枚举的中文名，与枚举名成对出现；
- `{关联字段}`：`{id, label}` 对象（对应 `ASSOCIATION_MODEL`）；
- 日期统一格式化为 `yyyy-MM-dd` / `yyyy-MM-dd HH:mm:ss`；
- `look=false` 与 `lookPermission` 不满足的字段**不会出现**。

### 3.2 操作符

| `op_` 值 | 语义 | 备注 |
| --- | --- | --- |
| `EQ` / `NE` | 等于 / 不等于 | 默认 |
| `LIKE` / `NOT_LIKE` | 包含 / 不包含 | 仅字符串 |
| `GT` / `GE` / `LT` / `LE` | 比较 | 数值、日期 |
| `IN` | 在集合中 | 值用逗号分隔 |

### 3.3 单条

```http
GET /api/models/{key}/{id}
```
不存在返回 `404 {"message":"员工 不存在：xxx"}`；超出数据范围返回 `403`。

### 3.4 新增

```http
POST /api/models/{key}
Content-Type: application/json; charset=utf-8

{ "empNo": "E2001", "name": "新员工", "deptId": "<部门uuid>", "status": "ENABLED" }
```
返回新增后的整行（含服务端生成的 `id`）。

`createBy` / `createdAt` 由服务端自动填充；主键由服务端生成；请求体里的 `id` 会被忽略。

### 3.5 修改

```http
PUT /api/models/{key}/{id}

{ "position": "技术总监" }
```
**只更新出现的字段**（未出现的保持原值）。整行返回。

### 3.6 删除

```http
DELETE /api/models/{key}/{id}
→ {"ok": true}
```

### 3.7 批量删除

```http
POST /api/models/{key}/batch-delete

{ "ids": ["uuid1", "uuid2"] }
→ {"deleted": 2}
```
逐条走完整删除流程（含钩子与审计），任何一条失败即中断。

---

## 4. 界面配置

只作用于**当前登录用户**，管理员也不能改别人的。

```http
GET /api/views/{modelKey}
→ {"config": {"columns": ["name","dept"], "size": 50}}

PUT /api/views/{modelKey}
{ "columns": ["name"], "size": 50 }
→ {"config": {...}}
```

配置是自由 JSON，服务端不解释语义，只按用户+模型存取（上限 240 字符）。

---

## 5. 角色与权限

写操作需要 `system:role:manage`；读操作需要 `ADMIN` 等级。

```http
GET    /api/roles
→ [{"id":"...","code":"ADMIN","name":"系统管理员","level":3,"dataScope":"ALL","builtin":true,"remark":"..."}]

POST   /api/roles
{ "code": "AUDITOR", "name": "审计员", "level": 2, "dataScope": "DEPT", "remark": "只读" }
→ {"id":"...","code":"AUDITOR","name":"审计员"}

DELETE /api/roles/{id}                 # 内置角色(builtin=true)不允许删除

GET    /api/permissions
→ [{"id":"...","code":"ai:use","name":"使用 AI 助手","group":"智能助手","remark":null}]

GET    /api/roles/{id}/permissions
→ {"permissionIds": ["uuid1","uuid2"]}

PUT    /api/roles/{id}/permissions
{ "permissionIds": ["uuid1"] }          # 整体替换（先删后插）
```

`level` 取值 0–4，对应 `STUDENT/NORMAL/TEACHER/ADMIN/SUPER_ADMIN`；
`dataScope` 取 `ALL / DEPT / SELF`。

---

## 6. 工作台统计

```http
GET /api/stats
```
```json
{
  "cards": [
    { "key": "employee", "name": "员工", "icon": "fa-user-tie", "count": 6 }
  ],
  "tickets": [ { "status": "OPEN", "text": "待处理", "count": 2 } ],
  "recent":  [ { "action": "CREATE", "model": "员工", "at": "2026-11-03 23:22:01" } ]
}
```
`count` 已按当前用户的数据范围统计；无查看权限的模型不出现。

---

## 7. AI

### 7.1 状态

```http
GET /api/ai/status
```
```json
{
  "enabled": false, "ready": false, "provider": "openai", "model": "deepseek-chat",
  "hasKey": false, "allowTools": true,
  "reason": "AI 未启用：设置环境变量 IMS_AI_ENABLED=true 与 IMS_AI_KEY 后重启",
  "tools": [], "mcp": [], "skills": []
}
```
启用后 `tools` 会列出 8 个 `ims_*` 工具与已挂载的 MCP 工具。

### 7.2 对话

```http
POST /api/ai/chat          # 需要 ai:use
{ "message": "现在有多少资产？", "allowTools": true }
→ {
    "reply": "当前共有 8 条资产记录……",
    "audit": [ { "tool": "ims_stats", "arguments": {...}, "ok": true } ],
    "tools": ["ims_list_models", "ims_query", "..."]
  }
```

会话按用户隔离；`allowTools = false` 时本轮只做纯文本回答，不调用工具。
模型调用失败返回 `502 {"message":"模型调用失败：..."}`。

### 7.3 历史 / 重置 / 重载

```http
GET  /api/ai/history     → {"version":1,"messages":[...]}   # 当前用户会话归档
POST /api/ai/reset       → 清空当前用户对话
POST /api/ai/reload      → 重新装配 MCP 与 Skill（需要 ai:manage）
```

---

## 8. 用 curl 快速联调

```bash
BASE=http://127.0.0.1:8080

TOKEN=$(curl -s -X POST $BASE/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")

curl -s $BASE/api/nav -H "Authorization: Bearer $TOKEN"

# 中文参数一定要 URL 编码，否则会 400/乱码
curl -s "$BASE/api/models/employee?kw=%E8%8B%8F" -H "Authorization: Bearer $TOKEN"

# 中文请求体建议写文件再 --data-binary，避免终端编码问题
curl -s -X POST $BASE/api/models/employee \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data-binary @body.json
```

> 在 Windows 的 Git Bash 里直接用 `-d '{"name":"中文"}'` 可能传出非 UTF-8 字节，
> 服务端会报 `Invalid UTF-8 start byte`。用文件 + `--data-binary` 最稳。
