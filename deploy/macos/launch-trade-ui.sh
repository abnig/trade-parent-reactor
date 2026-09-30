#!/bin/zsh
set -euo pipefail

: "${TRADE_UI_SERVER:?TRADE_UI_SERVER must point to serve-production.mjs}"
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"
exec node "$TRADE_UI_SERVER"
