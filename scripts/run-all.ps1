# Builds and starts the whole stack in Docker: PostgreSQL, backend (with demo data) and frontend.
# Needs only Docker Desktop. Usage:  .\scripts\run-all.ps1          start (builds images)
#                                    .\scripts\run-all.ps1 -Down    stop everything
#                                    .\scripts\run-all.ps1 -Reset   stop and delete the local database
#                                    .\scripts\run-all.ps1 -TestLimits   start, then run the nginx rate-limit tests (needs Node 20+)
param([switch]$Down, [switch]$Reset, [switch]$TestLimits)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$compose = @('compose', '-f', 'docker-compose.local.yml')

if ($Reset) { docker @compose down -v; exit $LASTEXITCODE }
if ($Down)  { docker @compose down;    exit $LASTEXITCODE }

$env:DOCKER_BUILDKIT = '1'
docker @compose up -d --build --wait
if ($LASTEXITCODE -ne 0) { docker @compose logs --tail 50; exit $LASTEXITCODE }

Write-Host ""
Write-Host "HLS is running:"
Write-Host "  UI        http://localhost:5173   (sign in: asha.admin / Password123!)"
Write-Host "  Backend   http://localhost:8080"
Write-Host "  Database  localhost:5433  (hls / hls / hls)"
if ($TestLimits) {
  node --test scripts/rate-limit.test.mjs
  exit $LASTEXITCODE
}

Write-Host "Logs: docker compose -f docker-compose.local.yml logs -f    Stop: .\scripts\run-all.ps1 -Down"
