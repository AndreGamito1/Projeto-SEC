param (
    [int]$HttpPort = 8080,
    [int]$NumClients = 2,
    [switch]$RestApiOnly = $false
)

$actualHttpPort = $HttpPort + 1

Write-Host "Starting Byzantine Blockchain System..." -ForegroundColor Green
Write-Host "REST API will be available at: http://localhost:$actualHttpPort/blockchain/"

# Clean key folders before initialization
Write-Host "Cleaning key folders..." -ForegroundColor Cyan
if (Test-Path "shared/priv_keys") {
    Remove-Item -Path "shared/priv_keys/*" -Force -Recurse -ErrorAction SilentlyContinue
}
if (Test-Path "shared/pub_keys") {
    Remove-Item -Path "shared/pub_keys/*" -Force -Recurse -ErrorAction SilentlyContinue
}
# Ensure directories exist
New-Item -ItemType Directory -Path "shared/priv_keys" -Force | Out-Null
New-Item -ItemType Directory -Path "shared/pub_keys" -Force | Out-Null

# 1. Start Leader
Write-Host "Starting Leader process..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Leader...' -ForegroundColor Yellow; mvn exec:java '-Dexec.mainClass=com.depchain.consensus.Leader'"

# Wait for Leader to initialize
Start-Sleep -Seconds 2

# 2. Start Members
for ($i = 1; $i -le 4; $i++) {
    Write-Host "Starting Member $i process..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Member $i...' -ForegroundColor Yellow; mvn exec:java '-Dexec.mainClass=com.depchain.consensus.Member' '-Dexec.args=member$i'"
    Start-Sleep -Milliseconds 500
}

# Wait for Members to initialize
Start-Sleep -Seconds 3

# 3. Start ClientLibrary with REST API
Write-Host "Starting ClientLibrary REST API on port $HttpPort..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting ClientLibrary REST API on port $HttpPort...' -ForegroundColor Yellow; mvn exec:java '-Dexec.mainClass=com.depchain.client.ClientLibrary' '-Dexec.args=$HttpPort'"

# Wait for REST API to initialize
Start-Sleep -Seconds 3

# 4. Start Clients (if not disabled)
if (-not $RestApiOnly) {
    Write-Host "Starting $NumClients blockchain client(s)..." -ForegroundColor Cyan
   
    for ($i = 1; $i -le $NumClients; $i++) {
        $clientId = "Client$i"
        Write-Host "  - Launching client: $clientId" -ForegroundColor Cyan
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Blockchain Client: $clientId' -ForegroundColor Yellow; mvn exec:java '-Dexec.mainClass=com.depchain.client.Client' '-Dexec.args=$clientId'"
        Start-Sleep -Milliseconds 500
    }
}

Write-Host "Blockchain system startup complete!" -ForegroundColor Green
Write-Host "REST API is available at: http://localhost:$actualHttpPort/blockchain/"

# Helper function for launching more clients
function Start-BlockchainClient {
    param (
        [string]$ClientId = "Client$(Get-Random -Minimum 100 -Maximum 999)"
    )
   
    Write-Host "Launching client: $ClientId" -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Blockchain Client: $ClientId' -ForegroundColor Yellow; mvn exec:java '-Dexec.mainClass=com.depchain.client.Client' '-Dexec.args=$ClientId'"
}

# Export function for PowerShell session
Export-ModuleMember -Function Start-BlockchainClient -ErrorAction SilentlyContinue

Write-Host "`nTo launch additional clients, run:" -ForegroundColor Magenta
Write-Host "Start-BlockchainClient -ClientId 'Alice'" -ForegroundColor Yellow