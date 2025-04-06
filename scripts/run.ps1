# Clear the files in the key directories
Remove-Item -Path "src\main\resources\priv_keys\*" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "src\main\resources\pub_keys\*" -Force -Recurse -ErrorAction SilentlyContinue

# Array of member names
$members = @("member1", "member2", "member3", "member4")

# Function to run a member in a new terminal
function Run-Member {
    param(
        [string]$member
    )
   
    # Start a new Command Prompt window
    Start-Process cmd.exe -ArgumentList "/K title $member && mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=$member"
}

# Function to run a client or client library in a new terminal
function Run-Client {
    param(
        [string]$title,
        [string]$command
    )
   
    # Start a new Command Prompt window
    Start-Process cmd.exe -ArgumentList "/K title $title && $command"
}

# Create directories if they don't exist
New-Item -Path "src\main\resources\priv_keys" -ItemType Directory -Force | Out-Null
New-Item -Path "src\main\resources\pub_keys" -ItemType Directory -Force | Out-Null

Write-Host "Cleared key directories and running members..."

# Run each member in a new terminal
foreach ($member in $members) {
    Run-Member -member $member
    # Small delay to prevent all terminals from starting simultaneously
    Start-Sleep -Seconds 1
}

Write-Host "Starting client and client library..."

# Start client1
Run-Client -title "client1" -command "mvn exec:java -Dexec.mainClass=com.depchain.client.Client -Dexec.args=`"client1`""
Start-Sleep -Seconds 1

# Start client library on port 8080
Run-Client -title "ClientLibrary" -command "mvn exec:java -Dexec.mainClass=com.depchain.client.ClientLibrary -Dexec.args=8080"

# Start additional terminals for Miguel
Run-Client -title "Miguel" -command "mvn exec:java -Dexec.mainClass=com.depchain.client.Client -Dexec.args=`"Miguel`""
Start-Sleep -Seconds 1

Write-Host "All members and clients launched!"