#!/usr/bin/env python3
"""示例 MCP 服务（stdio JSON-RPC）。

这是一个**可以直接跑**的最小实现，用来验证「MCP 动态注册」这条链路：
在系统里把 MCP 服务的启动命令配成 ["python", "mcp/demo_server.py"] 并启用，
下一次对话时模型就能看到 ping / add / now 三个工具。

约束（与 EmbedAiChat 的约定一致）：
- 只读 stdin 的 JSON 行、只往 stdout 写 JSON 行；日志一律走 stderr。
- 不回显任何密钥；不执行 shell 命令。
- 协议只实现 initialize / notifications/initialized / tools/list / tools/call。
"""

from __future__ import annotations

import json
import sys
from datetime import datetime

SUPPORTED_VERSIONS = {"2025-11-25", "2025-06-18", "2024-11-05"}
DEFAULT_VERSION = "2024-11-05"

TOOLS = [
    {
        "name": "ping",
        "description": "检查示例 MCP 服务是否在线，返回 pong",
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
    {
        "name": "add",
        "description": "两个数相加，用于验证工具参数与返回值",
        "inputSchema": {
            "type": "object",
            "properties": {"a": {"type": "number"}, "b": {"type": "number"}},
            "required": ["a", "b"],
            "additionalProperties": False,
        },
    },
    {
        "name": "now",
        "description": "返回服务端当前时间（ISO 8601）",
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
]


def log(message: str) -> None:
    """写 stderr，避免污染协议通道。"""
    print(f"[demo-mcp] {message}", file=sys.stderr, flush=True)


def send(payload: dict) -> None:
    """写一行 JSON 到 stdout。"""
    sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def result(request_id, value: dict) -> None:
    send({"jsonrpc": "2.0", "id": request_id, "result": value})


def error(request_id, code: int, message: str) -> None:
    send({"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}})


def call_tool(name: str, arguments: dict) -> dict:
    """执行工具，返回 MCP 的 content 结构。"""
    if name == "ping":
        return {"content": [{"type": "text", "text": "pong"}], "isError": False}
    if name == "add":
        total = float(arguments.get("a", 0)) + float(arguments.get("b", 0))
        return {"content": [{"type": "text", "text": str(total)}], "isError": False}
    if name == "now":
        return {
            "content": [{"type": "text", "text": datetime.now().isoformat(timespec="seconds")}],
            "isError": False,
        }
    return {
        "content": [{"type": "text", "text": f"unknown tool: {name}"}],
        "isError": True,
    }


def main() -> None:
    log("started")
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            message = json.loads(line)
        except json.JSONDecodeError as exc:
            log(f"invalid json: {exc}")
            error(None, -32700, "parse error")
            continue

        method = message.get("method")
        request_id = message.get("id")
        params = message.get("params") or {}

        if method == "initialize":
            requested = params.get("protocolVersion", DEFAULT_VERSION)
            version = requested if requested in SUPPORTED_VERSIONS else DEFAULT_VERSION
            result(
                request_id,
                {
                    "protocolVersion": version,
                    "capabilities": {"tools": {}},
                    "serverInfo": {"name": "demo-mcp", "version": "1.0.0"},
                },
            )
        elif method == "notifications/initialized":
            continue
        elif method == "tools/list":
            result(request_id, {"tools": TOOLS})
        elif method == "tools/call":
            name = params.get("name")
            arguments = params.get("arguments") or {}
            if not isinstance(arguments, dict):
                error(request_id, -32602, "arguments must be an object")
                continue
            result(request_id, call_tool(str(name), arguments))
        elif request_id is not None:
            error(request_id, -32601, f"method not found: {method}")
    log("stdin closed, exiting")


if __name__ == "__main__":
    main()
