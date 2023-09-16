#!/usr/bin/env bash

STRATEGY_BLACK=random
STRATEGY_WHITE=prioritiseCapture
BOARD_SIZE=7
UP_TO=10
VERSION=0.7.3

SAVE_DIR=results
SERVER=localhost
PORT=6030

while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    --black) STRATEGY_BLACK="$2";;
    --white) STRATEGY_WHITE="$2";;
    --size) BOARD_SIZE="$2";;
    --up-to) UP_TO="$2";;
    --version) VERSION="$2";;
    *) echo "uh, what?"; exit 1 ;;
  esac
  shift 2
done

mkdir -p "${SAVE_DIR}"
unzip -oq "./target/universal/go-3d-${VERSION}.zip"

COMBINATION="${STRATEGY_BLACK}:${STRATEGY_WHITE}:${BOARD_SIZE}"
OUT_FILE="${SAVE_DIR}/${COMBINATION}.csv"
touch "${OUT_FILE}"
while [ "$(wc -l "${OUT_FILE}" | cut -d ' ' -f 1)" -lt "${UP_TO}" ]; do
  echo "${COMBINATION} - $(wc -l "${OUT_FILE}" | cut -d ' ' -f 1)/${UP_TO}"
  "./go-3d-${VERSION}/bin/bot-client" --server "${SERVER}" --port "${PORT}" \
          --size "${BOARD_SIZE}" --color b \
          --strategy "${STRATEGY_BLACK}" | \
          grep 'Map(' | tr -d '[:alpha:][:blank:]()>@-' \
          >> "${OUT_FILE}" || exit 1 &
  sleep 2
  time "./go-3d-${VERSION}/bin/bot-client" --server "${SERVER}" --port "${PORT}" \
          --game-id "$(curl -s "http://${SERVER}:${PORT}/openGames" | jq -r .ids[0])" \
          --color w \
          --strategy "${STRATEGY_WHITE}" || exit 1
  sleep 1
done
