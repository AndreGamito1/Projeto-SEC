#!/bin/bash

# Configurable terminal program (change this to your preferred terminal)
TERMINAL="gnome-terminal"
# TERMINAL="xterm"      # Uncomment for xterm
# TERMINAL="konsole"    # Uncomment for KDE's konsole
# TERMINAL="terminator" # Uncomment for terminator

# Default parameters
HTTP_PORT=8080
NUM_CLIENTS=2
REST_API_ONLY=false

# Process command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --http-port|-p)
      HTTP_PORT="$2"
      shift 2
      ;;
    --num-clients|-n)
      NUM_CLIENTS="$2"
      shift 2
      ;;
    --rest-api-only|-r)
      REST_API_ONLY=true
      shift
      ;;
    --terminal|-t)
      TERMINAL="$2"
      shift 2
      ;;
    *)
      echo "Unknown parameter: $1"
      exit 1
      ;;
  esac
done

# Calculate actual HTTP port
ACTUAL_HTTP_PORT=$((HTTP_PORT + 1))

echo "Starting Byzantine Blockchain System..."
echo "REST API will be available at: http://localhost:$ACTUAL_HTTP_PORT/blockchain/"

# Clean and create key folders
rm -rf shared/priv_keys shared/pub_keys
mkdir -p shared/priv_keys shared/pub_keys

# Function to start a process in a new terminal
start_process() {
  local title="$1"
  local command="$2"
  
  $TERMINAL -- bash -c "echo 'Starting $title...'; $command; bash" &
  sleep 1
}

# 1. Start Leader
echo "Starting Leader process..."
start_process "Leader" "mvn exec:java -Dexec.mainClass=com.example.Leader"

# 2. Start Members
for i in {1..4}; do
  echo "Starting Member $i process..."
  start_process "Member $i" "mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member$i"
done

# 3. Start ClientLibrary with REST API
echo "Starting ClientLibrary REST API on port $HTTP_PORT..."
start_process "ClientLibrary" "mvn exec:java -Dexec.mainClass=com.example.ClientLibrary -Dexec.args=$HTTP_PORT"

# 4. Start Clients (if not disabled)
if [ "$REST_API_ONLY" = false ]; then
  echo "Starting $NUM_CLIENTS blockchain client(s)..."
  for i in $(seq 1 $NUM_CLIENTS); do
    client_id="Client$i"
    echo "  - Launching client: $client_id"
    start_process "$client_id" "mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=$client_id"
  done
fi

echo "Blockchain system startup complete!"
echo "REST API is available at: http://localhost:$ACTUAL_HTTP_PORT/blockchain/"

# Helper function for launching more clients
start_blockchain_client() {
  local client_id=${1:-Client$(shuf -i 100-999 -n 1)}
  echo "Launching client: $client_id"
  $TERMINAL -- bash -c "echo 'Starting Blockchain Client: $client_id'; mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=$client_id; bash" &
}

# Export function for bash session
export -f start_blockchain_client
export TERMINAL

echo ""
echo "To launch additional clients, run:"
echo "start_blockchain_client Alice"