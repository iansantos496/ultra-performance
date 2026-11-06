#!/usr/bin/env python3
"""Confere contra o Yarn 1.16.5 (clone em ref-yarn) se as classes importadas e os alvos dos mixins existem.
Uso: python3 -I dev/verify_names.py <caminho do projeto>   (precisa de tools/yarnq.py do scratchpad)"""
import re, sys, os
sys.path.insert(0, "/tmp/claude-0/-home-claude/218f6585-6624-55b7-bf75-14146175ac00/scratchpad/tools")
sys.argv = [sys.argv[0], "cls", "x"] + sys.argv[1:]
import io, contextlib
with contextlib.redirect_stdout(io.StringIO()):
    import yarnq
root = sys.argv[3]
bad = 0
def members(cls):
    c = yarnq.CLASSES[yarnq.NAMED2INTER[cls]]
    return c
for dp, _, fs in os.walk(os.path.join(root, "src/main/java")):
    for f in fs:
        if not f.endswith(".java"): continue
        src = open(os.path.join(dp, f), encoding="utf-8").read()
        for m in re.finditer(r"^import (net\.minecraft\.[\w.]+|com\.mojang\.[\w.]+);", src, re.M):
            n = m.group(1).replace(".", "/")
            if n not in yarnq.NAMED2INTER:
                # classe interna? tenta com $
                print("CLASSE NAO ENCONTRADA:", f, n); bad += 1
        tm = re.search(r"@Mixin\((\w+)\.class\)", src)
        if tm:
            simple = tm.group(1)
            full = [n for n in yarnq.NAMED2INTER if n.endswith("/" + simple)]
            print(f, "->", full)
            for im in re.finditer(r'method = "([^"]+)"|method = "([^"]+)"\s*\+\s*"([^"]+)"', src):
                pass
print("problemas:", bad)
