#!/usr/bin/env python3
"""Apply AyuGram's TL compatibility patch to the generated TLRPC source."""
from pathlib import Path
import re

path = Path("TMessagesProj/src/main/java/org/telegram/tgnet/TLRPC.java")
if not path.is_file():
    raise SystemExit(f"Missing TLRPC source: {path}")
data = path.read_text(encoding="utf-8")

if "public boolean ayuNoforwards;" not in data:
    data, n = re.subn(
        r"noforwards = (?P<flags>[^;]+);",
        "noforwards = false;\n            ayuNoforwards = \\g<flags>;",
        data,
    )
    if n == 0:
        raise SystemExit("Could not find the generated noforwards constructor assignment")
    old = "public boolean noforwards;"
    if old not in data:
        raise SystemExit("Could not find the generated noforwards field")
    data = data.replace(old, old + "\n        public boolean ayuNoforwards;", 1)

if "public boolean ayuDeleted;" not in data:
    old = "public boolean from_scheduled;"
    if old not in data:
        raise SystemExit("Could not find the generated from_scheduled field")
    data = data.replace(old, old + "\n        public boolean ayuDeleted;", 1)

# AyuGram consumes these Telegram flags as its own state; clear the original
# assignments so they are not represented twice in the UI model.
data = re.sub(r"restricted = [^;]+;", "restricted = false;", data)
data = re.sub(r"history_deleted = [^;]+;", "history_deleted = false;", data)

path.write_text(data, encoding="utf-8")
print("AyuGram TL compatibility patch applied.")
