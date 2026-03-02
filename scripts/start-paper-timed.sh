#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.paper-local"
PLUGIN_SRC_JAR="${PLUGIN_JAR:-$ROOT_DIR/build/libs/ClassificheExp-0.1.0-SNAPSHOT.jar}"
DEMO_PLUGIN_SRC_JAR="${DEMO_PLUGIN_JAR:-$ROOT_DIR/examples/consumer-demo/build/libs/classificheexp-consumer-demo-0.1.0-SNAPSHOT.jar}"
JAVA_BIN="${JAVA_BIN:-java}"
XMS="${XMS:-512M}"
XMX="${XMX:-1024M}"
STARTUP_TIMEOUT_SEC="${STARTUP_TIMEOUT_SEC:-120}"
POST_READY_RUN_SEC="${POST_READY_RUN_SEC:-15}"
BUILD_PLUGINS="${BUILD_PLUGINS:-1}"
RUN_SMOKE_COMMAND="${RUN_SMOKE_COMMAND:-1}"
SMOKE_COMMAND="${SMOKE_COMMAND:-classificaexpdemo smoke}"
SMOKE_TIMEOUT_SEC="${SMOKE_TIMEOUT_SEC:-45}"
SMOKE_SUCCESS_PATTERN="${SMOKE_SUCCESS_PATTERN:-DEMO_SMOKE_OK score=15}"
SMOKE_FAILURE_PATTERN="${SMOKE_FAILURE_PATTERN:-DEMO_SMOKE_KO}"

"$ROOT_DIR/scripts/setup-paper-latest.sh"

if [[ "$BUILD_PLUGINS" == "1" ]]; then
  echo "Building ClassificheExp..."
  (cd "$ROOT_DIR" && ./gradlew shadowJar --no-daemon)
  echo "Building consumer demo plugin..."
  (cd "$ROOT_DIR" && ./gradlew -p examples/consumer-demo shadowJar --no-daemon)
fi

if [[ ! -f "$PLUGIN_SRC_JAR" ]]; then
  echo "Plugin jar not found at $PLUGIN_SRC_JAR"
  echo "Build it first with: ./gradlew shadowJar"
  exit 1
fi

if [[ ! -f "$DEMO_PLUGIN_SRC_JAR" ]]; then
  echo "Demo plugin jar not found at $DEMO_PLUGIN_SRC_JAR"
  echo "Build it first with: ./gradlew -p examples/consumer-demo shadowJar"
  exit 1
fi

cp "$PLUGIN_SRC_JAR" "$RUN_DIR/plugins/ClassificheExp.jar"
cp "$DEMO_PLUGIN_SRC_JAR" "$RUN_DIR/plugins/ClassificheExpConsumerDemo.jar"

FIFO="$RUN_DIR/server.stdin"
LOG_FILE="$RUN_DIR/server-console.log"
rm -f "$FIFO"
mkfifo "$FIFO"
: > "$LOG_FILE"
exec 3<>"$FIFO"

echo "Starting Paper (timeout ${STARTUP_TIMEOUT_SEC}s, post-ready ${POST_READY_RUN_SEC}s)..."
(
  cd "$RUN_DIR"
  "$JAVA_BIN" -Xms"$XMS" -Xmx"$XMX" -jar paper-latest.jar nogui <&3 2>&1 | tee -a "$LOG_FILE"
) &
SERVER_WRAPPER_PID=$!

start_ts=$(date +%s)
ready=0
while true; do
  if grep -qE "Done \([0-9.]+s\)!|For help, type \"help\"" "$LOG_FILE"; then
    ready=1
    break
  fi
  if ! kill -0 "$SERVER_WRAPPER_PID" 2>/dev/null; then
    break
  fi
  now_ts=$(date +%s)
  if (( now_ts - start_ts >= STARTUP_TIMEOUT_SEC )); then
    echo "Startup timeout reached (${STARTUP_TIMEOUT_SEC}s)."
    break
  fi
  sleep 1
done

if (( ready == 1 )); then
  ready_ts=$(date +%s)
  echo "Server ready in $((ready_ts - start_ts))s"
  if [[ "$RUN_SMOKE_COMMAND" == "1" ]]; then
    echo "Running smoke command: /$SMOKE_COMMAND"
    echo "$SMOKE_COMMAND" >&3 || true
    smoke_start_ts=$(date +%s)
    smoke_ok=0
    smoke_failed=0
    while true; do
      if grep -q "$SMOKE_SUCCESS_PATTERN" "$LOG_FILE"; then
        smoke_ok=1
        break
      fi
      if grep -q "$SMOKE_FAILURE_PATTERN" "$LOG_FILE"; then
        smoke_failed=1
        break
      fi
      now_ts=$(date +%s)
      if (( now_ts - smoke_start_ts >= SMOKE_TIMEOUT_SEC )); then
        break
      fi
      sleep 1
    done

    if (( smoke_ok == 1 )); then
      echo "Smoke test passed: pattern '$SMOKE_SUCCESS_PATTERN' found."
    elif (( smoke_failed == 1 )); then
      echo "Smoke test failed: pattern '$SMOKE_FAILURE_PATTERN' found."
      POST_READY_RUN_SEC=0
      stop_status=1
    else
      echo "Smoke test timeout after ${SMOKE_TIMEOUT_SEC}s: success pattern not found."
      POST_READY_RUN_SEC=0
      stop_status=1
    fi
  fi
  sleep "$POST_READY_RUN_SEC"
fi

if kill -0 "$SERVER_WRAPPER_PID" 2>/dev/null; then
  echo "stop" >&3 || true
  wait "$SERVER_WRAPPER_PID" || true
fi

exec 3>&-
rm -f "$FIFO"

echo "Run completed. Log: $LOG_FILE"

if [[ "${stop_status:-0}" == "1" ]]; then
  exit 1
fi
