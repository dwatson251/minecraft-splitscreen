#!/bin/bash
# Runs a command with a hard memory cap for it and everything it starts (Gradle daemon, game JVMs): a systemd user
# scope with MemoryMax, swap off. Going over kills that group only, never the whole machine (or WSL).
#   scripts/capped.sh <command...>          MCSS_MEM_CAP=3G by default
exec systemd-run --user --scope --quiet -p MemoryMax="${MCSS_MEM_CAP:-3G}" -p MemorySwapMax=0 "$@"
