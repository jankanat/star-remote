#!/bin/bash
# Установка службы Звёздного пульта на Mac.
# Запуск одной командой:
#   curl -fsSL https://raw.githubusercontent.com/jankanat/star-remote/main/mac/install.sh | bash
# или из папки с agent.sh: bash install.sh
# Повторный запуск безопасен: токен сохраняется, служба перезапускается.
set -e

LABEL="kz.alnazar.starremote"
PORT="${PORT:-47823}"
DIR="$HOME/.starremote"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
SRC="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd)"

BASE="${BASE:-https://raw.githubusercontent.com/jankanat/star-remote/main/mac}"

mkdir -p "$DIR" "$HOME/Library/LaunchAgents"
if [ -f "$SRC/agent.sh" ]; then
  cp "$SRC/agent.sh" "$DIR/agent.sh"
else
  # запуск одной командой через curl | bash: берём agent.sh с GitHub
  curl -fsSL "$BASE/agent.sh" -o "$DIR/agent.sh"
fi
chmod 755 "$DIR/agent.sh"

if [ ! -s "$DIR/token" ]; then
  openssl rand -hex 16 > "$DIR/token"
  chmod 600 "$DIR/token"
fi
if [ ! -f "$DIR/config" ]; then
  echo "TORR_PORT=${TORR_PORT:-8090}" > "$DIR/config"
fi

cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>$DIR/agent.sh</string>
  </array>
  <key>inetdCompatibility</key>
  <dict><key>Wait</key><false/></dict>
  <key>AbandonProcessGroup</key><true/>
  <key>Sockets</key>
  <dict>
    <key>Listeners</key>
    <dict>
      <key>SockServiceName</key><string>$PORT</string>
      <key>SockType</key><string>stream</string>
      <key>SockFamily</key><string>IPv4</string>
      <key>Bonjour</key><string>starremote</string>
    </dict>
  </dict>
  <key>StandardErrorPath</key><string>$DIR/agent.err</string>
</dict>
</plist>
EOF

launchctl unload "$PLIST" >/dev/null 2>&1 || true
launchctl load -w "$PLIST"

IP=""
for i in en1 en0 en2; do IP="$(ipconfig getifaddr $i 2>/dev/null)"; [ -n "$IP" ] && break; done

sleep 1
echo
echo "Служба установлена."
echo "Проверка: $(curl -s --max-time 3 http://127.0.0.1:$PORT/ping || echo 'нет ответа')"
echo
echo "Введи в приложении (Настройки → Подключение):"
echo "  IP-адрес:     ${IP:-не найден}"
echo "  Порт службы:  $PORT"
echo "  Ключ доступа: $(cat "$DIR/token")"
echo
echo "Bonjour: dns-sd -B _starremote._tcp  (Ctrl+C, чтобы выйти)"
