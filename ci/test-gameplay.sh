#!/usr/bin/env bash
# Plays one bot-vs-bot game against a freshly started server and records the outcome.
#
# Environment:
#   INSTALL_DIR     directory containing bin/go-server and bin/bot-client (required)
#   BOARD_SIZE      board size (default 7)
#   STRATEGY_BLACK  strategy for black (default random)
#   STRATEGY_WHITE  strategy for white (default random)
#
# Writes server.log, white.log and results.<black>.<white>.csv to the current directory.
set -euo pipefail

: "${INSTALL_DIR:?INSTALL_DIR must be set}"
BOARD_SIZE=${BOARD_SIZE:-7}
STRATEGY_BLACK=${STRATEGY_BLACK:-random}
STRATEGY_WHITE=${STRATEGY_WHITE:-random}

mkdir -p saves
"${INSTALL_DIR}/bin/go-server" --port 6030 --save-dir saves > server.log 2>&1 &
sleep 5

# wait until the server answers: an empty list of open games yields "null"
GAME_ID=""
while [ "$GAME_ID" != "null" ]; do
  GAME_ID=$(curl -s http://localhost:6030/openGames | jq -r '.ids[0]' || true)
  echo "$GAME_ID"
  sleep 1
done
grep -A1 "Ember-Server service bound to address" server.log

echo "${STRATEGY_BLACK} - ${STRATEGY_WHITE}"
"${INSTALL_DIR}/bin/bot-client" --server localhost --port 6030 --size "$BOARD_SIZE" --color b \
  --strategy "$STRATEGY_BLACK" --max-thinking-time-ms 1000 | grep 'Map(' &

# wait until black has opened a game
while [ "$GAME_ID" == "null" ]; do
  GAME_ID=$(curl -s http://localhost:6030/openGames | jq -r '.ids[0]' || true)
  echo "$GAME_ID"
  sleep 1
done

/usr/bin/time -o time.log -f "%e\n%U\n%M\n%P" \
  "${INSTALL_DIR}/bin/bot-client" --server localhost --port 6030 --game-id "$GAME_ID" --color w \
  --strategy "$STRATEGY_WHITE" --max-thinking-time-ms 1000 | tee white.log

grep -c "go3d.server.http4s.DoSet" server.log
grep -c "go3d.server.http4s.DoPass" server.log || true

SCORE_BLACK=$(grep -E -o "[0-9]+" white.log | tail -n 2 | head -n 1)
SCORE_WHITE=$(grep -E -o "[0-9]+" white.log | tail -n 1)
WALL_TIME_WHITE=$(sed '1q;d' time.log)
USER_TIME_WHITE=$(sed '2q;d' time.log)
MEM_WHITE=$(sed '3q;d' time.log)
CPU_PERCENT_WHITE=$(sed '4q;d' time.log)
CSV_LINE="${STRATEGY_BLACK};${STRATEGY_WHITE};$SCORE_BLACK;$SCORE_WHITE;$WALL_TIME_WHITE"
CSV_LINE="${CSV_LINE};$USER_TIME_WHITE;$MEM_WHITE;$CPU_PERCENT_WHITE"
echo "$CSV_LINE"
echo "$CSV_LINE" >> "results.${STRATEGY_BLACK}.${STRATEGY_WHITE}.csv"
