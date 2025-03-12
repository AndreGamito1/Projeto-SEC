# Blockchain Client Starter
param (
    [Parameter(Mandatory=$false)]
    [string]$ClientId
)

# Generate a random client ID if none is provided
if ([string]::IsNullOrEmpty($ClientId)) {
    $randomNumber = Get-Random -Minimum 100 -Maximum 999
    $ClientId = "Client$randomNumber"
    Write-Host "No client ID provided. Generated random ID: $ClientId" -ForegroundColor Yellow
}

Write-Host "Starting blockchain client..." -ForegroundColor Cyan
Write-Host "  - Launching client: $ClientId" -ForegroundColor Cyan

Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host 'Starting Blockchain Client: $ClientId' -ForegroundColor Yellow; mvn exec:java '-Dexec.mainClass=com.example.Client' '-Dexec.args=$ClientId'"

Write-Host "Client process started: $ClientId" -ForegroundColor Green