# -*- coding: utf-8 -*-
"""从题目内容反推 课题性质/类型/来源 的 A–E 编码含义。"""
import sys
import io
import collections
import re

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

# 题目关键词分组（互斥性不强，只用来观察倾向）
GROUPS = {
    '硬件/嵌入式': r'单片机|STM32|FPGA|Arduino|树莓派|LoongArch|嵌入式|传感器|物联网|51单',
    '算法/AI研究': r'深度学习|机器学习|神经网络|YOLO|CNN|识别|检测|预测|算法|模型|分类|图像处理|自然语言|推荐',
    '管理系统/B端': r'管理系统|信息管理系统|后台管理|OA|CRM|ERP|教务|人事|仓储|图书管理',
    '电商/小程序': r'商城|购物|外卖|小程序|微信|电商|销售系统|点餐|预订|租赁',
    '实验/实训/平台': r'平台|网站|论坛|社区|交流|助手|工具',
}


def bucket(title):
    hits = [name for name, pat in GROUPS.items() if re.search(pat, title)]
    return hits[0] if hits else '其他'


def report(field_index, field_name):
    print('=' * 78)
    print(f'维度：{field_name}')
    print('=' * 78)
    counter = collections.Counter(r[field_index] for r in data)
    for value, count in sorted(counter.items(), key=lambda kv: -kv[1]):
        subset = [r for r in data if r[field_index] == value]
        buckets = collections.Counter(bucket(r[i_title]) for r in subset)
        total = len(subset)
        top = '、'.join(f'{k}{v*100//total}%' for k, v in buckets.most_common(4))
        print(f'\n  {value}  （{count} 条，{count*100/len(data):.1f}%）')
        print(f'    题目类型分布：{top}')
        for r in subset[:8]:
            print(f'      - {r[i_title][:52]}')
    print()


for idx, name in ((i_nat, 'paper_nature 课题性质'), (i_typ, 'paper_type 课题类型'),
                  (i_src, 'paper_source 课题来源'), (i_dif, 'paper_difficulty 课题难度')):
    report(idx, name)

# 维度之间是否独立
print('=' * 78)
print('交叉表：性质 × 类型')
print('=' * 78)
cross = collections.Counter((r[i_nat], r[i_typ]) for r in data)
for (a, b), c in sorted(cross.items()):
    print(f'  性质={a} 类型={b} : {c}')

print('\n交叉表：难度 × 性质 / 类型 / 来源')
for label, idx in (('性质', i_nat), ('类型', i_typ), ('来源', i_src)):
    print(f'\n  难度 × {label}')
    c2 = collections.Counter((r[i_dif], r[idx]) for r in data)
    for (d, x), n in sorted(c2.items()):
        print(f'    难度={d} {label}={x} : {n}')
