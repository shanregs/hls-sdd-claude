# Starts a local PostgreSQL for HLS in its own compose project ("hls-local"), so it never touches
# data from other compose projects. Flyway creates every table when the backend starts.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
# Host port 5433 (not 5432) so it never collides with another PostgreSQL already running here.
$env:HLS_DB_PORT = '5433'
docker compose -p hls-local up -d --wait postgres
Write-Host "PostgreSQL is ready on localhost:5433 (db/user/password: hls/hls/hls)."
