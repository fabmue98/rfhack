#!/usr/bin/env python3
"""Packt das Projekt als .caproj (ZIP) für den Import in CodeAssist.

Aufruf:  python3 tools/build_caproj.py [ausgabe.caproj]
Die Unit-Tests (app/src/test) werden nicht mit eingepackt.
"""
import json
import os
import sys
import time
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "arbeitszeit-1.3.caproj")

def include(rel):
    return not rel.startswith("project/app/src/test/")

files = []
for base in ("project", "deps"):
    for dirpath, _, names in os.walk(os.path.join(ROOT, base)):
        for name in sorted(names):
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, ROOT).replace(os.sep, "/")
            if include(rel):
                files.append((rel, full))
files.sort()

manifest = json.load(open(os.path.join(ROOT, "manifest.json"), encoding="utf-8"))
app_files = [(r, f) for r, f in files if r.startswith("project/app/")]
manifest["exportedAt"] = int(time.time() * 1000)
manifest["fileCount"] = len(files)
manifest["uncompressedSize"] = sum(os.path.getsize(f) for _, f in files)
for info in manifest["moduleInfos"]:
    if info["name"] == "app":
        info["files"] = len(app_files)
        info["size"] = sum(os.path.getsize(f) for _, f in app_files)

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("manifest.json", json.dumps(manifest, indent=2) + "\n")
    for rel, full in files:
        z.write(full, rel)
print("geschrieben:", OUT, "(%d Dateien)" % (len(files) + 1))
