#!/bin/bash

START_TIME=$(date +%s)

###############################################################################
# First, check if java is available and it's java 8:

if [ -n "$JAVA_HOME" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="java"
fi

if ! command -v "$JAVA_BIN" &>/dev/null; then
  echo "ERR: Java is not installed or not in PATH (or JAVA_HOME/bin)." >&2
  exit 1
fi

echo "Getting Java version from: $JAVA_BIN"
"$JAVA_BIN" -version

JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | awk -F '"' '/version/ {print $2}')
if [[ "$JAVA_VERSION" != 1.8* ]]; then
  echo "ERR: Java 8 is required. Detected version: $JAVA_VERSION" >&2
  exit 1
fi

echo "Java version is OK: $JAVA_VERSION"

################################################################################
# Build the project:

./gradlew --version
./gradlew installDist || exit 1
echo "Project built successfully, stopping the gradle daemon now . . ."
./gradlew --stop # just so it's not dangling around

################################################################################
# Now run the actual test:

BIN_PATH="./build/install/ignite-missing-entry-demo/bin/ignite-missing-entry-demo"

is_finished_early() {
  if ! kill -0 "$1" 2>/dev/null; then
    echo "ERR: Process $1 is expected to be running at this point, but it's not."
    return 0
  fi

  return 1
}

stop_and_check() {
  local pid=$1

  kill -9 "$pid" 2>/dev/null;
  sleep 1
  if kill -0 "$pid" 2>/dev/null; then
    echo "ERR: Process $pid is still running after kill -9."
    exit 1
  else
    echo "Process $pid has been stopped successfully."
  fi
}

remove_if_exist() {
  if [[ -f "$1" ]]; then
    rm "$1" || return 1
  fi
  return 0
}

unexpected_error=false
consec_broken_cycles=0

echo "Starting the test cycle . . ."

log() {
  NOW=$(date +%s)
  ELAPSED=$((NOW - START_TIME))
  printf -v DURATION '%02d:%02d:%02d' $((ELAPSED / 3600)) $((ELAPSED % 3600 / 60)) $((ELAPSED % 60))

  echo "$(date +%T) - elapsed $DURATION - $1"
}

while true; do

  if [ "$unexpected_error" = true ]; then
    consec_broken_cycles=$((consec_broken_cycles + 1))
    unexpected_error=false

    NOW=$(date +%s)

    [[ -f build/1.log ]] && cp build/1.log "build/$NOW-1.log"
    [[ -f build/2.log ]] && cp build/2.log "build/$NOW-2.log"
    [[ -f build/3.log ]] && cp build/3.log "build/$NOW-3.log"
    echo "Unexpected error has happened, the logs copied to build/$NOW-*.log, will retry now . . ."
  fi

  remove_if_exist "build/1.log" || exit 1
  remove_if_exist "build/2.log" || exit 1
  remove_if_exist "build/3.log" || exit 1

  [[ ! -z "$PID1" ]] && stop_and_check "$PID1" && unset PID1
  [[ ! -z "$PID2" ]] && stop_and_check "$PID2" && unset PID2
  [[ ! -z "$PID3" ]] && stop_and_check "$PID3" && unset PID3
  
  rm -rf ignite

  if [ $consec_broken_cycles -ge 3 ]; then
    echo "ERR: Too many consecutive broken cycles ($consec_broken_cycles), exiting."
    exit 1
  fi

  [ ! -z "${endMsg}" ] && log "${endMsg}"
  echo "---------------------------------"
  unset endMsg

  date

  # First node - does nothing apart from holding data when it arrives:
  "$BIN_PATH" --set-timeout >build/1.log 2>&1 &
  PID1=$!
  log "Started 1st node with PID $PID1"
  sleep 5
  if is_finished_early "$PID1"; then
    unexpected_error=true
    continue
  fi

  # Second node - this one listens for cache creation, subscribes, and then
  # actually checks for the missing entries:
  "$BIN_PATH" --set-timeout >build/2.log 2>&1 &
  PID2=$!
  log "Started 2nd node with PID $PID2"
  sleep 5
  if is_finished_early "$PID2"; then
    unexpected_error=true
    continue
  fi

  # Third node - this one creates the cache and puts some entries into it:
  "$BIN_PATH" --set-timeout >build/3.log 2>&1 &
  PID3=$!
  log "Started 3rd node with PID $PID3"
  sleep 5
  if is_finished_early "$PID3"; then
    unexpected_error=true
    continue
  fi

  # 2 mins is the timeout set in the java code:
  log "Waiting for the 2nd node to finish, 2 mins max . . ."
  wait $PID2
  EXIT_CODE=$?
  log "2nd node exited with code $EXIT_CODE..."

  if [ $EXIT_CODE -eq 32 ]; then
    msg="!!! Yay! Expected error has happened, check the logs (build/*.log) for details. !!!"
    log "${msg}"
    echo "${msg}" >>build/2.log
    exit $EXIT_CODE
  fi

  if [ $EXIT_CODE -ne 0 ]; then
    endMsg="ERR: Listener node got UNEXPECTED error $EXIT_CODE"
    unexpected_error=true
    continue
  fi

  grep "All putAsync operations completed successfully" build/3.log
  grep "keys, cache size is" build/2.log

  endMsg="Listener node exited successfully, the test will be re-tried now"

  consec_broken_cycles=0
done
