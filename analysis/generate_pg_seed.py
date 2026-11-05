# -*- coding: utf-8 -*-
"""把 dissertation.sql（MySQL）转成 PostgreSQL 种子脚本。

关键映射：
  * 整型主键 → 确定性 UUID v5（同一条记录每次都得到同一个 UUID，可重复导入）；
  * 枚举中文字面量 → Java 枚举常量名（与 th_* 模型字段口径一致）；
  * 论文成绩拆表：th_paper 不再带分数，分数进 th_paper_score；
  * 清理 direction 的 6 条脏数据（重复项、误录的专业名、说明性文字）。
"""
import sys
import io
import uuid
import collections

sys.path.insert(0, 'analysis')
from dump import rows, col_index as ci  # noqa: E402

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', newline='\n')

NS = uuid.UUID('6ba7b811-9dad-11d1-80b4-00c04fd430c8')  # NAMESPACE_URL


def uid(table, old_id):
    return str(uuid.uuid5(NS, f'ims:{table}:{old_id}'))


def q(value):
    """SQL 字面量。"""
    if value is None:
        return 'NULL'
    if isinstance(value, bool):
        return 'TRUE' if value else 'FALSE'
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


CLASS_TYPE = {'统招': 'REGULAR', '专升本': 'UPGRADE', '对口招生': 'TARGETED'}
TEACHER_POST = {
    '教授': 'PROFESSOR', '副教授': 'ASSOCIATE_PROFESSOR', '讲师': 'LECTURER', '助教': 'ASSISTANT',
    '正高级工程师': 'PROFESSOR_ENGINEER', '高级工程师': 'SENIOR_ENGINEER', '高工': 'SENIOR_ENGINEER',
    '工程师': 'ENGINEER', '其他中级': 'OTHER_INTERMEDIATE', '无': 'NONE',
}
TEACHER_EDU = {'博士': 'DOCTOR', '硕士': 'MASTER', '研究生': 'MASTER', '学士': 'BACHELOR', '无': 'NONE'}

# direction 中要丢弃的脏数据：重复的“网络工程”(69)、误录的专业名、说明性文字
DIRECTION_DROP = {64, 65, 66, 67, 69, 71}

out = []
stats = collections.OrderedDict()


def emit_batch(table, columns, values, size=200):
    """分批发多行 INSERT；空表直接跳过。"""
    if not values:
        stats[table] = 0
        return
    stats[table] = len(values)
    head = f'INSERT INTO {table} ({", ".join(columns)}) VALUES\n'
    for start in range(0, len(values), size):
        chunk = values[start:start + size]
        body = ',\n'.join('  (' + ', '.join(row) + ')' for row in chunk)
        out.append(head + body + ';\n')


NOW = 'now()'

# ---------------------------------------------------------------- th_college
dep = rows['department']
cols = ['college_id', 'name', 'remark', 'created_at', 'updated_at']
vals = [[q(uid('th_college', r[ci('department', 'department_id')])),
         q(r[ci('department', 'department_name')]),
         q(r[ci('department', 'remark')]), NOW, NOW] for r in dep]
emit_batch('th_college', cols, vals)

# ------------------------------------------------------------- th_profession
pro = rows['profession']
cols = ['profession_id', 'college_id', 'name', 'created_at', 'updated_at']
vals = [[q(uid('th_profession', r[ci('profession', 'profession_id')])),
         q(uid('th_college', r[ci('profession', 'department_id')])),
         q(r[ci('profession', 'profession_name')]), NOW, NOW] for r in pro]
emit_batch('th_profession', cols, vals)

# -------------------------------------------------------------- th_direction
d = rows['direction']
cols = ['direction_id', 'name', 'created_at', 'updated_at']
vals = []
for r in d:
    old = r[ci('direction', 'direction_id')]
    if old in DIRECTION_DROP:
        continue
    vals.append([q(uid('th_direction', old)), q(r[ci('direction', 'direction_name')]), NOW, NOW])
emit_batch('th_direction', cols, vals)

# ------------------------------------------------------------------ th_class
c = rows['class']
cols = ['class_id', 'profession_id', 'name', 'class_type', 'enroll_year', 'created_at', 'updated_at']
vals = []
for r in c:
    grade = r[ci('class', 'grade')]
    year = 2000 + grade if grade < 100 else grade
    vals.append([q(uid('th_class', r[ci('class', 'class_id')])),
                 q(uid('th_profession', r[ci('class', 'profession_id')])),
                 q(r[ci('class', 'class_name')]),
                 q(CLASS_TYPE[r[ci('class', 'class_type')]]),
                 str(year), NOW, NOW])
emit_batch('th_class', cols, vals)

# ---------------------------------------------------------------- th_teacher
t = rows['teacher']
cols = ['teacher_id', 'teacher_no', 'name', 'post', 'education', 'qq', 'wechat', 'mobile',
        'remark', 'created_at', 'updated_at']
vals = []
for r in t:
    tid = r[ci('teacher', 'teacher_id')]
    vals.append([q(uid('th_teacher', tid)), 'NULL',
                 q(r[ci('teacher', 'teacher_name')]),
                 q(TEACHER_POST[r[ci('teacher', 'teacher_post')]]),
                 q(TEACHER_EDU[r[ci('teacher', 'teacher_educational')]]),
                 q(r[ci('teacher', 'teacher_qq')]),
                 q(r[ci('teacher', 'teacher_wechat')]),
                 q(r[ci('teacher', 'teacher_phone')]),
                 q(r[ci('teacher', 'teacher_detail')]), NOW, NOW])
emit_batch('th_teacher', cols, vals)

# ---------------------------------------------------------------- th_student
s = rows['student']
cols = ['student_id', 'class_id', 'student_no', 'name', 'remark', 'create_by', 'created_at', 'updated_at']
vals = []
for r in s:
    vals.append([q(uid('th_student', r[ci('student', 'student_id')])),
                 q(uid('th_class', r[ci('student', 'class_id')])),
                 q(r[ci('student', 'student_no')]),
                 q(r[ci('student', 'student_name')]),
                 q(r[ci('student', 'student_detail')]), 'NULL', NOW, NOW])
emit_batch('th_student', cols, vals)

# --------------------------------------------------------- th_teacher_direction
td = rows['teacher_direction']
cols = ['teacher_direction_id', 'teacher_id', 'direction_id']
vals = [[q(uid('th_teacher_direction', r[ci('teacher_direction', 'teacher_direction_id')])),
         q(uid('th_teacher', r[ci('teacher_direction', 'teacher_id')])),
         q(uid('th_direction', r[ci('teacher_direction', 'direction_id')]))] for r in td
        if r[ci('teacher_direction', 'direction_id')] not in DIRECTION_DROP]
emit_batch('th_teacher_direction', cols, vals)

# -------------------------------------------------------- th_teacher_profession
tp = rows['teacher_profession']
cols = ['teacher_profession_id', 'teacher_id', 'profession_id']
vals = [[q(uid('th_teacher_profession', r[ci('teacher_profession', 'teacher_profession_id')])),
         q(uid('th_teacher', r[ci('teacher_profession', 'teacher_id')])),
         q(uid('th_profession', r[ci('teacher_profession', 'profession_id')]))] for r in tp]
emit_batch('th_teacher_profession', cols, vals)

# ------------------------------------------------------------------- th_paper
p = rows['paper']
cols = ['paper_id', 'student_id', 'teacher_id', 'paper_status', 'title',
        'topic_nature', 'topic_type', 'topic_source', 'topic_difficulty',
        'create_by', 'created_at', 'updated_at']
paper_vals = []
score_vals = []
no_topic = 0
for r in p:
    pid = r[ci('paper', 'paper_id')]
    title = r[ci('paper', 'paper_title')]
    defense = r[ci('paper', 'defense_score')]
    thesis = r[ci('paper', 'thesis_score')]
    if title is not None and title.strip() == '':
        title = None
    if defense is None and thesis is None:
        status = 'NOT_SELECTED'
        no_topic += 1
    elif defense is not None and thesis is not None:
        status = 'DEFENDED'
    else:
        status = 'SELECTED'
    paper_vals.append([q(uid('th_paper', pid)),
                       q(uid('th_student', r[ci('paper', 'student_id')])),
                       q(uid('th_teacher', r[ci('paper', 'teacher_id')])),
                       q(status), q(title),
                       q(r[ci('paper', 'paper_nature')]),
                       q(r[ci('paper', 'paper_type')]),
                       q(r[ci('paper', 'paper_source')]),
                       q(r[ci('paper', 'paper_difficulty')]),
                       'NULL', NOW, NOW])
    if defense is not None and thesis is not None:
        total = round(thesis * 0.6 + defense * 0.4, 1)
        score_vals.append([q(uid('th_paper_score', pid)),
                           q(uid('th_paper', pid)),
                           str(defense), str(thesis), str(total), 'NULL', 'NULL', NOW, NOW])

emit_batch('th_paper', cols, paper_vals)
emit_batch('th_paper_score',
           ['paper_score_id', 'paper_id', 'defense_score', 'thesis_score',
            'comprehensive_score', 'remark', 'create_by', 'created_at', 'updated_at'],
           score_vals)

header = """-- =============================================================================
-- 论文管理系统 · PostgreSQL 种子数据（由 dissertation.sql 转换生成）
-- -----------------------------------------------------------------------------
-- 生成口径：
--   * 主键统一为 UUID v5（namespace 固定），源库整型 ID 与 UUID 一一对应，
--     因此重复导入同一份数据不会产生新的主键，外键关系也保持稳定；
--   * th_paper 不再存分数，分数拆到 th_paper_score，总成绩按 论文×0.6+答辩×0.4 重算；
--   * direction 丢弃 6 条脏数据（id 64/65/66/67/69/71：说明性文字、误录的专业名、重复项）；
--   * 原库 9 条「空标题 + 无成绩」的占位论文保留，状态置为 NOT_SELECTED、题目为 NULL；
--   * 职称「高工」并入「高级工程师」，学历「研究生」并入「硕士」。
--
-- 执行前提：应用已在 postgres profile 下启动过一次，th_* 表由 iPage 建好。
-- 导入顺序即外键顺序，请勿调整。
-- =============================================================================

BEGIN;
"""

footer = "\nCOMMIT;\n"

with open('dissertation-refactor/seed-postgres.sql', 'w', encoding='utf-8', newline='\n') as f:
    f.write(header)
    f.write(''.join(out))
    f.write(footer)

print('生成完成：dissertation-refactor/seed-postgres.sql')
total = 0
for table, count in stats.items():
    print(f'  {table:26s} {count:5d}')
    total += count
print(f'  {"合计":24s} {total:5d}')
print(f'未选题论文（空标题占位行）：{no_topic}')
