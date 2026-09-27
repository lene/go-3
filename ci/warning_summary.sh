#!/usr/bin/env bash
# Prints compiler warnings from an sbt log grouped by kind and by file.
set -euo pipefail
LOG=${1:?usage: warning_summary.sh <sbt log>}

echo "Warnings by kind:"
{
  grep -o '\[wartremover:[A-Za-z]*\]' "$LOG" || true
  grep -oE '^\[warn\] *\| *(unused [a-z ]+)$' "$LOG" | sed -E 's/^\[warn\] *\| *//' || true
} | sort | uniq -c | sort -rn

echo "Warnings by file:"
grep -oE '^\[warn\] -- .*src/(main|test)/scala/[^:]+' "$LOG" \
  | sed -E 's#.*src/(main|test)/scala/#\1: #' | sort | uniq -c | sort -rn | head -30 || true
