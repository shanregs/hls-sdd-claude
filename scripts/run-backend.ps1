# Runs the backend on http://localhost:8080 against the local PostgreSQL, with the dev-only demo
# data enabled (hls.seed.demo-data=true): demo users plus a Zone, Schools, a Manager and Teachers.
# Everything is idempotent, so restarting is safe. Start the database first (scripts\start-db.ps1).
$ErrorActionPreference = 'Stop'
$env:DB_URL = 'jdbc:postgresql://localhost:5433/hls'
$env:DB_USERNAME = 'hls'
$env:DB_PASSWORD = 'hls'
$env:COOKIE_SECURE = 'false'            # plain http://localhost
$env:HLS_SEED_DEMO_DATA = 'true'        # binds to hls.seed.demo-data
Set-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'backend')
mvn spring-boot:run
