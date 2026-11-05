# -*- coding: utf-8 -*-
"""细看 (性质,类型,来源) 组合，判断各字母的语义。"""
import sys
import io
import collections

sys.path.insert(0, 'analysis')
from dump import rows, col_index as ci  # noqa: E402

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

P = rows['paper']
i_title = ci('paper', 'paper_title')
i_nat = ci('paper', 'paper_nature')
i_typ = ci('paper', 'paper_type')
i_src = ci('paper', 'paper_source')
i_dif = ci('paper', 'paper_difficulty')
data = [r for r in P if r[i_title] and r[i_title].strip()]

print('=' * 78)
print('1) 三元组合分布（性质, 类型, 来源）')
print('=' * 78)
combo = collections.Counter((r[i_nat], r[i_typ], r[i_src]) for r in data)
for (n, t, s), c in sorted(combo.items(), key=lambda kv: -kv[1]):
    print(f'  性质={n} 类型={t} 来源={s}  →  {c:4d} 条')

print('\n' + '=' * 78)
print('2) 性质=B 且 类型=B 的 38 条题目（看是不是“非系统类”）')
print('=' * 78)
for r in data:
    if r[i_nat] == 'B' and r[i_typ] == 'B':
        print(f'  [来源{r[i_src]}/难度{r[i_dif]}] {r[i_title][:60]}')

print('\n' + '=' * 78)
print('3) 来源=A 的 19 条题目')
print('=' * 78)
for r in data:
    if r[i_src] == 'A':
        print(f'  [性质{r[i_nat]}/类型{r[i_typ]}/难度{r[i_dif]}] {r[i_title][:58]}')

print('\n' + '=' * 78)
print('4) 来源=E 的 40 条 / 来源=C 的 80 条 / 来源=D 的 75 条 —— 各取 10 条')
print('=' * 78)
for src in ('C', 'D', 'E'):
    print(f'\n  来源={src}')
    shown = 0
    for r in data:
        if r[i_src] == src and shown < 10:
            print(f'    [性质{r[i_nat]}/类型{r[i_typ]}/难度{r[i_dif]}] {r[i_title][:56]}')
            shown += 1

print('\n' + '=' * 78)
print('5) 难度=A / B 的全部题目')
print('=' * 78)
for d in ('A', 'B'):
    print(f'\n  难度={d}')
    for r in data:
        if r[i_dif] == d:
            print(f'    [性质{r[i_nat]}/类型{r[i_typ]}/来源{r[i_src]}] {r[i_title][:56]}')
