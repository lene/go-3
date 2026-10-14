#!/usr/bin/env bash
# Load test for the Lambda API (#125): GAMES concurrent games played to the end through the write
# routes, while STATUS_PER_MIN status requests per minute hit /status for DURATION_SECONDS. Uses
# curl only. Prints count, 429s, 5xx and p50/p95/max latency per route, and exits non-zero on any
# 5xx, unexpected status code, unfinished game or missing archive.
#
# Environment:
#   API_URL           base URL of the API (required)
#   GAMES             concurrent games (default 100); 0 for a status-only run, e.g. against a
#                     production stack that has no write routes yet
#   STATUS_PER_MIN    status requests per minute (default 1000)
#   DURATION_SECONDS  length of the status load (default 60)
#   STATUS_PATH       path requested by the status load when there is no game to ask for
#                     (default /status/CANARY, a 404 that still reads DynamoDB)
#
# 429 answers to game requests are retried after a pause and counted; 429 answers to status
# requests are only counted, so the throttling shows up in the summary.
# Client-side times include the network; judge the server-side p95 in CloudWatch.
set -euo pipefail
# curl prints times with a decimal point; awk would read them wrongly in a comma locale
export LC_ALL=C

: "${API_URL:?API_URL must be set}"
API=${API_URL%/}
GAMES=${GAMES:-100}
STATUS_PER_MIN=${STATUS_PER_MIN:-1000}
DURATION_SECONDS=${DURATION_SECONDS:-60}
STATUS_PATH=${STATUS_PATH:-/status/CANARY}
OUT=$(mktemp)
export API OUT STATUS_PATH

# one LABEL URL [TOKEN]: one request, logged as "label code seconds"; the body goes to stdout
one() {
  local label=$1 url=$2 token=${3:-} body code seconds
  body=$(mktemp)
  read -r code seconds < <(curl -s -m 30 -o "$body" -w '%{http_code} %{time_total}\n' \
    ${token:+-H "Authentication: Bearer $token"} "$url" || echo "000 30")
  echo "$label $code $seconds" >> "$OUT"
  # only a successful body is passed on, so a retried request leaves one JSON document
  if [[ "$code" =~ ^[23] ]]; then cat "$body"; rm -f "$body"; return 0; fi
  rm -f "$body"
  return 1
}

# req LABEL URL [TOKEN]: like one, retrying while the API throttles
req() {
  local n=0
  until one "$@"; do
    [[ "$(tail -n1 "$OUT" | cut -d' ' -f2)" == 429 && $((++n)) -lt 30 ]] || return 1
    sleep 0.3
  done
}

# shellcheck disable=SC2329 # run through bash -c by xargs
play_game() {
  local black=(1/1/1 1/1/3 1/3/1 1/3/3) white=(3/3/3 3/3/1 3/1/3 3/1/1)
  local id btoken wtoken i
  id=$(req new "$API/new/3" | jq -r .id)
  btoken=$(req register "$API/register/$id/%40" | jq -r .authToken)
  wtoken=$(req register "$API/register/$id/O" | jq -r .authToken)
  for i in 0 1 2 3; do
    req set "$API/set/$id/${black[i]}" "$btoken" > /dev/null
    req set "$API/set/$id/${white[i]}" "$wtoken" > /dev/null
  done
  req pass "$API/pass/$id" "$btoken" > /dev/null
  req pass "$API/pass/$id" "$wtoken" > /dev/null
  echo "$id" >> "$OUT.games"
}

# shellcheck disable=SC2329 # run through bash -c by xargs
status_one() { one status "$API${STATUS_PATH}" > /dev/null || true; }

status_load() {
  local requests=$((STATUS_PER_MIN * DURATION_SECONDS / 60))
  local pause
  pause=$(awk -v r="$STATUS_PER_MIN" 'BEGIN { printf "%.4f", 60 / r }')
  for _ in $(seq "$requests"); do echo x; sleep "$pause"; done \
    | xargs -P 50 -I{} bash -c status_one
}

export -f one req play_game status_one

if (( GAMES > 0 )); then
  # a finished game gives the status load something to read
  req new "$API/new/3" | jq -r .id > "$OUT.statusgame"
  STATUS_PATH="/status/$(cat "$OUT.statusgame")"
  export STATUS_PATH
fi

echo "load test on $API: $GAMES games, $STATUS_PER_MIN status/min for ${DURATION_SECONDS}s"
start=$SECONDS
status_load &
status_pid=$!
failed=0
if (( GAMES > 0 )); then
  seq "$GAMES" | xargs -P "$GAMES" -I{} bash -c 'play_game {}' || failed=1
fi
wait "$status_pid"
echo "load phase took $((SECONDS - start))s"

# every game must be over and archived
verified=0
if (( GAMES > 0 )); then
  touch "$OUT.games"
  while read -r id; do
    if [[ "$(one verify "$API/status/$id" | jq -r .over)" == true ]] \
      && [[ "$(curl -s -o /dev/null -w '%{http_code}' "$API/archived/$id")" =~ ^3 ]]; then
      verified=$((verified + 1))
    fi
  done < "$OUT.games"
  echo "games finished and archived: $verified of $GAMES"
  (( verified == GAMES )) || failed=1
fi

percentile() { sort -n | awk -v p="$1" '{ a[NR] = $1 } END { if (NR) printf "%.0f", a[int((NR - 1) * p) + 1] * 1000 }'; }

printf '%-9s %6s %5s %5s %7s %7s %7s\n' route count 429 5xx p50ms p95ms maxms
for label in new register set pass status verify; do
  rows=$(grep -c "^$label " "$OUT" || true)
  (( rows > 0 )) || continue
  times=$(grep "^$label " "$OUT" | cut -d' ' -f3)
  printf '%-9s %6d %5d %5d %7s %7s %7s\n' "$label" "$rows" \
    "$(grep -c "^$label 429 " "$OUT" || true)" "$(grep -c "^$label 5" "$OUT" || true)" \
    "$(percentile 0.5 <<< "$times")" "$(percentile 0.95 <<< "$times")" \
    "$(percentile 1 <<< "$times")"
done

# Anything but success and 429 fails: 5xx, transport errors (000), and a 404 unless the status
# load asks for a game that does not exist on purpose (GAMES=0)
ok_codes='^[a-z]+ (2|3|429 )'
(( GAMES == 0 )) && ok_codes='^[a-z]+ (2|3|429 |404 )'
if grep -v -E "$ok_codes" "$OUT" > "$OUT.bad"; then
  echo "unexpected answers (route code seconds):"
  cut -d' ' -f1,2 "$OUT.bad" | sort | uniq -c | sort -rn | head
  failed=1
fi
exit "$failed"
