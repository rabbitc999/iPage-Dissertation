"""IMS stdio MCP bridge. Uploads files under an explicitly configured local directory."""
import argparse
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

from ims_cli import DEFAULT_BASE, load_token

MAX_FILE_BYTES = 10 * 1024 * 1024
PROTOCOL_VERSIONS = {"2025-11-25", "2025-06-18", "2024-11-05"}
TOOL = {
    "name": "ims_upload_record_file",
    "description": "Upload a local picture or file to an IMS ticket or asset and optionally update its fields atomically.",
    "inputSchema": {
        "type": "object",
        "properties": {
            "model": {"enum": ["ticket", "asset"]},
            "recordId": {"type": "string", "format": "uuid"},
            "path": {"type": "string", "description": "Path relative to the configured upload directory"},
            "values": {"type": "object", "description": "Optional editable IMS field names and values"},
        },
        "required": ["model", "recordId", "path"],
        "additionalProperties": False,
    },
}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """Never forward the IMS login token to a redirected destination."""

    def redirect_request(self, *args, **kwargs):
        return None


def upload(base, root, model_id, tool_access, arguments):
    model = arguments.get("model")
    if model not in ("ticket", "asset"):
        raise ValueError("请选择工单或资产")
    record_id = str(uuid.UUID(arguments.get("recordId", "")))
    values = arguments.get("values", {})
    if not isinstance(values, dict):
        raise ValueError("values 必须是 JSON 对象")
    relative = Path(arguments["path"])
    if relative.is_absolute():
        raise ValueError("请填写相对于上传目录的路径")
    path = (root / relative).resolve(strict=True)
    if not path.is_relative_to(root) or not path.is_file():
        raise ValueError("只能上传配置目录中的普通文件")
    token = os.environ.get("IMS_TOKEN") or load_token()
    if not token:
        raise ValueError("请先使用 IMS CLI 登录，或为 MCP 进程设置当前账号的 IMS_TOKEN")
    with path.open("rb") as source:
        content = source.read(MAX_FILE_BYTES + 1)
    if not content or len(content) > MAX_FILE_BYTES:
        raise ValueError("请选择 10MB 以内的非空文件")

    boundary = "ims-" + uuid.uuid4().hex
    filename = urllib.parse.quote(path.name, safe="")
    mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    file_part = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"attachment\"; "
        f"filename*=UTF-8''{filename}\r\nContent-Type: {mime}\r\n\r\n"
    ).encode()
    values_part = (
        f"\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"values\"\r\n"
        f"Content-Type: application/json\r\n\r\n{json.dumps(values, ensure_ascii=True)}\r\n--{boundary}--\r\n"
    ).encode()
    options = urllib.parse.urlencode({"aiModelId": model_id, "toolAccess": tool_access})
    request = urllib.request.Request(f"{base}/api/models/{model}/{record_id}/attachments?{options}",
        data=file_part + content + values_part,
        headers={"Authorization": "Bearer " + token, "Content-Type": f"multipart/form-data; boundary={boundary}"})
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read(8192)
        try:
            message = json.loads(detail).get("message")
        except (ValueError, AttributeError):
            message = None
        raise ValueError(message or f"IMS 请求失败（HTTP {error.code}）") from None


def handle(request, base, root, model_id, tool_access):
    method = request.get("method")
    params = request.get("params", {})
    if method == "initialize":
        version = params.get("protocolVersion")
        return {
            "protocolVersion": version if version in PROTOCOL_VERSIONS else "2025-11-25",
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "ipage-ims-attachments", "version": "1.0.0"},
        }
    if method == "ping":
        return {}
    if method == "tools/list":
        return {"tools": [TOOL]}
    if method == "tools/call" and params.get("name") == TOOL["name"]:
        try:
            result = upload(base, root, model_id, tool_access, params.get("arguments", {}))
            return {"content": [{"type": "text", "text": json.dumps(result, ensure_ascii=False)}]}
        except (OSError, ValueError, TypeError, KeyError) as error:
            return {"content": [{"type": "text", "text": str(error)}], "isError": True}
    raise LookupError()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=DEFAULT_BASE)
    parser.add_argument("--directory", required=True, type=Path)
    parser.add_argument("--model-id", required=True, type=uuid.UUID, help="IMS model configuration ID")
    parser.add_argument("--tool-access", choices=("READ_ONLY", "ADD", "ADD_EDIT", "FULL"), default="READ_ONLY")
    args = parser.parse_args()
    base = args.base.rstrip("/")
    url = urllib.parse.urlsplit(base)
    if (not url.hostname or url.username or url.password or url.path or url.query or url.fragment
            or not (url.scheme == "https" or url.scheme == "http" and url.hostname in ("localhost", "127.0.0.1", "::1"))):
        parser.error("IMS 地址必须是 HTTPS 或本机 HTTP 服务的基础地址")
    root = args.directory.resolve()
    if not root.is_dir():
        parser.error("上传目录不存在")

    for line in sys.stdin.buffer:
        reply = {"jsonrpc": "2.0", "id": None}
        try:
            request = json.loads(line)
            if not isinstance(request, dict) or request.get("jsonrpc") != "2.0":
                raise TypeError()
            if "id" not in request:
                continue
            reply["id"] = request["id"]
            reply["result"] = handle(request, base, root, args.model_id, args.tool_access)
        except json.JSONDecodeError:
            reply["error"] = {"code": -32700, "message": "Parse error"}
        except LookupError:
            reply["error"] = {"code": -32601, "message": "Method or tool not found"}
        except (TypeError, AttributeError):
            reply["error"] = {"code": -32602, "message": "Invalid request or parameters"}
        print(json.dumps(reply, ensure_ascii=True), flush=True)


if __name__ == "__main__":
    main()
