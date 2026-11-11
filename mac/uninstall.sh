#!/bin/bash
# Удаление службы Звёздного пульта с Mac.
LABEL="kz.alnazar.starremote"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
launchctl unload -w "$PLIST" >/dev/null 2>&1
rm -f "$PLIST"
launchctl unload -w "$HOME/Library/LaunchAgents/kz.alnazar.starremote.awake.plist" >/dev/null 2>&1
rm -f "$HOME/Library/LaunchAgents/kz.alnazar.starremote.awake.plist"
rm -rf "$HOME/.starremote"
echo "Служба удалена. Ежедневное пробуждение снимается отдельно: sudo pmset repeat cancel"
