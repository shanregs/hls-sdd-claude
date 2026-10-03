# Runs the frontend dev server on http://localhost:5173 (proxies /api to the backend on :8080).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend')
if (-not (Test-Path node_modules)) { npm install }
npm run dev
