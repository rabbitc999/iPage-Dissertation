# -*- coding: utf-8 -*-
"""接口冒烟：登录 → 导航 → schema → 列表 → 成绩计算钩子。"""
import json
import sys
import io
import urllib.request

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = 'http://127.0.0.1:8080'


def call(path, method='GET', body=None, token=None):
    data = None if body is None else json.dumps(body).encode('utf-8')
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header('Content-Type', 'application/json; charset=utf-8')
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode('utf-8'))


login = call('/api/auth/login', 'POST', {'username': 'admin', 'password': '123456'})
token = login['token']
print('登录成功，token 前缀:', token[:16])

nav = call('/api/nav', token=token)
print('\n=== 导航分组 ===')
groups = nav.get('groups') if isinstance(nav, dict) else None
if groups is None:
    print(json.dumps(nav, ensure_ascii=False)[:400])
else:
    for group in groups:
        names = [m.get('name') for m in group.get('models', [])]
        print(f'  {group.get("name")}: ' + '、'.join(names))

print('\n=== 论文模型 schema ===')
schema = call('/api/models/paper/schema', token=token)
print(' 模型名:', schema.get('name'), '| 表:', schema.get('table'))
for f in schema.get('fields', []):
    print(f'   - {f.get("text"):8s} {f.get("name"):20s} type={f.get("type")}')
