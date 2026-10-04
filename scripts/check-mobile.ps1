# Runs the mobile checks used before a commit or in CI (spec 018 T007):
# install, lint, typecheck, tests, and the Android manifest check.
$ErrorActionPreference = "Stop"
Push-Location (Join-Path $PSScriptRoot "..\mobile")
try {
    npm ci
    npm run lint
    npm run typecheck
    npm test -- --ci
    npm run check:manifest
} finally {
    Pop-Location
}
