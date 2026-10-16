#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/mylauncher-core-tests.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM

find "$ROOT/launcher/src/main/java" "$ROOT/launcher/tests" -type f -name '*.java' -print > "$BUILD_DIR/sources.txt"
if [ ! -s "$BUILD_DIR/sources.txt" ]; then
  echo "No launcher core Java sources or tests were found." >&2
  exit 1
fi

javac -Xlint:all -Werror -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
java -ea -cp "$BUILD_DIR/classes" com.mf.mylauncher.core.CoreTests
