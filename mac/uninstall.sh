#!/bin/bash
# Удаление службы Звёздного пульта с Mac.
LABEL="kz.alnazar.starremote"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
launchctl unload -w "$PLIST" >/dev/null 2>&1
rm -f "$PLIST"
rm -rf "$HOME/.starremote"
echo "Служба удалена."
