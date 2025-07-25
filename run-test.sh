#!/bin/bash

START_TIME=$(date +%s)


###############################################################################
# First, check if java is available and it's java 21:

if [ -n "$JAVA_HOME" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="java"
fi

if ! command -v "$JAVA_BIN" &> /dev/null; then
  echo "ERR: Java is not installed or not in PATH (or JAVA_HOME/bin)." >&2
  exit 1
fi

# Check if java version is 21
JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | awk -F[\".] '/version/ {print $2}')
if [ "$JAVA_VERSION" != "21" ]; then
  echo "ERR: Java 21 is required. Found Java version: $("$JAVA_BIN" -version 2>&1 | head -n 1). Set the JAVA_HOME to Java 21 maybe? Currently it's '$JAVA_HOME'" >&2

  exit 1
fi



################################################################################
# Build the project:

./gradlew installDist || exit 1
./gradlew --stop # just so it's not dangling around

################################################################################
# Now run the actual test:

echo "----------------------------------"
echo "Starting the test cycle . . ."

BIN_PATH="./build/install/ignite-missing-entry-demo/bin/ignite-missing-entry-demo"

check_if_running() {
  if ! kill -0 "$1" 2>/dev/null; then
    echo "ERR: Process $1 is expected to be running at this point, but it's not."
    exit 1
  fi
}

stop_and_check() {
  local pid=$1

  kill -9 "$pid"
  sleep 1
  if kill -0 "$pid" 2>/dev/null; then
    echo "ERR: Process $pid is still running after kill -9."
    exit 1
  else
    echo "Process $pid has been stopped successfully."
  fi
}

while true; do
  date

  # First node - it's just a data node waiting for cache to be created
  "$BIN_PATH" > build/1.log 2>&1 &
  PID1=$!
  echo "Started 1st node with PID $PID1"

  sleep 5
  check_if_running "$PID1"

  # Second node - this one listens for cache creation, subscribes, and actually
  # checks for missing entries:
  "$BIN_PATH" > build/2.log 2>&1 &
  PID2=$!
  echo "Started 2nd node with PID $PID2"

  sleep 5
  check_if_running "$PID2"

  # Third node - this one creates the cache and puts some entries into it:
  "$BIN_PATH" > build/3.log 2>&1 &
  PID3=$!
  echo "Started 3rd node with PID $PID3"

  sleep 5
  check_if_running "$PID3"

  wait $PID2
  EXIT_CODE=$?
  echo "2nd node (listener) exited with code $EXIT_CODE, stopping other nodes..."

  stop_and_check "$PID1"
  stop_and_check "$PID3"

  if [ $EXIT_CODE -eq 32 ]; then
    echo "!!! Yay! Expected error has happened, check logs (build/*.log) for details. !!!"
    echo "!!! Yay! Expected error has happened, check logs (build/*.log) for details. !!!" >> build/2.log
    exit $EXIT_CODE
  fi

  if [ $EXIT_CODE -ne 0 ]; then
    echo "ERR: Unexpected error has happened, check logs (build/*.log) for details."
    exit $EXIT_CODE
  fi

  echo "Listener node exited successfully, restarting the test..."

  NOW=$(date +%s)
  ELAPSED=$((NOW - START_TIME))
  printf -v DURATION '%02d:%02d:%02d' $((ELAPSED/3600)) $((ELAPSED%3600/60)) $((ELAPSED%60))
  echo "Elapsed time: $DURATION"

  echo "---------------------------------"
done

