#!/bin/bash

# Clear the files in the key directories
rm -rf src/main/resources/priv_keys/*
rm -rf src/main/resources/pub_keys/*

# Array of member names
members=("member1" "member2" "member3" "member4")

# Function to run a member in a new terminal
run_member() {
    local member=$1
    gnome-terminal --title="$member" -- bash -c "cd $(pwd) && mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=$member; exec bash"
}

# Function to run a client or client library in a new terminal
run_client() {
    local title=$1
    local command=$2
    gnome-terminal --title="$title" -- bash -c "cd $(pwd) && $command; exec bash"
}

# Create directories if they don't exist
mkdir -p src/main/resources/priv_keys
mkdir -p src/main/resources/pub_keys

echo "Cleared key directories and running members..."

# Run each member in a new terminal
for member in "${members[@]}"; do
    run_member "$member"
    # Small delay to prevent all terminals from starting simultaneously
    sleep 1
done

echo "Starting client and client library..."

# Start client1
run_client "client1" "mvn exec:java -Dexec.mainClass=com.depchain.client.Client -Dexec.args=\"Andre\""
sleep 1

# Start client library on port 8080
run_client "ClientLibrary" "mvn exec:java -Dexec.mainClass=com.depchain.client.ClientLibrary -Dexec.args=8080"

echo "All members and clients launched!"