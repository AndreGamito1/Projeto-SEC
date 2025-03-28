#!/bin/bash
# Blockchain Client Starter

# Define colors for terminal output
CYAN='\033[0;36m'
YELLOW='\033[0;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# Parse arguments
CLIENT_ID=$1

# Generate a random client ID if none is provided
if [ -z "$CLIENT_ID" ]; then
  RANDOM_NUMBER=$(shuf -i 100-999 -n 1)
  CLIENT_ID="Client$RANDOM_NUMBER"
  echo -e "${YELLOW}No client ID provided. Generated random ID: $CLIENT_ID${NC}"
fi

echo -e "${CYAN}Starting blockchain client...${NC}"
echo -e "${CYAN} - Launching client: $CLIENT_ID${NC}"

# Start the client in a new gnome-terminal with the title set to the client ID
gnome-terminal --title="$CLIENT_ID" -- bash -c "echo -e '${YELLOW}Starting Blockchain Client: $CLIENT_ID${NC}'; mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=$CLIENT_ID; bash"

echo -e "${GREEN}Client process started: $CLIENT_ID${NC}"