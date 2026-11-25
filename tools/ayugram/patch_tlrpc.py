#!/usr/bin/env python3
"""Apply AyuGram's TL compatibility patch to the generated TLRPC source."""
from pathlib import Path
import re

path = Path("TMessagesProj/src/main/java/org/telegram/tgnet/TLRPC.java")
if not path.is_file():
    raise SystemExit(f"Missing TLRPC source: {path}")
data = path.read_text(encoding="utf-8")

# Preserve Telegram's noforwards bit separately for AyuGram's forwarding rules.
if "ayuNoforwards = hasFlag(flags, FLAG_26);" not in data:
    data, n = re.subn(
        r"noforwards = (?P<flags>[^;]+);",
        "noforwards = false;\n            ayuNoforwards = \\g<flags>;",
        data,
    )
    if n == 0:
        raise SystemExit("Could not find the generated noforwards constructor assignment")

# The flag is referenced through both Chat and Message base types.
for class_name in ("Chat", "Message"):
    match = re.search(
        rf"(?:public )?static\s+(?:abstract\s+)?class\s+{class_name}\s+extends\s+[^\{{]+\{{",
        data,
    )
    if not match:
        raise SystemExit(f"Could not find the generated {class_name} base class")
    body_end = data.find("class TL_", match.end())
    if body_end < 0:
        body_end = len(data)
    body = data[match.end():body_end]
    if "public boolean ayuNoforwards;" not in body:
        data = data[:match.end()] + "\n        public boolean ayuNoforwards;" + data[match.end():]

if "public boolean ayuDeleted;" not in data:
    old = "public boolean from_scheduled;"
    if old not in data:
        raise SystemExit("Could not find the generated from_scheduled field")
    data = data.replace(old, old + "\n        public boolean ayuDeleted;", 1)

# AyuGram consumes these Telegram flags as its own state.
data = re.sub(r"restricted = [^;]+;", "restricted = false;", data)
data = re.sub(r"history_deleted = [^;]+;", "history_deleted = false;", data)

path.write_text(data, encoding="utf-8")
print("AyuGram TL compatibility patch applied.")
