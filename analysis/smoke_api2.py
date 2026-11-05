# -*- coding: utf-8 -*-
"""接口冒烟（二）：成绩拆表后的写入与自动计算、唯一约束、列表关联展示。"""
import json
import sys
import io
import urllib.request
import urllib.error

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = 'http://127.0.0.1:8080'


def call(path, method='GET', body=None, token=None):
    data = None if body is None else json.dumps(body).encode('utf-8')
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header('Content-Type', 'application/json; charset=utf-8')
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read().decode('utf-8')
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        return {'__error__': e.code, 'body': e.read().decode('utf-8', 'replace')}


token = call('/api/auth/login', 'POST', {'username': 'admin', 'password': '123456'})['token']

print('=== 论文列表（前 3 条）===')
page = call('/api/models/paper?page=1&size=3', token=token)
rows = page.get('rows', [])
print('  总数:', page.get('total'))
for r in rows:
    stu = r.get('student') or {}
    tea = r.get('teacher') or {}
    print(f'   {r.get("title","")[:26]:28s} 学生={stu.get("label")} | 教师={tea.get("label")} | 状态={r.get("statusText")}')

print('\n=== 成绩独立表（前 3 条）===')
page = call('/api/models/paperScore?page=1&size=3', token=token)
print('  总数:', page.get('total'))
for r in page.get('rows', []):
    print(f'   答辩={r.get("defenseScore")} 论文={r.get("thesisScore")} 总评={r.get("comprehensiveScore")}')

paper_id = '07feb3f2-a19a-5db3-a0a6-0762593e5596'
print('\n=== 给未选题论文录成绩，并故意塞总成绩 ===')
created = call('/api/models/paperScore', 'POST', {
    'paperId': paper_id, 'defenseScore': 88.5, 'thesisScore': 76.0, 'comprehensiveScore': 12.3
}, token=token)
print('  原始响应:', json.dumps(created, ensure_ascii=False)[:300])
if created and '__error__' not in created:
    expected = round(76.0 * 0.6 + 88.5 * 0.4, 1)
    got = created.get('comprehensiveScore')
    print(f'  答辩={created.get("defenseScore")} 论文={created.get("thesisScore")} 总评={got}')
    print(f'  期望总评={expected} | 一致={got is not None and abs(float(got) - expected) < 0.001}')
    print(f'  接口直写总成绩(12.3)是否被忽略={str(got) != "12.3"}')

    print('\n=== 同一论文再录一条（应被唯一索引拒绝）===')
    dup = call('/api/models/paperScore', 'POST',
               {'paperId': paper_id, 'defenseScore': 60, 'thesisScore': 60}, token=token)
    print('  ', json.dumps(dup, ensure_ascii=False)[:220])

    print('\n=== 清理测试成绩 ===')
    print('  ', call('/api/models/paperScore/' + created['id'], 'DELETE', token=token))
