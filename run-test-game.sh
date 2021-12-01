#!/usr/bin/env bash

STRATEGY_BLACK=random
STRATEGY_WHITE=prioritiseCapture
BOARD_SIZE=7
VERSION=0.6.3

while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    --black) STRATEGY_BLACK="$2";;
    --white) STRATEGY_WHITE="$2";;
    --size) BOARD_SIZE="$2";;
    --version) VERSION="$2";;
    *) echo "uh, what?"; exit 1
  esac
  shift 2
done

mkdir -p results
unzip -oq "./target/universal/go-3d-${VERSION}.zip"

"./go-3d-${VERSION}/bin/bot-client" --server localhost --port 6030 \
        --size "$BOARD_SIZE" --color b \
        --strategy "$STRATEGY_BLACK" | \
        grep 'Map(' | tr -d '[:alpha:][:blank:]()>@-' \
        >> "results/${STRATEGY_BLACK}:${STRATEGY_WHITE}:${BOARD_SIZE}.csv" &
sleep 2
time "./go-3d-${VERSION}/bin/bot-client" --server localhost --port 6030 \
        --game-id $(curl -s http://localhost:6030/openGames | jq -r .ids[0]) \
        --color w \
        --strategy "$STRATEGY_WHITE"
wait
sleep 1
