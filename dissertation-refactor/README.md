# dissertation → iPage-IMS 移植方案（PostgreSQL）

> 把 `D:\Desktop\dissertation.sql`（MySQL 8.0 的毕业设计管理系统库）
> 移植成本项目的模型层，数据库切到 **PostgreSQL**，并把**论文成绩拆成独立表**。
>
> 本文记录：改了什么、为什么这么改、怎么跑起来、验证到了什么程度。

---

## 一、结论先说

| 项 | 结果 |
| --- | --- |
| 模型类 | 新增 **10 个** `@ModelClass`（`th_` 前缀），不写一行 Controller / Mapper / 页面 |
| 建表 | 由 iPage `synchronizeSchema` 在 PostgreSQL 上自动建 10 张表 |
| 约束 | 由 `BusinessConstraints` 补 **10 个唯一索引 + 10 个外键** |
| 数据 | 从原库转换 **4414 行**，主键改为确定性 UUID，外键关系完整 |
| 成绩 | 从论文表拆出 `th_paper_score`，总成绩由钩子按 `论文×0.6 + 答辩×0.4` 计算 |
| 验证 | 真实 PostgreSQL 17 实例上建表 + 导数据 + 接口读写全部通过（见第五节） |

所有改动都落在 **装配层**（`server/.../model`、`enums`、`service`、`seed`），
没有动 `libs/` 下的框架源码。

---

## 二、交付物

| 文件 | 说明 |
| --- | --- |
| `seed-postgres.sql` | 由原 MySQL 库转换的数据种子（4414 行，UUID 主键，可直接 `psql -f`） |
| `schema-postgres.sql` | 移植后 PostgreSQL 上**实际生成**的结构快照（`pg_dump --schema-only`），供评审或 DBA 比对 |
| `../analysis/generate_pg_seed.py` | 种子生成器：读原 MySQL dump、做枚举映射与 UUID 转换，改口径后重跑即可重新生成（依赖 `analysis/dump.py` 解析器） |

代码改动：

| 位置 | 文件 |
| --- | --- |
| `server/.../enums` | `ClassType`、`TeacherPost`、`TeacherEducation`、`PaperStatus` |
| `server/.../model` | `College`、`Profession`、`SchoolClass`、`Student`、`Teacher`、`Direction`、`TeacherDirection`、`TeacherProfession`、`Paper`、`PaperScore` |
| `server/.../service` | `PaperHook`（空串归一、状态与题目的一致性）、`PaperScoreHook`（总成绩计算） |
| `server/.../seed` | `BusinessConstraints`（扩展为支持**复合**唯一索引，并登记论文域规则） |

---

## 三、表映射：原库 → 新库

| 原表（MySQL） | 新表（PostgreSQL） | 模型类 | 行数 | 主要改动 |
| --- | --- | --- | ---: | --- |
| `department` | `th_college` | `College` | 1 | 改名：这里的「院系」是教学学院，与本项目已有 `ims_department`（行政部门）区分 |
| `profession` | `th_profession` | `Profession` | 6 | 去表名前缀；专业名唯一 |
| `class` | `th_class` | `SchoolClass` | 25 | 表名 `class` 与 Java 关键字冲突；`grade`(22/24) → `enroll_year`(2022/2024) |
| `student` | `th_student` | `Student` | 1177 | 删 `teacher_id`、`student_grade`；`student_detail` → `remark` |
| `teacher` | `th_teacher` | `Teacher` | 131 | 职称/学历改枚举；`teacher_qq/wechat/phone` → `qq/wechat/mobile` 并设为管理员可见；新增 `teacher_no` |
| `direction` | `th_direction` | `Direction` | 73 → 67 | 方向名唯一；清掉 6 条脏数据 |
| `teacher_direction` | `th_teacher_direction` | `TeacherDirection` | 338 | 复合唯一 `(teacher_id, direction_id)` |
| `teacher_profession` | `th_teacher_profession` | `TeacherProfession` | 324 | 复合唯一 `(teacher_id, profession_id)` |
| `paper` | `th_paper` | `Paper` | 1177 | **分数拆出去**；新增状态；课题四属性加取值校验 |
| — | `th_paper_score` | `PaperScore` | 1168 | **新增**：答辩成绩、论文成绩、总成绩 |

> `th_` 不在 `ims_` / `sys_` / `ai_` 三个前缀里，按 `ModelRegistry.groupOf()` 的默认分支，
> 这 10 个模型统一落在侧边栏的「业务数据」分组，与员工、资产、工单同级。

---

## 四、关键改造说明

### 4.1 成绩为什么单独一张表

原库把三列塞在论文行里：

```sql
-- dissertation.paper
defense_score DECIMAL(5,1),      -- 答辩成绩
thesis_score  DECIMAL(5,1),      -- 论文成绩
comprehensive_score DECIMAL(5,1) -- 总成绩
```

两个问题：

1. **总成绩是派生的**。实测 1177/1177 行都满足 `总成绩 = 论文×0.6 + 答辩×0.4`，
   但没有任何约束阻止它被改坏——一旦不一致，没有任何办法判断哪个是对的。
2. **成绩会增殖**。真实业务里成绩按答辩批次、按评委、按二次答辩记录，
   挂在选题行上就没地方放。

移植后：

```
th_paper        选题信息（学生、指导教师、题目、课题属性、状态）  1 ── 1  th_paper_score  成绩
```

- 唯一索引 `uq_th_paper_score_paper` 保证「一篇论文只有一条成绩」；
- `comprehensive_score` 在 `PaperScore` 上标了 `editPermission = FORBIDDEN`，
  **接口层传什么都改不了**，只能由 `PaperScoreHook` 写入：

```java
score.comprehensiveScore = score.thesisScore.multiply(THESIS_WEIGHT)   // 0.6
        .add(score.defenseScore.multiply(DEFENSE_WEIGHT))              // 0.4
        .setScale(1, RoundingMode.HALF_UP);
```

- 任一分项缺失时总成绩置空，不会出现「只有一半成绩却有总分」。

> 权重是两个常量，改口径只改这里。若要保留历史权重，应该把权重和计算时间一起落库，
> 而不是在文档里写死。

### 4.2 删掉的三处冗余（都有实测证据）

| 删除项 | 证据 | 为什么可以删 |
| --- | --- | --- |
| `student.teacher_id` | 与 `paper.teacher_id` **1177/1177 完全一致** | 指导教师是论文的属性，不是学生的属性；原列还没有外键，只有索引 |
| `student.student_grade` | 与 `class.grade` **11/1177 不一致** | 年级由班级的 `enroll_year` 派生，那份拷贝已经开始漂移 |
| `paper.teacher_id` 之外的重复成绩列 | 总成绩恒等于公式 | 见 4.1 |

### 4.3 枚举化：把自由文本收进类型系统

原库用 `varchar` 存中文和单字母编码，实测：

| 字段 | 原取值 | 处理 |
| --- | --- | --- |
| `class.class_type` | 统招 / 专升本 / 对口招生 | → `ClassType` |
| `teacher.teacher_post` | 10 种写法，其中「高工」与「高级工程师」同义 | → `TeacherPost`，「高工」并入 `SENIOR_ENGINEER` |
| `teacher.teacher_educational` | 5 种，其中「研究生」与「硕士」混用 | → `TeacherEducation`，「研究生」并入 `MASTER` |
| `paper.paper_status` | 原库没有 | → `PaperStatus`（未选题 / 已选题 / 已答辩 / 已归档） |
| `paper.paper_nature/type/source/difficulty` | A–E 单字母 | 改为 4 个枚举（`TopicNature`/`TopicType`/`TopicSource`/`TopicDifficulty`），**库里的值仍是 A/B/C 不变**，只把中文标签补上（依据见第 4.6 节） |

### 4.4 「未选题」不再用空串表示

原库有 **9 行**「题目是空串、四个课题属性与三个成绩全 NULL」的占位数据。
空串既不能进唯一约束，也没法参与查询。移植后：

- `paper_status = NOT_SELECTED` 时允许 `title` 为空；
- 其他状态由 `PaperHook` 强制要求题目非空（数据库层表达不了这种跨列规则）；
- 空串一律归一成 `NULL`，避免「空串 / NULL」两种缺失并存。

### 4.5 清掉的脏数据

`direction` 73 行里有 6 行是历史误录，导入时直接剔除（都是零引用，不影响任何教师）：

| id | 内容 | 原因 |
| ---: | --- | --- |
| 64 | 可带专业（去重后） | 说明性文字，不是方向 |
| 65 | 计算机科学与技术 | 专业名误录 |
| 66 | 软件工程 | 专业名误录 |
| 67 | 数据科学与大数据技术 | 专业名误录 |
| 69 | 网络工程 | 与 id=34 重复（34 有引用，保留） |
| 71 | 数字媒体技术 | 专业名误录 |

> id=68「人工智能」同样是专业名，但有 5 位教师引用，**保留**并建议业务确认是否改名。

### 4.6 课题编码 A–E 的含义（数据反推）

原库没有任何字典或注释，只有字母。以下结论是**从 1177 条论文题目与四个维度之间的相关性反推**出来的，
不是学校文档。判断依据写在每个枚举类的 Javadoc 里。

| 维度 | A | B | C | D | E | 置信度 |
| --- | --- | --- | --- | --- | --- | --- |
| **难度** | 难 | 较难 | 中等 | 较易 | 易 | **高**（业务方确认：A 最高、E 最低） |
| **性质** | 理论研究 | 应用研究 | 开发研究 | — | — | 中高 |
| **类型** | 综合类 | 论文类 | 设计类 | — | — | 中 |
| **来源** | 生产/社会实际 | 教师拟题 | 实验室与教学建设 | 科研项目 | 学生自选 | 低–中（B、C 最不确定） |

支撑证据：

- **难度**：A 的 9 条全是「改进 YOLO12 的 MRI 检测」「Transformer 加密流量分类」「LLM 心理理解模型」
  「二进制栈漏洞利用符号化建模」；B 的 29 条是 STM32、Unity、MCTS 优化一类；C 是 1130 条常规业务系统。
- **性质**：A 的 62 条里 77% 标题以「…方法研究 / …的研究」结尾，且 **100%** 落在「论文类」课题上；
  B 的 1106 条几乎都是「…系统的设计与实现」。
- **类型**：性质=理论的 62 条**全部**是 B，性质=应用的 1068 条**全部**是 C ——
  这种完全绑定只有「论文类 / 设计类」能解释。
- **来源**：A（19 条）集中在安徽地质旅游、茶叶商城、农产品、詹记商店、PE 板材厂这类具体企业题材；
  D（75 条）里 53% 是算法研究，且难度 A 的 9 条有 8 条来自 D，最像科研项目；
  E（40 条）是 Unity 游戏、社团招新、家政预约这类兴趣题。

> **实现上的取舍**：枚举常量名直接沿用字母（`TopicNature.A`），而不是改成
> `THEORETICAL` 之类的语义名。这样库里的值、历史统计、学校纸质表上的字母全部保持一致，
> 万一推断错了，只改 `@ModelEnum(text = ...)` 一处，**不需要动数据**。

### 4.7 约束集中登记

`BusinessConstraints` 原本只支持单列规则，这次扩展为「列组合」，于是关联表上的复合唯一也能表达：

```java
Rule.unique("uq_th_teacher_direction", TeacherDirection.class, "teacherId", "directionId"),
Rule.unique("uq_th_teacher_profession", TeacherProfession.class, "teacherId", "professionId"),
Rule.unique("uq_th_paper_student", Paper.class, "studentId"),
Rule.unique("uq_th_paper_score_paper", PaperScore.class, "paperId"),
Rule.foreign("fk_th_paper_student", Paper.class, "studentId", Student.class),
...
```

启动时先做数据体检（重复 / 孤儿关联），不通过就**拒绝启动**并报出是哪张表哪一列，
不会出现「索引建了一半」的中间态。

---

## 五、怎么跑

```bash
# 1. 建库（只需一次）
psql -U postgres -c "CREATE DATABASE ipage_ims WITH ENCODING 'UTF8' TEMPLATE template0;"

# 2. 打包
./mvnw -pl server -am -DskipTests package

# 3. 用 postgres profile 启动一次 —— 表与约束由 iPage 自动创建
java -jar server/target/ims-server-1.0.0.jar --spring.profiles.active=postgres

# 4. 再导入数据（必须在第 3 步之后，表要先存在）
psql -U postgres -d ipage_ims -v ON_ERROR_STOP=1 -f dissertation-refactor/seed-postgres.sql
```

连接参数在 `application.yml`（默认已是 PostgreSQL），可用环境变量覆盖：`PG_USER`、`PG_PASSWORD`。

> **库的默认值**：现在默认连 PostgreSQL（库名 `ipage_ims`），也就是论文数据所在的库。
> 免安装的 H2 模式改为显式 profile：`--spring.profiles.active=h2`，
> 它是**独立空库**，首次启动会自动导入内置演示数据（`seed/thesis-data.sql`，与本目录的 seed 同源）。

---

## 六、验证记录（本机 PostgreSQL 17.2 实测）

### 6.1 建表与约束

```
表：th_class, th_college, th_direction, th_paper, th_paper_score,
    th_profession, th_student, th_teacher, th_teacher_direction, th_teacher_profession   （10 张）
唯一索引：uq_th_* × 10
外键：fk_th_* × 10
```

### 6.2 数据一致性（全部为 0 才算通过）

| 检查项 | 结果 |
| --- | ---: |
| 总成绩 ≠ `论文×0.6 + 答辩×0.4` 的行数 | 0 |
| 一名学生多篇论文 | 0 |
| 论文指向不存在的学生（孤儿） | 0 |
| 有成绩却状态不是「已答辩」 | 0 |
| 状态分布 | 已答辩 1168 / 未选题 9 |

### 6.3 接口

| 用例 | 结果 |
| --- | --- |
| `GET /api/models/paper` | 1177 条；关联对象正常回填：`学生=顾豪（2242159303）`、`教师=何丽娟` |
| `GET /api/models/paperScore` | 1168 条；总成绩与分项一致 |
| `POST /api/models/paperScore`（88.5 / 76.0，并故意传 `comprehensiveScore=12.3`） | 总成绩自动算成 **81.0**，手工传的 12.3 被忽略 |
| 同一论文重复录成绩 | 返回 **409**（唯一索引 `uq_th_paper_score_paper` 生效） |
| `h2` profile 启动（全新空库） | 自动建表并导入内置数据：院系 1 / 学生 1177 / 论文 1177，登录与查询正常 |

---

## 七、还需要业务确认的事

**难度已确认**（A 难 / B 较难 / C 中等 / D 较易 / E 易），其余三项是按数据反推的（见 4.6 节），
建议对照学校当年的纸质选题审批表核一遍：

1. **课题来源的 B、C 两档**最不确定。A（生产/社会实际）、D（科研项目）、E（学生自选）有题材分布佐证，
   B（954 条、教师拟题）和 C（80 条、实验室与教学建设）只是按占比和题材猜的。
2. **「研究生」算硕士还是博士？** 目前按硕士处理（`TeacherEducation.MASTER`）。
3. **id=68 的「人工智能」是方向还是误录的专业名？** 它有 5 位教师引用，暂时保留。

> 前三项若与学校口径不同，**只改枚举上的 `@ModelEnum(text = ...)`**，库里的字母值不用动，
> 历史统计口径也不受影响。

---

## 八、后续可选增强（本次未做）

按投入产出排序：

1. **成绩按答辩批次拆分**：`th_paper_score` 加 `batch` / `reviewer` 字段，
   一论文多分数行（评分项 + 权重），这需要先有「答辩安排」这张表。
2. **选题双选流程**：课题库、学生志愿、教师确认，目前只有结果表没有过程表。
3. **教师工号、院系/专业编码**：字段已经留好（`teacher_no`），等业务给数据。
4. **软删除**：现在删除是物理删除，外键 `RESTRICT` 会拦住误删，但没有回收站。
5. **数据范围**：`Student` / `Paper` 已带 `createBy`，可把角色的 `DataScope` 设为 `SELF`，
   让学生只看自己的论文；教师维度需要再补一个「按指导教师过滤」的规则。
