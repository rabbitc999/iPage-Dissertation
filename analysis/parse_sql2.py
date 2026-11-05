# -*- coding: utf-8 -*-
import re, collections, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.path.insert(0, 'analysis')
from dump import tables, rows

pn = [c[0] for c in tables['paper']['cols']]
sn = [c[0] for c in tables['student']['cols']]
tn = [c[0] for c in tables['teacher']['cols']]
cn = [c[0] for c in tables['class']['cols']]

print('=== 空标题论文明细 ===')
bad = [r for r in rows['paper'] if r[pn.index('paper_title')] == '']
for r in bad:
    sid = r[pn.index('student_id')]
    s = rows['student'][sid - 1]
    print(' paper_id=%d student_id=%d 学生=%s 学号=%s 教师=%d' %
          (r[0], sid, s[sn.index('student_name')], s[sn.index('student_no')], r[pn.index('teacher_id')]))
print('空标题同时成绩全空 =', all(r[pn.index('defense_score')] is None for r in bad))

print('\n=== 成绩异常 ===')
i_d, i_t, i_c = pn.index('defense_score'), pn.index('thesis_score'), pn.index('comprehensive_score')
for r in rows['paper']:
    for i, n in ((i_d, '答辩'), (i_t, '论文'), (i_c, '总')):
        v = r[i]
        if v is not None and (v < 60 or v > 100):
            pass
vals = [r[i_c] for r in rows['paper'] if r[i_c] is not None]
print('总成绩 min=%.1f max=%.1f 平均=%.2f  <60 人数=%d' %
      (min(vals), max(vals), sum(vals) / len(vals), sum(1 for v in vals if v < 60)))
dv = sorted(vals)
print('分位数 P25=%.1f P50=%.1f P75=%.1f' % (dv[len(dv)//4], dv[len(dv)//2], dv[3*len(dv)//4]))

print('\n=== 学生学号格式 ===')
nos = [r[sn.index('student_no')] for r in rows['student']]
print('样例', nos[:5], '长度分布', collections.Counter(len(n) for n in nos))
print('非纯数字', [n for n in nos if not n.isdigit()][:10])

print('\n=== 指导教师负载 ===')
load = collections.Counter(r[pn.index('teacher_id')] for r in rows['paper'])
print('教师数=%d 人均=%.1f 最大=%d 最小=%d' % (len(load), len(rows['paper'])/len(load), max(load.values()), min(load.values())))
print('负载 Top5', load.most_common(5))
print('负载 Bottom5', load.most_common()[-5:])
print('0 篇论文的教师', sorted({r[tn.index('teacher_id')] for r in rows['teacher']} - set(load)))

print('\n=== 教师门禁：paper.teacher_id 是否必等于 student.teacher_id ===')
smap = {r[sn.index('student_id')]: r for r in rows['student']}
same = sum(1 for r in rows['paper'] if smap[r[pn.index('student_id')]][sn.index('teacher_id')] == r[pn.index('teacher_id')])
print('一致 %d / %d = %.1f%%' % (same, len(rows['paper']), 100*same/len(rows['paper'])))

print('\n=== teacher_id=77 缺口影响 ===')
print('paper 引用 77 =', sum(1 for r in rows['paper'] if r[pn.index('teacher_id')] == 77))
print('student 引用 77 =', sum(1 for r in rows['student'] if r[sn.index('teacher_id')] == 77))

print('\n=== 班级分布 ===')
cl = collections.Counter(r[sn.index('class_id')] for r in rows['student'])
print('班级数=%d 每班人数 min=%d max=%d' % (len(cl), min(cl.values()), max(cl.values())))
print('空班级', [r[cn.index('class_id')] for r in rows['class'] if r[cn.index('class_id')] not in cl])

print('\n=== 学生年级!=班级年级 明细 ===')
ci_grade, ci_id = cn.index('grade'), cn.index('class_id')
cmap = {r[ci_id]: r[ci_grade] for r in rows['class']}
g = [(r[sn.index('student_id')], r[sn.index('student_grade')], cmap[r[sn.index('class_id')]], r[sn.index('class_id')]) for r in rows['student'] if cmap[r[sn.index('class_id')]] != r[sn.index('student_grade')]]
print(g)

print('\n=== 专业/班级/方向 冗余检查 ===')
print('class 表含 profession_id + grade，student 表又含 student_grade —— 三元冗余')
print('direction 表中与 profession 同名项：')
prof = [r[2] for r in rows['profession']]
dirs = [r[1] for r in rows['direction']]
print(' profession:', prof)
print(' direction ∩ profession:', [d for d in dirs if d in prof])

print('\n=== 每教师方向数 / 可带专业数 ===')
td = [c[0] for c in tables['teacher_direction']['cols']]
tp = [c[0] for c in tables['teacher_profession']['cols']]
dc = collections.Counter(r[td.index('teacher_id')] for r in rows['teacher_direction'])
pc = collections.Counter(r[tp.index('teacher_id')] for r in rows['teacher_profession'])
print('方向数 min=%d max=%d 平均=%.1f' % (min(dc.values()), max(dc.values()), sum(dc.values())/len(dc)))
print('可带专业数 min=%d max=%d 平均=%.1f' % (min(pc.values()), max(pc.values()), sum(pc.values())/len(pc)))
