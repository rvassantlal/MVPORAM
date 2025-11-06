#!/bin/bash

if [ "$#" -ne 3 ]; then
    echo "Usage: $(basename "$0") <number of workers> <controller ip> <controller port>"
    echo "Example: $(basename "$0") 5 127.0.0.1 12000"
    exit 1
fi

N=$1
CONTROLLER_IP=$2
CONTROLLER_PORT=$3

BASE_DIR=$(pwd)

BUILD_PATH="$BASE_DIR/build/local"
LOG_DIR="$BASE_DIR/logs"

# Create log directory if it doesn't exist
if [ ! -d "$LOG_DIR" ]; then
    mkdir -p "$LOG_DIR"
fi

echo "Log directory: $LOG_DIR"

echo "Launching $N workers..."

cd "$BUILD_PATH"

LAST=$((N - 1))

for i in $(seq 0 $LAST); do
    WORKER_DIR="$BUILD_PATH/worker$i"
    cd "worker$i"
    echo "Starting worker $i in $WORKER_DIR..."
    ./smartrun.sh worker.WorkerStartup $CONTROLLER_IP $CONTROLLER_PORT > "$LOG_DIR/worker$i.log" 2>&1 &
    cd ..
done

echo "All workers launched."