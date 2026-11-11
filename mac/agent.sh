#!/bin/bash
# Звёздный пульт: служба на Mac.
# launchd принимает соединение на порту и запускает этот скрипт для каждого запроса
# (inetdCompatibility): сокет приходит на stdin/stdout.
# Совместимо с bash 3.2, который стоит в macOS.

DIR="$HOME/.starremote"
LOG="$DIR/agent.log"
TOKEN_FILE="$DIR/token"
CONF="$DIR/config"
VERSION="2"

TORR_PORT=8090
[ -f "$CONF" ] && . "$CONF"

log() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "$LOG"; }

json_escape() {
  # \ → \\, " → \", перевод строки → \n, табы и \r убираем
  printf '%s' "$1" | tr -d '\r' | tr '\t' ' ' \
    | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | awk 'BEGIN{ORS=""} NR>1{print "\\n"} {print}'
}

respond() {
  # $1 код, $2 текст статуса, $3 json
  local body="$3"
  local len
  len=$(printf '%s' "$body" | wc -c | tr -d ' ')
  printf 'HTTP/1.1 %s %s\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s' \
    "$1" "$2" "$len" "$body"
  exit 0
}

urldecode() {
  local s="${1//+/ }"
  printf '%b' "${s//%/\\x}"
}

param() {
  # param имя строка_запроса
  local IFS='&' kv
  for kv in $2; do
    case "$kv" in "$1="*) urldecode "${kv#*=}"; return;; esac
  done
}

# --- читаем запрос ---
read -r -t 5 REQ || exit 0
REQ="${REQ%$'\r'}"
METHOD="${REQ%% *}"
TARGET="${REQ#* }"; TARGET="${TARGET%% *}"
PATH_ONLY="${TARGET%%\?*}"
QUERY=""
case "$TARGET" in *\?*) QUERY="${TARGET#*\?}";; esac

HDR_TOKEN=""
while read -r -t 5 line; do
  line="${line%$'\r'}"
  [ -z "$line" ] && break
  case "$line" in
    [Xx]-[Tt]oken:*) HDR_TOKEN="${line#*:}"; HDR_TOKEN="${HDR_TOKEN# }";;
  esac
done

export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:$PATH"

# /ping отвечает без токена: по нему телефон понимает, что Mac проснулся
if [ "$PATH_ONLY" = "/ping" ]; then
  respond 200 OK "{\"ok\":true,\"app\":\"starremote\",\"v\":$VERSION,\"host\":\"$(json_escape "$(scutil --get LocalHostName 2>/dev/null)")\"}"
fi

TOKEN="$(cat "$TOKEN_FILE" 2>/dev/null)"
if [ -z "$TOKEN" ] || [ "$HDR_TOKEN" != "$TOKEN" ]; then
  log "401 $METHOD $PATH_ONLY"
  respond 401 Unauthorized '{"ok":false,"error":"bad_token"}'
fi

is_caffeinated() { pgrep -x caffeinate >/dev/null 2>&1 && echo true || echo false; }
GUARD_PID="$DIR/guard.pid"
stop_guard() {
  if [ -f "$GUARD_PID" ]; then
    local p; p="$(cat "$GUARD_PID")"
    [ -n "$p" ] && pkill -P "$p" >/dev/null 2>&1; [ -n "$p" ] && kill "$p" >/dev/null 2>&1
    rm -f "$GUARD_PID"
  fi
}
has_guard() { [ -f "$GUARD_PID" ] && kill -0 "$(cat "$GUARD_PID")" 2>/dev/null && echo true || echo false; }
is_torr_proc() { pgrep -f -i "torrserver-darwin" >/dev/null 2>&1 && echo true || echo false; }
is_torr_up() { { nc -z -G 1 127.0.0.1 "$TORR_PORT" || nc -z -G 1 ::1 "$TORR_PORT"; } >/dev/null 2>&1 && echo true || echo false; }

case "$METHOD $PATH_ONLY" in
  "GET /status")
    respond 200 OK "{\"ok\":true,\"caffeinate\":$(is_caffeinated),\"torrProc\":$(is_torr_proc),\"torrserve\":$(is_torr_up),\"guard\":$(has_guard),\"torrPort\":$TORR_PORT,\"uptime\":\"$(json_escape "$(uptime | sed 's/^ *//')")\"}"
    ;;

  "POST /run")
    NAME="$(param name "$QUERY")"
    if [ -z "$NAME" ]; then respond 400 'Bad Request' '{"ok":false,"error":"no_name"}'; fi
    log "run: $NAME"
    START=$(date +%s)
    # не дольше 120 с: perl alarm вместо отсутствующей в macOS команды timeout
    OUT="$(perl -e 'alarm shift; exec @ARGV' 120 /usr/bin/shortcuts run "$NAME" 2>&1)"
    CODE=$?
    DUR=$(( $(date +%s) - START ))
    log "run: $NAME → код $CODE за ${DUR}с ${OUT:0:300}"
    if [ $CODE -eq 0 ]; then OK=true; else OK=false; fi
    respond 200 OK "{\"ok\":$OK,\"code\":$CODE,\"sec\":$DUR,\"output\":\"$(json_escape "${OUT:0:800}")\"}"
    ;;

  "POST /guard")
    # страховка: если телефон не выключит сам, через SECS секунд Mac запустит шорткат выключения
    SECS="$(param secs "$QUERY")"
    NAME="$(param name "$QUERY")"
    case "$SECS" in ''|*[!0-9]*) respond 400 'Bad Request' '{"ok":false,"error":"bad_secs"}';; esac
    [ -z "$NAME" ] && respond 400 'Bad Request' '{"ok":false,"error":"no_name"}'
    stop_guard
    nohup /bin/bash -c 'sleep "$1"; echo "$(date "+%Y-%m-%d %H:%M:%S") страховка: run $2" >> "$3"; /usr/bin/shortcuts run "$2"; rm -f "$4"' \
      guard "$SECS" "$NAME" "$LOG" "$GUARD_PID" >/dev/null 2>&1 &
    echo $! > "$GUARD_PID"
    log "страховка: «$NAME» через ${SECS}с"
    respond 200 OK "{\"ok\":true,\"secs\":$SECS}"
    ;;

  "POST /guard/stop")
    stop_guard
    log "страховка снята"
    respond 200 OK '{"ok":true}'
    ;;

  "POST /sleep")
    log "sleep"
    ( sleep 2; pmset sleepnow >/dev/null 2>&1 ) &
    respond 200 OK '{"ok":true}'
    ;;

  *)
    respond 404 'Not Found' '{"ok":false,"error":"not_found"}'
    ;;
esac
