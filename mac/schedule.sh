#!/bin/bash
# Расписание бодрствования Mac для StarRemote.
# Mac сам просыпается в начале окна и не засыпает до его конца (гаснет только экран).
#
# Установка (16:00–02:30):
#   curl -fsSL https://raw.githubusercontent.com/jankanat/star-remote/main/mac/schedule.sh | bash -s 16:00 02:30
# Отключить:
#   curl -fsSL https://raw.githubusercontent.com/jankanat/star-remote/main/mac/schedule.sh | bash -s off
set -e

LABEL="kz.alnazar.starremote.awake"
DIR="$HOME/.starremote"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"

if [ "$1" = "off" ]; then
  launchctl unload -w "$PLIST" >/dev/null 2>&1 || true
  rm -f "$PLIST" "$DIR/awake.sh"
  echo "Нужен пароль администратора, чтобы снять ежедневное пробуждение:"
  sudo pmset repeat cancel
  echo "Расписание отключено. Mac снова засыпает как обычно."
  exit 0
fi

FROM="${1:-16:00}"
TO="${2:-02:30}"
case "$FROM$TO" in *[!0-9:]*|'') echo "Время пиши так: 16:00 02:30"; exit 1;; esac
FH="${FROM%%:*}"; FM="${FROM##*:}"

mkdir -p "$DIR" "$HOME/Library/LaunchAgents"

# awake.sh: если сейчас внутри окна, держит Mac бодрым до конца окна
cat > "$DIR/awake.sh" <<'EOF'
#!/bin/bash
FROM="$1"; TO="$2"
sec() { local h="${1%%:*}" m="${1##*:}"; echo $(( 10#$h * 3600 + 10#$m * 60 )); }
NOW=$(( 10#$(date +%H) * 3600 + 10#$(date +%M) * 60 + 10#$(date +%S) ))
F=$(sec "$FROM"); T=$(sec "$TO")
DUR=0
if [ "$T" -le "$F" ]; then            # окно через полночь
  if [ "$NOW" -ge "$F" ]; then DUR=$(( 86400 - NOW + T ))
  elif [ "$NOW" -lt "$T" ]; then DUR=$(( T - NOW )); fi
else
  if [ "$NOW" -ge "$F" ] && [ "$NOW" -lt "$T" ]; then DUR=$(( T - NOW )); fi
fi
LOG="$HOME/.starremote/agent.log"
if [ "$DUR" -le 0 ]; then
  echo "$(date '+%Y-%m-%d %H:%M:%S') расписание: вне окна $FROM–$TO" >> "$LOG"
  exit 0
fi
echo "$(date '+%Y-%m-%d %H:%M:%S') расписание: не сплю ещё $((DUR / 60)) мин" >> "$LOG"
exec /usr/bin/caffeinate -i -t "$DUR"
EOF
chmod 755 "$DIR/awake.sh"

cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>$DIR/awake.sh</string>
    <string>$FROM</string>
    <string>$TO</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>StartCalendarInterval</key>
  <dict>
    <key>Hour</key><integer>$((10#$FH))</integer>
    <key>Minute</key><integer>$((10#$FM))</integer>
  </dict>
</dict>
</plist>
EOF

launchctl unload "$PLIST" >/dev/null 2>&1 || true
launchctl load -w "$PLIST"

echo
echo "Нужен пароль администратора, чтобы Mac сам просыпался каждый день в $FROM:"
sudo pmset repeat wakeorpoweron MTWRFSU "$FROM:00"

echo
echo "Готово: Mac просыпается в $FROM и не засыпает до $TO (экран гаснет как обычно)."
pmset -g sched | sed -n '1,3p'
