# AGENTS.md — 改造本系统的操作手册

> 面向后续接手这个仓库的 **AI 编码助手**和**人类开发者**。
> 这里写的不是"介绍"，而是**约束与流程**：按这里做，改动不会破坏现有设计；
> 不按这里做，大概率会踩到下面列出的坑。
>
> 动手前请先读一遍 [docs/01-架构总览.md](docs/01-架构总览.md) 和 [docs/02-数据模型.md](docs/02-数据模型.md)。
> 本文只讲"怎么改"，原理在 docs 里。

---

## 0. 一句话理解这个仓库

**一张表 = 一个 Java 模型类。** 加了模型，建表、CRUD 接口、前端列表页、表单、权限判断、审计日志，全部自动出现。
所以 90% 的需求都是"加/改模型类"，而不是"加 Controller / 加 Mapper / 加页面"。

```
model/Department.java   →  ims_department 表 + /api/models/department + 前端「部门」页
```

---

## 1. 先认清三层边界（改错层的代价最高）

| 层 | 位置 | 职责 | 什么时候改 |
| --- | --- | --- | --- |
| **框架层** | `libs/ipage/`、`libs/embed-ai-chat/` | 通用 SQL 引擎、AI 控制台 | **基本不要改**。它们是上游源码复制进来的，改了会和上游分叉，且受各自许可证约束 |
| **装配层** | `server/.../model`、`meta`、`service`、`security`、`web` | 把框架组装成业务系统 | 绝大多数需求改这里 |
| **展示层** | `web/src/` | schema 驱动的通用界面 | 只有"要一个非通用页面"时才改 |

> ⚠️ `libs/` 下的代码属于第三方（Apache-2.0 / AGPL-3.0）。确实要改时：在文件头写明"本文件已被 iPage-IMS 修改"，
> 并同步更新 THIRD-PARTY-NOTICES.md。优先考虑在 `server/` 侧用钩子/装饰解决。

---

## 2. 八条硬约束（违反会出安全或数据问题）

### 2.1 SQL 只能由 iPage 生成，不要手写拼接
如果确实需要自定义 SQL，必须遵守 iPage 的五条设计约束：

1. **标识符**（表名、列名、别名）一律走 `dialect.quote(...)`，不要字符串拼；
2. **值**一律走 `?` 占位符，绝不拼接；
3. **表名只有一个来源**：`ModelMeta.table()`（或 `sql.tableName(Class)`），不要在代码里写死 `"ims_employee"`；
4. **别名统一 `t_` 前缀**，避免和列名冲突；
5. **GROUP BY 必须覆盖所有非聚合选择列**，否则 MySQL 的 `ONLY_FULL_GROUP_BY` 会报错。

正例（`CrudService.applyKeyword` 就是这么写的）：

```java
String statement = "SELECT " + dialect.quote(meta.idColumn())
                 + " FROM " + dialect.quote(sql.tableName(meta.modelClass()))
                 + " WHERE " + dialect.quote(field.column()) + " LIKE ?";
sql.query(new SqlStatement(statement, List.of(pattern)), rs -> rs.getObject(1));
```

### 2.2 主键是 UUID 字符串，插入前必须由应用侧赋值
所有表的主键都是 `String`（UUID v4）。**iPage 只对整数主键做自增**，所以：

```java
Ids.assignIfAbsent(entity);   // 必须在 sql.insert(...) 之前调用
sql.insert(entity);
```

漏掉这一步的症状：`NULL not allowed for column "XXX_ID"`。
同理，所有外键字段（`deptId`、`createBy`、`assigneeId`…）都是 `String`，不要写 `Long`。

### 2.3 主键字段不要加 `fixed = true`
`fixed = true` 会让 iPage 认为主键由外部固定提供，从而关闭自增判定；本项目主键是 UUID，
一旦加上，插入会因主键为空失败。**主键只需 `type = FieldType.MODEL_ID`**。

（`applyBody()` 已经显式跳过 `MODEL_ID`，所以即便请求体里带了 `id` 也改不了主键，不需要靠 `fixed` 保护。）

### 2.4 权限默认拒绝
- 模型级：`@ModelClass(look=..., edit=..., delete=...)` 不写就是 `LEAST`（人人可读）；
- 字段级：敏感字段要 `look = false`（如 `passwordHash`）或 `editPermission = FORBIDDEN`；
- 行级：`DataScope` 的过滤在 `CrudService.applyScope()` 统一施加，**不要在某个 Controller 里另写一套**；
- 新增接口默认也要过 `AuthInterceptor`（`/api/**` 全拦），确需匿名访问要在 `WebConfig` 里显式排除并说明理由。

### 2.5 AI 工具不能绕过权限
`ims_*` 工具最终调用的都是 `CrudService`，用的是**当前登录用户**的 `LoginUser`。
不要为了"让 AI 能做更多事"而在工具里构造一个管理员身份——那等于给 AI 开了后门。

### 2.6 前端不许写死字段名
页面一律读 `/api/models/{key}/schema` 渲染。新增字段后前端不用改代码就能显示。
只有"要做通用表格表达不了的交互"时，才新写一个 view。

### 2.7 写操作包在事务里
`create/update/delete` 都是 `sql.transaction(tx -> { ... })`：实体、关联表、审计日志一起提交。
钩子里如果也要写库，必须用传进来的 `tx` 会话，不要用注入的 `sql`（否则在事务外、无法回滚）。

### 2.8 受检异常必须处理
`ModelSession.insert/update` 抛受检的 `CheckerException`。要么 `catch` 转成 `ApiException.badRequest(...)`，
要么向上抛。吞掉会让用户看到 500 而拿不到"哪个字段填错了"。

---

## 3. 任务手册

### A. 加一张全新的表（最常见）

以"客户"为例，**四步，不需要改任何 Controller 或前端**。

**第 1 步：写模型类** `server/src/main/java/com/rabbitc/ims/model/Customer.java`

```java
package com.rabbitc.ims.model;

import com.rabbitc.ipage.annotation.ModelClass;
import com.rabbitc.ipage.annotation.ModelField;
import com.rabbitc.ipage.annotation.ValidatorLength;
import com.rabbitc.ipage.annotation.ValidatorNotNull;
import com.rabbitc.ipage.enums.FieldType;
import com.rabbitc.ipage.meta.ModelPermission;
import com.rabbitc.ims.enums.RecordStatus;

import java.time.LocalDateTime;

@ModelClass(name = "客户", table = "ims_customer", icon = "fa-address-book",
        look = ModelPermission.STUDENT,      // 谁能看
        edit = ModelPermission.NORMAL,       // 谁能改
        delete = ModelPermission.TEACHER,    // 谁能删
        text = "${name}")                    // 下拉框/详情里怎么显示
public class Customer {

    @ModelField(column = "customer_id", type = FieldType.MODEL_ID, text = "ID", order = 1)
    public String id;

    @ModelField(column = "customer_name", text = "客户名称", order = 2)
    @ValidatorNotNull(message = "客户名称不能为空")
    @ValidatorLength(max = 64, message = "客户名称最长 64 个字符")
    public String name;

    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_ID, byModel = Department.class,
            text = "归属部门", order = 3)
    public String deptId;

    @ModelField(column = "dept_id", type = FieldType.ASSOCIATION_MODEL, byModel = Department.class,
            text = "归属部门", order = 4)
    public Department dept;

    @ModelField(column = "level", type = FieldType.ENUM, byEnum = CustomerLevel.class,
            text = "客户等级", order = 5)
    public CustomerLevel level = CustomerLevel.NORMAL;

    @ModelField(column = "customer_status", type = FieldType.ENUM, byEnum = RecordStatus.class,
            text = "状态", order = 6)
    public RecordStatus status = RecordStatus.ENABLED;

    @ModelField(column = "create_by", type = FieldType.ASSOCIATION_ID, byModel = User.class,
            text = "创建人", order = 7, look = false)
    public String createBy;

    @ModelField(column = "created_at", type = FieldType.DATE, text = "创建时间", order = 8, look = false)
    public LocalDateTime createdAt;

    @Override
    public String toString() {
        return name == null ? "客户" : name;
    }
}
```

**第 2 步：枚举**（若有）放 `enums/`，用 `@ModelEnum(text = "...")` 标注中文名：

```java
public enum CustomerLevel {
    @ModelEnum(text = "普通") NORMAL,
    @ModelEnum(text = "重要") IMPORTANT,
    @ModelEnum(text = "战略") STRATEGIC
}
```

**第 3 步：重启**。启动时 `DataInitializer` 会调 `synchronizeSchema` 自动建表。
日志里会出现 `CREATE TABLE IF NOT EXISTS ims_customer (...)`。

**第 4 步：验证**

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python -c "import sys,json;print(json.load(sys.stdin)['token'])")

curl -s localhost:8080/api/models/customer/schema -H "Authorization: Bearer $TOKEN"   # 应返回字段定义
curl -s -X POST localhost:8080/api/models/customer \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"示例客户"}'                                                             # 应返回带 uuid 的行
```

前端刷新即出现「业务数据 → 客户」（分组由表名前缀决定：`ims_` → 业务数据，`sys_` → 系统管理，`ai_` → 智能助手）。

**可选第 5 步**：需要演示数据就加到 `seed/DataInitializer.seed()`（注意只在用户表为空时执行）。
需要走特殊逻辑就加一个 `ModelHook`（见 E）。

---

### B. 给已有表加字段

1. 在模型类里加一个 `public` 字段 + `@ModelField`，`order` 挑个空位；
2. 重启 —— `synchronizeSchema` 会 `ALTER TABLE ... ADD COLUMN`；
3. 前端自动出现该列与表单项。

**限制**：iPage 只"加列"，**不改类型、不删列、不改约束**（刻意的保守设计）。
要改类型或删列，必须手写迁移脚本并说明，不要在模型上"假装"改了。

---

### C. 加一个权限点

1. `DataInitializer.seedPermissions()` 里加一条 `permission("biz:customer:export", "客户导出", "业务数据", 4)`；
2. 空库重启才会写入（已有库要么手工加数据，要么写一次性迁移）；
3. 在 `PermissionsView` 的矩阵里勾给角色即可；
4. 后端接口上标注 `@RequirePermission("biz:customer:export")` 或代码里判断 `user.has("biz:customer:export")`。

> 权限点用于"同一等级下更细的开关"；如果只是"谁能进这张表"，直接用 `@ModelClass(edit=...)`，不要加权限点。

---

### D. 调整数据范围（能看到哪些行）

数据范围只有三档，实现在 `CrudService`：

| 档位 | 过滤规则 | 依赖字段 |
| --- | --- | --- |
| `ALL` | 不过滤 | — |
| `DEPT` | `deptId = 当前用户部门` | 模型必须有 `deptId` 字段，否则不过滤（放行） |
| `SELF` | `createBy = 当前用户` | 模型必须有 `createBy` 字段，否则不过滤（放行） |

要点：

- 列表走 `applyScope()`（生成 SQL 条件），单行走 `visibleRow()`（内存判断），**两处必须同步改**；
- 用户的实际范围 = 其所有角色里**最宽**的那个（`AuthService.snapshot`）；
- 管理员的等级规则见 `LoginUser.reaches/isAdmin`，不要绕过。

---

### E. 加一个业务钩子（如工单状态流转校验）

实现 `ModelHook`，注册为 `@Component` 即生效（`CrudService` 会注入所有 `ModelHook`）：

```java
@Component
public class TicketHook implements ModelHook {

    @Override
    public boolean supports(Class<?> modelClass) {
        return modelClass == Ticket.class;      // 只处理工单
    }

    @Override
    public void beforeUpdate(ModelMeta meta, Object entity, Object before,
                             Map<String, Object> body, LoginUser user) {
        Ticket next = (Ticket) entity;
        Ticket prev = (Ticket) before;
        if (prev.status == TicketStatus.CLOSED && next.status != TicketStatus.CLOSED) {
            throw ApiException.badRequest("已关闭的工单不能重新打开，请新建工单");
        }
    }
}
```

可用的五个时点：`beforeCreate / afterCreate / beforeUpdate / afterUpdate / beforeDelete`。
- `before*` 里抛 `ApiException` 就能中断操作；
- `after*` 里写库要用事务会话（方法签名里没给会话时，用 `sql` 也可以，但注意它已在 `CrudService` 的事务内）；
- `UserHook` 是最好的参考实现：它处理了密码散列与角色关联同步。

---

### F. 加一个自定义页面（通用表格表达不了时）

1. `web/src/views/` 新建 `XxxView.vue`；
2. `web/src/router.js` 加一条路由（`createWebHashHistory`，路径形如 `/xxx`）；
3. 需要出现在侧边栏就在 `AppLayout.vue` 的导航里加一项，并用 `auth.can('权限点')` 控制显隐。

**先想想能不能不加页面**：列显隐、排序、过滤、搜索、批量删除，通用页都有了；
只是"想给某模型换个默认排序或默认列"，应该用界面配置，而不是写新页面。

---

### G. 加一个 AI 工具

在 `ai/AiEngineManager.registerTools()` 里：

```java
engine.registerTool(new EmbedAiChat.Tool(
        "ims_export_customer",
        "Export customers as CSV text",
        schema("""
                {"type":"object",
                 "properties":{"dept":{"type":"string","description":"department uuid"}},
                 "additionalProperties":false}
                """),
        args -> {
            // 必须用当前 user 调服务层，权限自然生效
            PageResult page = crud.page(user, "customer", Map.of("size", "200"));
            return json(Map.of("count", page.total()));
        }));
```

约定：
- 工具名统一 `ims_` 前缀，参数用 JSON Schema 描述，`additionalProperties: false`；
- 工具描述用**英文**（模型对英文描述理解更稳），返回 JSON；
- 不要给工具传 `LoginUser` 以外的身份；
- 新增工具后同步更新 `resources/ai/skills/ims-assistant/SKILL.md` 里的操作规则（如果需要教模型怎么用）。

---

### H. 接一个外部 MCP 服务

不需要改代码：登录后进「智能助手 → MCP」新增一行，`command` 填 **JSON 数组**：

```json
["python", "mcp/demo_server.py"]
["npx", "-y", "@modelcontextprotocol/server-filesystem", "/data"]
```

- 命令是 argv 数组，**不经过 shell**，别写 `&&`、重定向、通配符；
- 保存后会发 `AiConfigChangedEvent`，`AiEngineManager.invalidateAll()` 作废旧会话，下一次对话自动挂载；
- 工具名会带 `prefix` 前缀，例如 `demo_ping`，避免与 `ims_*` 冲突。

---

## 4. 禁止事项

| 不要做 | 原因 |
| --- | --- |
| 在 `libs/` 里改业务逻辑 | 与上游分叉，且受许可证约束 |
| 手写字符串拼 SQL | 注入风险；有方言/引号差异 |
| 给主键加 `fixed = true` | 主键会变成"必须外部提供"，插入失败 |
| 用 `Long` 接主键 | 主键是 UUID，`Long` 会在 Spring 参数绑定阶段直接 400 |
| 在 `toRow()` 之外另造一套行输出 | 字段级权限（`look=false`、`lookPermission`）会失效 |
| 为了省事让 AI 工具用 administrator 身份 | 越权后门 |
| 把 `password` 加到 `@ModelField` | 明文会进元数据与 SQL；正确做法见 `UserHook` |
| 在 `web/` 里写死字段名 | 破坏"加字段即生效" |
| 直接改已上线库的列类型 | iPage 不做类型迁移，会与元数据不一致 |

---

## 5. 验收清单（改完必须跑）

```bash
# 1. 编译（必须先于一切）
export JAVA_HOME=/path/to/jdk-17+     # 注意：JAVA_HOME 指向 JDK 根目录，不是 bin
./mvnw -o -q -pl server -am compile

# 2. 前端构建（只改了 web/ 才需要）
cd web && npm run build

# 3. 启动
cd server && java -jar target/ims-server-1.0.0.jar

# 4. 冒烟
curl -s localhost:8080/api/health                       # {"status":"ok"}
curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'        # 有 token
curl -s localhost:8080/api/nav -H "Authorization: Bearer $TOKEN"   # 新模型出现在正确分组
```

**权限回归**（改动涉及权限/数据范围时必做）：
分别用 `admin / manager / staff` 登录，确认同一张表的可见行数与预期一致（`ALL / 本部门 / 仅本人`）。

**界面回归**：浏览器打开首页，控制台应**无红色报错**；随便点开一个模型的"新增"，表单字段齐全。

---

## 6. 常见报错 → 原因

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `NULL not allowed for column "XXX_ID"` | 插入前没 `Ids.assignIfAbsent` | 见 2.2 |
| `Method ... not applicable` / `Object 无法转换为 X` | `sql.selectList(Query)` 在 `for` 里被推断成 `Object` | 写成 `sql.<T>selectList(...)` |
| `Unhandled exception: CheckerException` | 受检异常没捕获 | 见 2.8 |
| 新字段不显示 | 前端读了缓存 schema，或字段 `look = false` | 刷新/重登录；检查注解 |
| 新表不出现 | 模型不在 `com.rabbitc.ims.model` 包下，或缺 `@ModelClass` | 移动包/补注解；看启动日志的扫描结果 |
| 页面 403 | 等级不够或权限点没配 | 看 `@ModelClass` 的 look/edit/delete 与角色矩阵 |
| 列表条数比预期少 | 数据范围在起作用（SELF/DEPT） | 确认账号角色，见 D |
| `No static resource favicon.ico` 刷日志 | 浏览器自动请求（已修） | 已有 `web/public/favicon.svg` |
| 中文乱码 | 请求体不是 UTF-8 | 客户端固定 `Content-Type: application/json; charset=utf-8` |

---

## 7. 代码地图（找代码先看这张表）

| 想知道… | 看这里 |
| --- | --- |
| 表结构由什么决定 | `model/*.java` 的 `@ModelClass` / `@ModelField` |
| 为什么请求被拦 | `security/AuthInterceptor` + `WebConfig` |
| 为什么看到的数据变少了 | `service/CrudService.applyScope()` |
| 某个字段为什么不出现在响应里 | `service/CrudService.toRow()` + 字段注解 |
| 密码怎么存的 | `security/PasswordHasher` |
| 令牌怎么校验的 | `security/TokenService` + `AuthService.load()` |
| 角色权限怎么算的 | `security/AuthService.snapshot()` |
| 菜单分组从哪来 | `meta/ModelRegistry.groupOf()`（按表名前缀） |
| 前端表单长什么样 | `meta/MetaService.describe()` + `web/src/components/DataForm.vue` |
| AI 有哪些工具 | `ai/AiEngineManager.registerTools()` |
| 演示数据 | `seed/DataInitializer` |
| 独立客户端怎么对接 | `clients/cli/ims_cli.py` + `docs/10-客户端开发指南.md` |

---

## 8. 提交前自检

- [ ] 新增/修改的文件都有类级 Javadoc 或文件头注释，说明"为什么这么写"
- [ ] 没有把 `libs/` 的改动混进业务提交
- [ ] 改了模型 → 说明是否需要重建库（改类型/删列必须提醒）
- [ ] 改了权限 → 在提交说明里写清影响哪些账号
- [ ] 改了 AI 工具 → 同步 `SKILL.md`
- [ ] `./mvnw -o -q -pl server -am compile` 通过，`web` 若有改动 `npm run build` 通过
- [ ] 涉及许可证/新依赖 → 更新 `THIRD-PARTY-NOTICES.md`
