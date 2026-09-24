#!/bin/zsh
set -euo pipefail

: "${TRADE_REST_JAR:?TRADE_REST_JAR must point to the executable trade-rest JAR}"
: "${TRADE_REST_ENV_FILE:?TRADE_REST_ENV_FILE must point to the external credentials file}"

if [[ ! -r "$TRADE_REST_ENV_FILE" ]]; then
  print -u2 "The trade-rest credentials file is not readable: $TRADE_REST_ENV_FILE"
  exit 78
fi

set -a
source "$TRADE_REST_ENV_FILE"
set +a

JAVA_HOME="$(/usr/libexec/java_home -v 25)"
exec "$JAVA_HOME/bin/java" -jar "$TRADE_REST_JAR"
