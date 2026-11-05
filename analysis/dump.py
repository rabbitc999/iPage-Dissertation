# -*- coding: utf-8 -*-
"""dissertation.sql 解析器：被分析脚本复用，产出 tables(结构) 与 rows(数据)。"""
import re
import collections

SRC = r'D:\Desktop\dissertation.sql'
_txt = open(SRC, encoding='utf-8').read()

# ---------- 1. 表结构 ----------
tables = {}
for _m in re.finditer(r'CREATE TABLE `(\w+)`\s*\((.*?)\n\) ENGINE', _txt, re.S):
    _name, _body = _m.group(1), _m.group(2)
    _cols, _idx = [], []
    for _line in _body.split('\n'):
        _line = _line.strip().rstrip(',')
        if not _line:
            continue
        if _line.startswith('PRIMARY KEY'):
            _idx.append(('PK', _line))
        elif _line.startswith(('INDEX', 'UNIQUE INDEX', 'KEY')):
            _idx.append(('IDX', _line))
        elif _line.startswith('CONSTRAINT'):
            _idx.append(('FK', _line))
        else:
            _cm = re.match(r'`(\w+)`\s+(\S+?)(\s|$)', _line)
            if _cm:
                _cols.append((_cm.group(1), _cm.group(2)))
    tables[_name] = {'cols': _cols, 'idx': _idx}


def col_index(table, col):
    return [c[0] for c in tables[table]['cols']].index(col)


def col_name(table, idx):
    return tables[table]['cols'][idx][0]


# ---------- 2. 数据 ----------
rows = collections.defaultdict(list)
for _m in re.finditer(r'INSERT INTO `(\w+)` VALUES \((.*?)\);\s*$', _txt, re.M):
    _t, _raw = _m.group(1), _m.group(2)
    _vals, _cur, _inq, _esc = [], '', False, False
    for _ch in _raw:
        if _esc:
            _cur += _ch
            _esc = False
            continue
        if _ch == '\\':
            _cur += _ch
            _esc = True
            continue
        if _ch == "'":
            _inq = not _inq
            _cur += _ch
            continue
        if _ch == ',' and not _inq:
            _vals.append(_cur.strip())
            _cur = ''
            continue
        _cur += _ch
    if _cur.strip():
        _vals.append(_cur.strip())

    def _conv(v):
        if v == 'NULL':
            return None
        if v.startswith("'"):
            s = v[1:-1]
            s = s.replace("\\'", "'").replace('\\"', '"').replace('\\\\', '\\')
            return s.replace('\\n', '\n').replace('\\r', '\r')
        try:
            return int(v)
        except ValueError:
            try:
                return float(v)
            except ValueError:
                return v

    rows[_t].append([_conv(v) for v in _vals])
