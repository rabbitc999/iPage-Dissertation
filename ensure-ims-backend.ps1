# Reuse a ready backend for Vite or start the existing production launcher.
$ErrorActionPreference = 'Stop'
$port = 0
if ($env:SERVER_PORT -notmatch '^[0-9]+$' -or
    -not [int]::TryParse($env:SERVER_PORT, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
    throw 'SERVER_PORT must be a valid TCP port from 1 to 65535.'
}

function Test-ImsBackend {
    try {
        return (Invoke-RestMethod "http://127.0.0.1:$port/api/health" -TimeoutSec 2).status -eq 'ok'
    } catch {
        return $false
    }
}

if (Test-ImsBackend) { exit 0 }

Write-Host "Starting the IMS backend on port $port..."
$backend = Start-Process -FilePath $env:ComSpec -ArgumentList '/d /c run-ims.cmd <nul' -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru
$deadline = [DateTime]::UtcNow.AddMinutes(2)
do {
    if (Test-ImsBackend) { exit 0 }
    if ($backend.HasExited) {
        throw 'The IMS backend failed to start. Run run-ims.cmd to see its output.'
    }
    Start-Sleep -Milliseconds 750
} while ([DateTime]::UtcNow -lt $deadline)
throw "The IMS backend on port $port did not become ready."
