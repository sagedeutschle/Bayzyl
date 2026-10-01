import struct
from typing import Any, Dict, List

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


def _write_name(buf: bytearray, name: str) -> None:
    encoded = name.encode("utf-8")
    buf += struct.pack(">H", len(encoded))
    buf += encoded


def _write_tag(buf: bytearray, tag_type: int, name: str, payload: bytes) -> None:
    buf.append(tag_type)
    _write_name(buf, name)
    buf += payload


def _write_payload(value: Any, tag_type: int) -> bytes:
    if tag_type == TAG_BYTE:
        return struct.pack(">b", value)
    if tag_type == TAG_SHORT:
        return struct.pack(">h", value)
    if tag_type == TAG_INT:
        return struct.pack(">i", value)
    if tag_type == TAG_LONG:
        return struct.pack(">q", value)
    if tag_type == TAG_FLOAT:
        return struct.pack(">f", value)
    if tag_type == TAG_DOUBLE:
        return struct.pack(">d", value)
    if tag_type == TAG_STRING:
        encoded = value.encode("utf-8")
        return struct.pack(">H", len(encoded)) + encoded
    if tag_type == TAG_BYTE_ARRAY:
        return struct.pack(">i", len(value)) + bytes(value)
    if tag_type == TAG_INT_ARRAY:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">i", v) for v in value)
    if tag_type == TAG_LONG_ARRAY:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">q", v) for v in value)
    if tag_type == TAG_LIST:
        list_type, items = value
        payload = bytearray()
        payload.append(list_type)
        payload += struct.pack(">i", len(items))
        for item in items:
            payload += _write_payload(item, list_type)
        return bytes(payload)
    if tag_type == TAG_COMPOUND:
        payload = bytearray()
        for key, (t, v) in value.items():
            _write_tag(payload, t, key, _write_payload(v, t))
        payload.append(TAG_END)
        return bytes(payload)
    raise ValueError(f"Unsupported tag type: {tag_type}")


def write_nbt(root_name: str, root: Dict[str, Any]) -> bytes:
    buf = bytearray()
    _write_tag(buf, TAG_COMPOUND, root_name, _write_payload(root, TAG_COMPOUND))
    return bytes(buf)
