# -*- coding: utf-8 -*-
"""解析 dissertation.sql 的结构与数据，输出重构前的事实基线。"""
import re, json, collections, sys, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
SRC = r'D:\Desktop\dissertation.sql'
txt = open(SRC, encoding='utf-8').read()

# ---------- 1. 表结构 ----------
tables = {}
for m in re.finditer(r'CREATE TABLE `(\w+)`\s*\((.*?)\n\) ENGINE', txt, re.S):
    name = m.group(1)
    body = m.group(2)
    cols, idx = [], []
    for line in body.split('\n'):
        line = line.strip().rstrip(',')
        if not line:
            continue
        if line.startswith('PRIMARY KEY'):
            idx.append(('PK', line))
        elif line.startswith(('INDEX', 'UNIQUE INDEX', 'KEY')):
            idx.append(('IDX', line))
        elif line.startswith('CONSTRAINT'):
            idx.append(('FK', line))
        else:
            cm = re.match(r'`(\w+)`\s+(\S+?)(\s|$)', line)
            if cm:
                cols.append((cm.group(1), cm.group(2)))
    tables[name] = {'cols': cols, 'idx': idx}

# ---------- 2. 数据 ----------
rows = collections.defaultdict(list)
for m in re.finditer(r'INSERT INTO `(\w+)` VALUES \((.*?)\);\s*$', txt, re.M):
    t, raw = m.group(1), m.group(2)
    vals, cur, inq, esc = [], '', False, False
    for ch in raw:
        if esc:
            cur += ch; esc = False; continue
        if ch == '\\':
            cur += ch; esc = True; continue
        if ch == "'":
            inq = not inq; cur += ch; continue
        if ch == ',' and not inq:
            vals.append(cur.strip()); cur = ''; continue
        cur += ch
    if cur.strip() or raw.endswith(','):
        vals.append(cur.strip())

    def conv(v):
        if v == 'NULL':
            return None
        if v.startswith("'"):
            s = v[1:-1]
            s = s.replace("\\'", "'").replace('\\"', '"').replace('\\\\', '\\')
            s = s.replace('\\n', '\n').replace('\\r', '\r')
            return s
        try:
            return int(v)
        except ValueError:
            try:
                return float(v)
            except ValueError:
                return v

    rows[t].append([conv(v) for v in vals])

print('=' * 70)
print('表清单 / 行数 / 列数')
print('=' * 70)
for t in sorted(tables):
    print(f'{t:22s} rows={len(rows[t]):5d} cols={len(tables[t]["cols"]):2d} '
          f'pk={tables[t]["cols"][0][0]}')

# ---------- 3. 主键检查 ----------
print('\n' + '=' * 70)
print('主键 / 自增连续性')
print('=' * 70)
for t in sorted(tables):
    pk = tables[t]['cols'][0][0]
    idx = tables[t]['cols'][0][1]
    if 'int' not in idx.lower():
        continue
    ids = [r[0] for r in rows[t]]
    dup = [k for k, v in collections.Counter(ids).items() if v > 1]
    mx = max(ids) if ids else 0
    missing = sorted(set(range(1, mx + 1)) - set(ids))
    print(f'{t:22s} n={len(ids):5d} max={mx:5d} 缺号={len(missing):4d} '
          f'重复={dup if dup else "无"}  缺口样例={missing[:8]}')

# ---------- 4. 外键完整性 ----------
print('\n' + '=' * 70)
print('外键完整性（含缺失约束的隐含外键）')
print('=' * 70)
declared = {}  # (child, col) -> (parent, pcol)
for t, meta in tables.items():
    for kind, s in meta['idx']:
        if kind == 'FK':
            m = re.search(r'FOREIGN KEY \(`(\w+)`\) REFERENCES `(\w+)` \(`(\w+)`\)', s)
            if m:
                declared[(t, m.group(1))] = (m.group(2), m.group(3))

implied = {('student', 'teacher_id'): ('teacher', 'teacher_id')}
for (ct, cc), (pt, pc) in list(declared.items()) + list(implied.items()):
    tag = 'FK约束' if (ct, cc) in declared else '!!无约束'
    pset = {r[tables[pt]['cols'].index((pc,)) if False else [c[0] for c in tables[pt]['cols']].index(pc)]
            for r in rows[pt]}
    ci = [c[0] for c in tables[ct]['cols']].index(cc)
    bad = sorted({r[ci] for r in rows[ct] if r[ci] is not None} - pset)
    nulls = sum(1 for r in rows[ct] if r[ci] is None)
    print(f'{ct}.{cc} -> {pt}.{pc:16s} [{tag}] 孤儿={len(bad)} NULL={nulls} {bad[:10]}')

# ---------- 5. 唯一约束 ----------
print('\n' + '=' * 70)
print('唯一约束 / 业务唯一性检查')
print('=' * 70)
uniq = {
    ('department', 'department_name'): '声明 UNIQUE',
    ('student', 'student_no'): '声明 UNIQUE',
    ('teacher_direction', 'teacher_id,direction_id'): '声明 UNIQUE',
    ('teacher_profession', 'teacher_id,profession_id'): '声明 UNIQUE',
    ('profession', 'department_id,profession_name'): '缺失(业务唯一)',
    ('class', 'profession_id,class_name,grade'): '缺失(业务唯一)',
    ('paper', 'student_id'): '缺失(一学生一题)',
    ('teacher', 'teacher_name'): '缺失(同名教师)',
    ('direction', 'direction_name'): '缺失',
}
for (t, cols), note in uniq.items():
    names = [c[0] for c in tables[t]['cols']]
    keys = [names.index(c) for c in cols.split(',')]
    cnt = collections.Counter(tuple(r[k] for k in keys) for r in rows[t])
    dups = [(k, v) for k, v in cnt.items() if v > 1]
    print(f'{t}({cols}) [{note}] 重复组={len(dups)} {dups[:6]}')

# ---------- 6. 空串 / 占位垃圾 ----------
print('\n' + '=' * 70)
print('空串与占位值')
print('=' * 70)
for t in sorted(tables):
    names = [c[0] for c in tables[t]['cols']]
    for i, c in enumerate(names):
        vals = [r[i] for r in rows[t]]
        empty = sum(1 for v in vals if v == '')
        nul = sum(1 for v in vals if v is None)
        if empty or nul:
            print(f'{t}.{c:22s} 空串={empty:4d} NULL={nul:4d} / {len(vals)}')

# ---------- 7. 枚举值分布 ----------
print('\n' + '=' * 70)
print('低基数字符串列（应为枚举）的值分布')
print('=' * 70)
for t in sorted(tables):
    names = [c[0] for c in tables[t]['cols']]
    for i, (c, ty) in enumerate(tables[t]['cols']):
        if 'varchar' not in ty:
            continue
        vals = [r[i] for r in rows[t] if r[i] is not None]
        if not vals:
            continue
        cnt = collections.Counter(vals)
        if len(cnt) <= 12 and len(vals) > 20:
            print(f'{t}.{c:20s} distinct={len(cnt)} -> {dict(cnt)}')

# ---------- 8. 业务逻辑一致性 ----------
print('\n' + '=' * 70)
print('业务逻辑一致性')
print('=' * 70)
# 8.1 总成绩 = 0.4*答辩 + 0.6*论文 ?
pn = [c[0] for c in tables['paper']['cols']]
i_d, i_t, i_c = pn.index('defense_score'), pn.index('thesis_score'), pn.index('comprehensive_score')
bad = []
for r in rows['paper']:
    d, t2, c = r[i_d], r[i_t], r[i_c]
    if None in (d, t2, c):
        continue
    calc = round(0.4 * d + 0.6 * t2, 1)
    if abs(calc - c) > 0.05:
        bad.append((r[0], d, t2, c, calc))
print(f'总成绩=0.4*答辩+0.6*论文 不符: {len(bad)}/{len(rows["paper"])} {bad[:5]}')

# 8.2 部分成绩缺失
partial = [r[0] for r in rows['paper']
           if (r[i_d] is None) != (r[i_t] is None) or (r[i_c] is None) != (r[i_d] is None)]
print(f'成绩字段缺一半/不一致的论文: {len(partial)} {partial[:10]}')

# 8.3 student.teacher_id vs paper.teacher_id
sn = [c[0] for c in tables['student']['cols']]
si_t = sn.index('teacher_id'); si_id = sn.index('student_id'); si_cls = sn.index('class_id')
si_grade = sn.index('student_grade')
pi_s = pn.index('student_id'); pi_t = pn.index('teacher_id')
smap = {r[si_id]: r for r in rows['student']}
mismatch = []
for r in rows['paper']:
    s = smap.get(r[pi_s])
    if s and s[si_t] != r[pi_t]:
        mismatch.append((r[0], r[pi_s], 'student表', s[si_t], 'paper表', r[pi_t]))
print(f'指导教师双写不一致: {len(mismatch)} {mismatch[:5]}')

# 8.4 student 无 paper
pset = {r[pi_s] for r in rows['paper']}
print(f'学生总数={len(rows["student"])} 有论文={len(pset)} 无论文={len(rows["student"])-len(pset)}')
print(f'论文中学生不存在={len(pset - set(smap))}')

# 8.5 student.grade vs class.grade
cn = [c[0] for c in tables['class']['cols']]
ci_grade = cn.index('grade'); ci_id = cn.index('class_id')
cmap = {r[ci_id]: r[ci_grade] for r in rows['class']}
gmis = [(r[si_id], r[si_grade], cmap.get(r[si_cls])) for r in rows['student']
        if cmap.get(r[si_cls]) != r[si_grade]]
print(f'学生年级 != 班级年级: {len(gmis)}/{len(rows["student"])} {gmis[:5]}')

# 8.6 专业/班级冗余
print(f'class.grade 分布: {dict(collections.Counter(r[ci_grade] for r in rows["class"]))}')
print(f'student.student_grade 分布: {dict(collections.Counter(r[si_grade] for r in rows["student"]))}')
print(f'class.class_type 分布: {dict(collections.Counter(r[cn.index("class_type")] for r in rows["class"]))}')

# 8.7 教师无关联
td = [c[0] for c in tables['teacher_direction']['cols']]
tp = [c[0] for c in tables['teacher_profession']['cols']]
tn = [c[0] for c in tables['teacher']['cols']]
tids = {r[tn.index('teacher_id')] for r in rows['teacher']}
has_dir = {r[td.index('teacher_id')] for r in rows['teacher_direction']}
has_pro = {r[tp.index('teacher_id')] for r in rows['teacher_profession']}
print(f'教师={len(tids)} 有方向={len(has_dir)} 有可带专业={len(has_pro)} '
      f'无方向={len(tids-has_dir)} 无专业={len(tids-has_pro)}')
paper_teachers = {r[pi_t] for r in rows['paper']}
print(f'带论文的教师={len(paper_teachers)} 其中无任何方向={len(paper_teachers - has_dir)}')

# 8.8 direction 脏数据
dn = [c[0] for c in tables['direction']['cols']]
dvals = [r[dn.index('direction_name')] for r in rows['direction']]
print(f'\ndirection 总数={len(dvals)} 去重后={len(set(dvals))}')
sus = [v for v in dvals if re.search(r'[（(]|去重|可带|^计算机科学与技术$|^软件工程$|^人工智能$|^网络工程$|^数字媒体技术$|^数据科学与大数据技术$', v)]
print(f'direction 疑似误录(专业名/说明性文字) {len(sus)}: {sus}')
used = {r[td.index("direction_id")] for r in rows['teacher_direction']}
did = [c[0] for c in tables['direction']['cols']]
ids = [r[did.index('direction_id')]] if False else [0]
didx = did.index('direction_id')
ids = [r[didx] for r in rows['direction']]
unused = [v for i, v in zip(ids, dvals) if i not in used]
print(f'direction 未被任何教师使用 = {len(unused)}: {unused[:20]}')

# 8.9 论文题目重复
pt = pn.index('paper_title')
tc = collections.Counter(r[pt] for r in rows['paper'] if r[pt])
print(f'\n论文题目重复: {[(k, v) for k, v in tc.items() if v > 1]}')

# 8.10 teacher 联系方式
print('\nteacher 联系方式非空统计:')
tnx = [c[0] for c in tables['teacher']['cols']]
for c in ('teacher_qq', 'teacher_wechat', 'teacher_phone'):
    i = tnx.index(c)
    print(f'  {c:16s} 非空={sum(1 for r in rows["teacher"] if r[i] is not None)}/{len(rows["teacher"])}')
