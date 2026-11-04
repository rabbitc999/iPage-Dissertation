#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""iPage-IMS 命令行客户端。

一个零依赖的参考客户端（只用 Python 标准库），演示独立客户端如何对接 iPage-IMS：

  * 登录后把令牌缓存在本地，后续命令自动带上 Authorization；
  * 字段从 /api/models/{key}/schema 读取，不在客户端写死任何业务字段；
  * 主键一律按字符串处理（UUID），不做数值转换；
  * 错误直接把服务端返回的中文 message 打印出来。

用法示例::

    python ims_cli.py login --user admin --password 123456
    python ims_cli.py models
    python ims_cli.py schema employee
    python ims_cli.py list employee --kw 苏 --size 5
    python ims_cli.py show employee <uuid>
    python ims_cli.py create employee --set empNo=E2001 --set name=新员工
    python ims_cli.py update employee <uuid> --set position=技术总监
    python ims_cli.py delete employee <uuid>

服务地址默认 http://127.0.0.1:8080，可用环境变量 IMS_BASE 或 --base 指定。

本文件与仓库其余原创代码同为 MIT 许可证。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DEFAULT_BASE = os.environ.get("IMS_BASE", "http://127.0.0.1:8080")
TOKEN_FILE = Path(os.environ.get("IMS_TOKEN_FILE", Path.home() / ".ims_token"))


# --------------------------------------------------------------------------- HTTP


class ApiError(Exception):
    """服务端返回的错误，携带 HTTP 状态码。"""

    def __init__(self, message: str, status: int = 0) -> None:
        super().__init__(message)
        self.status = status


def api(base: str, path: str, method: str = "GET", body=None, token=None, params=None):
    """调用一个接口。

    :param base:   服务地址，如 http://127.0.0.1:8080
    :param path:   以 / 开头的路径，不含 /api
    :param method: HTTP 方法
    :param body:   请求体（会被序列化为 UTF-8 JSON）
    :param token:  令牌；为空则不带 Authorization
    :param params: 查询参数，空值自动跳过
    :return: 解析后的 JSON
    """
    url = base.rstrip("/") + "/api" + path
    if params:
        # 中文必须百分号编码，否则服务端收到的是乱码
        query = urllib.parse.urlencode(
            {k: v for k, v in params.items() if v not in (None, "")},
            encoding="utf-8",
        )
        if query:
            url += "?" + query

    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        # ensure_ascii=False 才能发出真正的中文，而不是 \uXXXX
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            text = response.read().decode("utf-8")
            return json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "replace")
        try:
            message = json.loads(text).get("message") or text
        except (ValueError, AttributeError):
            message = text or f"请求失败（{error.code}）"
        raise ApiError(message, error.code) from None
    except urllib.error.URLError as error:
        raise ApiError(f"无法连接服务端 {base}：{error.reason}") from None


# --------------------------------------------------------------------------- 令牌


def save_token(token: str, username: str) -> None:
    """把令牌写到本地文件。生产客户端应改用系统凭据库。"""
    TOKEN_FILE.write_text(
        json.dumps({"token": token, "username": username}, ensure_ascii=False),
        encoding="utf-8",
    )
    try:
        TOKEN_FILE.chmod(0o600)  # 仅本人可读
    except OSError:
        pass  # Windows 上可能不支持，忽略


def load_token() -> str | None:
    """读取本地令牌；不存在或损坏时返回 None。"""
    if not TOKEN_FILE.exists():
        return None
    try:
        return json.loads(TOKEN_FILE.read_text(encoding="utf-8")).get("token")
    except (ValueError, OSError):
        return None


def require_token() -> str:
    token = load_token()
    if not token:
        raise SystemExit("尚未登录，请先执行：python ims_cli.py login --user admin --password 123456")
    return token


# --------------------------------------------------------------------------- 展示


def print_table(rows: list[dict], columns: list[dict]) -> None:
    """按列打印，中文按显示宽度对齐。

    :param rows:    行数据
    :param columns: [{"name": 字段名, "title": 表头, "type": 类型}, ...]
    """
    if not rows:
        print("（没有数据）")
        return

    def cell(row: dict, column: dict) -> str:
        name = column["name"]
        value = row.get(name)
        # 枚举取服务端下发的 xxxText（"启用"），而不是枚举名（"ENABLED"）
        if column.get("type") == "ENUM":
            text = row.get(name + "Text")
            return str(text) if text else "—"
        # 关联对象 {id,label} 只显示 label，UUID 对用户没有意义
        if isinstance(value, dict):
            return str(value.get("label") or "—")
        if column.get("type") == "BOOLEAN":
            return "是" if value else "否"
        if value is None or value == "":
            return "—"
        return str(value)

    def width(text: str) -> int:
        # 中日韩字符占两列，否则中文列会错位
        return sum(2 if ord(ch) > 0x2E80 else 1 for ch in text)

    headers = [c["title"] for c in columns]
    body = [[cell(row, c) for c in columns] for row in rows]
    widths = [max(width(h), *(width(r[i]) for r in body)) for i, h in enumerate(headers)]

    def line(values: list[str]) -> str:
        return "  ".join(v + " " * (widths[i] - width(v)) for i, v in enumerate(values))

    print(line(headers))
    print("  ".join("-" * w for w in widths))
    for values in body:
        print(line(values))


def columns_from_schema(schema: dict) -> list[dict]:
    """从 schema 推导列表列：table=true 的字段，按 order 排序。

    客户端不写死任何业务字段——服务端加字段，这里自动多一列。
    """
    fields = [f for f in schema.get("fields", []) if f.get("table")]
    fields.sort(key=lambda f: f.get("order", 0))
    return [
        {"name": f["name"], "title": f.get("text") or f["name"], "type": f.get("type")}
        for f in fields
    ]


# --------------------------------------------------------------------------- 子命令


def cmd_login(args) -> None:
    result = api(args.base, "/auth/login", "POST",
                 {"username": args.user, "password": args.password})
    save_token(result["token"], args.user)
    user = result["user"]
    print(f"登录成功：{user['name']}（{user['username']}）")
    print(f"  等级 {user['level']} · 数据范围 {user['dataScope']} · 权限点 {len(user.get('permissions') or [])} 个")
    print(f"  令牌已保存到 {TOKEN_FILE}")


def cmd_models(args) -> None:
    nav = api(args.base, "/nav", token=require_token())
    for item in nav:
        print(f"[{item['group']}] {item['key']:<16} {item['name']}")


def cmd_schema(args) -> None:
    schema = api(args.base, f"/models/{args.model}/schema", token=require_token())
    print(f"{schema['name']}（{schema['key']}）  表 {schema['table']}")
    permissions = schema.get("permissions") or {}
    print("权限：" + "  ".join(f"{k}={v}" for k, v in permissions.items()))
    print()
    print_table(
        [{"name": f["name"], "title": f.get("text"), "type": f["type"],
          "required": f.get("required"), "table": f.get("table"), "form": f.get("form")}
         for f in schema.get("fields", [])],
        [{"name": "name", "title": "字段"},
         {"name": "title", "title": "名称"},
         {"name": "type", "title": "类型"},
         {"name": "required", "title": "必填"},
         {"name": "table", "title": "列表"},
         {"name": "form", "title": "表单"}],
    )


def cmd_list(args) -> None:
    token = require_token()
    schema = api(args.base, f"/models/{args.model}/schema", token=token)
    result = api(args.base, f"/models/{args.model}", token=token, params={
        "page": args.page, "size": args.size, "kw": args.kw,
        "sort": args.sort, "order": args.order,
    })
    columns = columns_from_schema(schema)
    if args.with_id:
        # 主键默认不展示（UUID 太长），但 show/update/delete 需要，所以给它一个开关
        columns.append({"name": "id", "title": "ID", "type": "STRING"})
    print_table(result["rows"], columns)
    print()
    if not result["total"]:
        # 注意：这可能是数据范围（SELF/DEPT）过滤后的结果，不一定是"库里没有数据"
        print("共 0 条（如有数据但看不到，多半是当前角色的数据范围限制）")
    else:
        print(f"第 {result['page']}/{result['pages']} 页，共 {result['total']} 条")


def cmd_show(args) -> None:
    row = api(args.base, f"/models/{args.model}/{args.id}", token=require_token())
    for key, value in row.items():
        if isinstance(value, dict):
            value = f"{value.get('label')}（{value.get('id')}）"
        print(f"{key:<20} {value}")


def cmd_create(args) -> None:
    body = parse_pairs(args.set or [])
    row = api(args.base, f"/models/{args.model}", "POST", body, token=require_token())
    print(f"已新增：{row.get('_display')}  id={row.get('id')}")


def cmd_update(args) -> None:
    body = parse_pairs(args.set or [])
    row = api(args.base, f"/models/{args.model}/{args.id}", "PUT", body, token=require_token())
    print(f"已保存：{row.get('_display')}  id={row.get('id')}")


def cmd_delete(args) -> None:
    if not args.yes:
        answer = input(f"确认删除 {args.model}/{args.id}？输入 yes 继续：").strip().lower()
        if answer != "yes":
            print("已取消")
            return
    api(args.base, f"/models/{args.model}/{args.id}", "DELETE", token=require_token())
    print("已删除")


def parse_pairs(pairs: list[str]) -> dict:
    """把 --set k=v 解析成字典；值保持字符串，类型交给服务端转换。"""
    body = {}
    for pair in pairs:
        if "=" not in pair:
            raise SystemExit(f"--set 需要 k=v 形式，收到：{pair}")
        key, value = pair.split("=", 1)
        body[key.strip()] = value
    return body


# --------------------------------------------------------------------------- 入口


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="iPage-IMS 命令行客户端")
    parser.add_argument("--base", default=DEFAULT_BASE, help=f"服务地址，默认 {DEFAULT_BASE}")
    sub = parser.add_subparsers(dest="command", required=True)

    login = sub.add_parser("login", help="登录并缓存令牌")
    login.add_argument("--user", required=True)
    login.add_argument("--password", required=True)
    login.set_defaults(func=cmd_login)

    models = sub.add_parser("models", help="列出可访问的模型")
    models.set_defaults(func=cmd_models)

    schema = sub.add_parser("schema", help="查看模型字段定义")
    schema.add_argument("model")
    schema.set_defaults(func=cmd_schema)

    list_cmd = sub.add_parser("list", help="分页查询")
    list_cmd.add_argument("model")
    list_cmd.add_argument("--kw", default=None, help="关键字")
    list_cmd.add_argument("--page", type=int, default=1)
    list_cmd.add_argument("--size", type=int, default=10)
    list_cmd.add_argument("--sort", default=None, help="排序字段（Java 字段名）")
    list_cmd.add_argument("--order", default="desc", choices=["asc", "desc"])
    list_cmd.add_argument("--with-id", action="store_true", help="额外显示主键列（便于接 show/update/delete）")
    list_cmd.set_defaults(func=cmd_list)

    show = sub.add_parser("show", help="按主键查看一条")
    show.add_argument("model")
    show.add_argument("id")
    show.set_defaults(func=cmd_show)

    create = sub.add_parser("create", help="新增")
    create.add_argument("model")
    create.add_argument("--set", action="append", metavar="k=v")
    create.set_defaults(func=cmd_create)

    update = sub.add_parser("update", help="修改（只改传入字段）")
    update.add_argument("model")
    update.add_argument("id")
    update.add_argument("--set", action="append", metavar="k=v")
    update.set_defaults(func=cmd_update)

    delete = sub.add_parser("delete", help="删除")
    delete.add_argument("model")
    delete.add_argument("id")
    delete.add_argument("--yes", action="store_true", help="跳过确认")
    delete.set_defaults(func=cmd_delete)

    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        args.func(args)
        return 0
    except ApiError as error:
        # 服务端的 message 已经是中文可读的，直接透出，不要改写成"操作失败"
        print(f"错误：{error}", file=sys.stderr)
        if error.status == 401:
            print("提示：令牌已失效，请重新 login", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
