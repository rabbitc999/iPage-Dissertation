# -*- coding: utf-8 -*-
"""用 sqlglot 对 MySQL 方言做语法校验，并做一批自定义规则检查。"""
import re
import sys
import io
import glob
import sqlglot
from sqlglot import exp

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')


def split_statements(sql):
    """按分号切分，跳过注释与字符串内的分号。"""
    out, cur, i, n = [], [], 0, len(sql)
    in_s = in_d = in_b = in_line = in_blk = False
    while i < n:
        ch = sql[i]
        nxt = sql[i + 1] if i + 1 < n else ''
        if in_line:
            cur.append(ch)
            if ch == '\n':
                in_line = False
            i += 1
            continue
        if in_blk:
            cur.append(ch)
            if ch == '*' and nxt == '/':
                cur.append(nxt)
                i += 2
                in_blk = False
                continue
            i += 1
            continue
        if in_s:
            cur.append(ch)
            if ch == '\\':
                if i + 1 < n:
                    cur.append(sql[i + 1])
                i += 2
                continue
            if ch == "'":
                in_s = False
            i += 1
            continue
        if in_d:
            cur.append(ch)
            if ch == '`':
                in_d = False
            i += 1
            continue
        if ch == '-' and nxt == '-':
            in_line = True
            cur.append(ch)
            i += 1
            continue
        if ch == '/' and nxt == '*':
            in_blk = True
            cur.append(ch)
            i += 1
            continue
        if ch == "'":
            in_s = True
            cur.append(ch)
            i += 1
            continue
        if ch == '`':
            in_d = True
            cur.append(ch)
            i += 1
            continue
        if ch == ';':
            stmt = ''.join(cur).strip()
            if stmt and not re.fullmatch(r'[\s;]*', stmt):
                out.append(stmt)
            cur = []
            i += 1
            continue
        cur.append(ch)
        i += 1
    tail = ''.join(cur).strip()
    if tail and not re.fullmatch(r'[\s;]*', tail):
        out.append(tail)
    return out


def strip_comments_only(s):
    s = re.sub(r'/\*.*?\*/', '', s, flags=re.S)
    s = re.sub(r'--[^\n]*', '', s)
    return s.strip()


def validate(path):
    sql = open(path, encoding='utf-8').read()
    stmts = split_statements(sql)
    ok = bad = skip = 0
    print('=' * 78)
    print('文件:', path, '| 语句数:', len(stmts))
    print('=' * 78)
    for idx, st in enumerate(stmts, 1):
        body = strip_comments_only(st)
        if not body:
            skip += 1
            continue
        head = body.split(None, 3)
        kw = ' '.join(head[:2]).upper()
        if body.upper().startswith(('SET ', 'DROP TABLE', 'USE ')):
            print(f'  [{idx:3d}] 跳过(session/DDL清理): {body.splitlines()[0][:70]}')
            skip += 1
            continue
        try:
            sqlglot.parse_one(body, dialect='mysql')
            ok += 1
        except Exception as e:  # noqa: BLE001
            bad += 1
            msg = str(e).splitlines()[0][:200]
            print(f'  [{idx:3d}] ✗ 解析失败: {msg}')
            print('        ' + body.splitlines()[0][:110])
    print(f'\n结果: 通过={ok} 失败={bad} 跳过={skip}')
    return bad


if __name__ == '__main__':
    files = sys.argv[1:] or sorted(glob.glob('dissertation-refactor/*.sql'))
    total = sum(validate(f) for f in files)
    sys.exit(1 if total else 0)
