#!/usr/bin/env bash
# Self-test of ci/sbt-retry.sh against a fake sbt: a download failure is retried, a test failure
# is not, and the retries end after SBT_RETRY_DELAYS.
set -euo pipefail

retry="$(cd "$(dirname "$0")" && pwd)/sbt-retry.sh"
dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT

cat > "$dir/sbt" <<'EOF'
#!/usr/bin/env bash
echo call >> "$FAKE_SBT_DIR/calls"
calls=$(wc -l < "$FAKE_SBT_DIR/calls")
case "$FAKE_SBT_MODE" in
  429-once)
    if [ "$calls" -eq 1 ]; then
      echo "retryable HTTP error: https://repo1.maven.org/x.pom (HTTP 429)"; exit 1
    fi
    echo "[success]"; exit 0 ;;
  test-failure) echo "[error] Failed: Total 3, Failed 1"; exit 3 ;;
  429-always) echo "[error] [launcher] could not retrieve sbt 1.13.1"; exit 1 ;;
  # root compiles with a warning, then the engine's dependency download fails
  warn-then-429)
    echo "[warn] -- attempt $calls"
    if [ "$calls" -eq 1 ]; then
      echo "Error downloading com.microsoft.onnxruntime:onnxruntime_gpu:1.32.0" >&2; exit 1
    fi
    exit 0 ;;
esac
EOF
chmod +x "$dir/sbt"

failed=0

# check <mode> <expected exit status> <expected number of sbt calls>
check() {
  : > "$dir/calls"
  local status=0
  FAKE_SBT_DIR="$dir" FAKE_SBT_MODE="$1" SBT_RETRY_DELAYS="0 0 0" PATH="$dir:$PATH" \
    "$retry" test > "$dir/out" 2>&1 || status=$?
  local calls
  calls=$(wc -l < "$dir/calls")
  if [ "$status" -eq "$2" ] && [ "$calls" -eq "$3" ]; then
    echo "ok: $1"
  else
    echo "FAIL: $1 expected status $2 and $3 calls, got status $status and $calls calls"
    failed=1
  fi
}

check 429-once 0 2
if ! grep -q '^\[success\]$' "$dir/out"; then
  echo "FAIL: 429-once expected the sbt output on stdout"
  failed=1
fi
check test-failure 3 1
check 429-always 1 4

# SBT_RETRY_LOG keeps the output of the last attempt only, so that the warnings step counts the
# warnings of one compile; the failure on stderr is still recognised
SBT_RETRY_LOG="$dir/last.log" check warn-then-429 0 2
if [ "$(cat "$dir/last.log" 2>/dev/null)" != "[warn] -- attempt 2" ]; then
  echo "FAIL: warn-then-429 expected only the last attempt in SBT_RETRY_LOG"
  failed=1
fi

exit "$failed"
