# Clear the files in the key directories
Remove-Item -Path "src\main\resources\priv_keys\*" -Force -Recurse -ErrorAction SilentlyContinue
Remove-Item -Path "src\main\resources\pub_keys\*" -Force -Recurse -ErrorAction SilentlyContinue

# Array of member configurations (name, behavior)
$members = @(
    @{name="member1"; behavior="default"},
    @{name="member2"; behavior="default"},
    @{name="member3"; behavior="default"},
    @{name="member4"; behavior="RANDOM"}  # Byzantine NoMan behavior
)

# Function to run a member in a new terminal
function Run-Member {
    param(
        [string]$member,
        [string]$behavior
    )
   
    if ($behavior -eq "default") {
        # Start a normal member
        Start-Process cmd.exe -ArgumentList "/K title $member && mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=`"$member`""
    } else {
        # Start a Byzantine member with behavior
        Start-Process cmd.exe -ArgumentList "/K title $member-$behavior && mvn exec:java -Dexec.mainClass=com.depchain.consensus.Main -Dexec.args=`"$member $behavior`""
    }
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
    Run-Member -member $member.name -behavior $member.behavior
    # Small delay to prevent all terminals from starting simultaneously
    Start-Sleep -Seconds 1
}

Write-Host "Starting client and client library..."

# Start client1
Run-Client -title "client1" -command "mvn exec:java -Dexec.mainClass=com.depchain.client.Client -Dexec.args=`"client1`""
Start-Sleep -Seconds 1

# Start client library on port 8080
Run-Client -title "ClientLibrary" -command "mvn exec:java -Dexec.mainClass=com.depchain.client.ClientLibrary -Dexec.args=8080"

# Start additional terminals for Jiraiya and Miguel
Run-Client -title "jiraiya" -command "mvn exec:java -Dexec.mainClass=com.depchain.client.Client -Dexec.args=`"jiraiya`""
Start-Sleep -Seconds 1

Write-Host "All members and clients launched!"
Write-Host "Network configuration: 3 normal members, 1 Byzantine NoMan member (member4)"