#!/usr/bin/env bash
# Runs sbt with the given arguments; retries after a delay when it failed to download something.
#
# Maven Central answers bursts of requests from shared CI egress IPs with HTTP 429, without a
# Retry-After header, and the downloader in sbt 1.13.1 (lm-coursier 2.1.13) has no setting for
# rate limits, so the wait happens here. Only a failure whose output shows a download error is
# retried: a compile error or a failing test fails at once.
#
# SBT_RETRY_DELAYS: the seconds to wait before each retry (default "120 300 600": three retries).
set -uo pipefail

PATTERN='HTTP 429|could not retrieve|Error downloading|shasum mismatch'
PATTERN+='|CantDownloadModule|download error'
read -r -a delays <<< "${SBT_RETRY_DELAYS:-120 300 600}"

log=$(mktemp)
trap 'rm -f "$log"' EXIT

attempt=0
while true; do
  sbt "$@" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  if [ "$status" -eq 0 ]; then exit 0; fi
  if [ "$attempt" -ge "${#delays[@]}" ] || ! grep -Eq "$PATTERN" "$log"; then exit "$status"; fi
  delay=${delays[$attempt]}
  attempt=$((attempt + 1))
  echo "sbt-retry: download failure, retrying in ${delay}s (retry $attempt of ${#delays[@]})"
  sleep "$delay"
done
