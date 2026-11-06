#!/usr/bin/env bash
# Compila o núcleo do mod (pacote com.ultraperformance.core, que não depende do Minecraft)
# com Java 8 e roda o autoteste. Precisa só de um JDK (8 ou mais novo) instalado.
# Uso: bash dev/run-selftest.sh
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d "${TMPDIR:-/tmp}/ultraperformance-selftest.XXXXXX")"
trap 'rm -rf "$OUT"' EXIT
RELEASE=()
if javac --help 2>&1 | grep -q -- '--release'; then
  RELEASE=(--release 8)
fi
javac "${RELEASE[@]}" -encoding UTF-8 -Xlint:all -d "$OUT" \
  src/main/java/com/ultraperformance/core/*.java dev/selftest/CoreSelfTest.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "$OUT" CoreSelfTest
