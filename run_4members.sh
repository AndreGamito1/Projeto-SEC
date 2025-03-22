#!/bin/bash

# Clear the files in the key directories
rm -rf src/main/resources/priv_keys/*
rm -rf src/main/resources/pub_keys/*

# Array of member names
members=("member1" "member2" "member3" "member4")

# Function to run a member in a new terminal
run_member() {
    local member=$1
    # Check which terminal is available
    if command -v gnome-terminal &> /dev/null; then
        # For GNOME terminal (Linux)
        gnome-terminal --title="$member" -- bash -c "mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=$member; exec bash"
    elif command -v xterm &> /dev/null; then
        # For xterm (Linux/Unix)
        xterm -T "$member" -e "mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=$member; exec bash"
    elif command -v konsole &> /dev/null; then
        # For KDE Konsole
        konsole --new-tab --title "$member" -e bash -c "mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=$member; exec bash"
    elif command -v osascript &> /dev/null; then
        # For macOS Terminal
        osascript -e "tell application \"Terminal\" to do script \"echo -en \"\\033]0;$member\\007\"; cd $(pwd) && mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=$member\""
    elif command -v cmd.exe &> /dev/null; then
        # For Windows Command Prompt
        start "cmd.exe" /K "title $member && mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=$member"
    else
        echo "No supported terminal found. Please run the command manually:"
        echo "mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=$member"
    fi
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

echo "All members launched!"