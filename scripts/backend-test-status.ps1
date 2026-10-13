<#
.SYNOPSIS
  Shows the progress of a full backend test run started with:  cd backend; mvn -o test > target/full-run.log 2>&1

.DESCRIPTION
  Reads backend/target/full-run.log and reports whether the run is still going, how many test classes have finished,
  the totals so far, any failing classes and the last thing Maven printed. Safe to run as often as you like; it only reads.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/backend-test-status.ps1
  powershell -ExecutionPolicy Bypass -File scripts/backend-test-status.ps1 -Watch      # refresh every 15 seconds
#>
param(
    [switch]$Watch,
    [int]$IntervalSeconds = 15
)

$backend = Join-Path $PSScriptRoot "..\backend"
$log = Join-Path $backend "target\full-run.log"
$startedFile = Join-Path $backend "target\full-run.started"

function Show-Status {
    if (-not (Test-Path $log)) {
        Write-Host "No log yet at $log. Start a run with:  cd backend; mvn -o test > target/full-run.log 2>&1" -ForegroundColor Yellow
        return
    }
    $lines = Get-Content $log
    $totalClasses = (Get-ChildItem (Join-Path $backend "src\test") -Recurse -Include "*Test.java", "*Tests.java" | Measure-Object).Count

    # one line per finished class:  Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 14.2 s -- in com.hls.X
    $done = $lines | Where-Object { $_ -match "Tests run: \d+.*-- in " }
    $tests = 0; $failures = 0; $errors = 0; $skipped = 0; $bad = @()
    foreach ($line in $done) {
        if ($line -match "Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+).*-- in (\S+)") {
            $tests += [int]$Matches[1]; $failures += [int]$Matches[2]; $errors += [int]$Matches[3]; $skipped += [int]$Matches[4]
            if ([int]$Matches[2] -gt 0 -or [int]$Matches[3] -gt 0) { $bad += $Matches[5] }
        }
    }

    $finished = $lines | Where-Object { $_ -match "BUILD (SUCCESS|FAILURE)" } | Select-Object -Last 1
    $mvnRunning = [bool](Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -match "plexus-classworlds|surefire" })

    $state = if ($finished) { ($finished -replace ".*BUILD ", "FINISHED: BUILD ").Trim() }
             elseif ($mvnRunning) { "RUNNING" }
             else { "STOPPED (no Maven process and no BUILD line; the run may have been interrupted)" }

    $elapsed = ""
    if (Test-Path $startedFile) {
        $started = [DateTimeOffset]::FromUnixTimeSeconds([long](Get-Content $startedFile)).LocalDateTime
        $span = (Get-Date) - $started
        if ($finished) { $span = (Get-Item $log).LastWriteTime - $started }
        $elapsed = "{0:hh\:mm\:ss}" -f $span
    }

    $pct = if ($totalClasses -gt 0) { [math]::Min(100, [math]::Round(100 * $done.Count / $totalClasses)) } else { 0 }
    $eta = ""
    if (-not $finished -and $done.Count -gt 0 -and $span.TotalSeconds -gt 0) {
        $left = [math]::Max(0, $totalClasses - $done.Count) * ($span.TotalSeconds / $done.Count)
        $eta = "  (about {0:N0} min left)" -f ($left / 60)
    }

    Write-Host ""
    Write-Host "Backend test run" -ForegroundColor Cyan
    Write-Host ("  State    : {0}" -f $state)
    if ($elapsed) { Write-Host ("  Elapsed  : {0}{1}" -f $elapsed, $eta) }
    Write-Host ("  Classes  : {0} of about {1} finished ({2}%)" -f $done.Count, $totalClasses, $pct)
    Write-Host ("  Tests    : {0} run, {1} failed, {2} errors, {3} skipped" -f $tests, $failures, $errors, $skipped)
    if ($bad.Count -gt 0) {
        Write-Host "  Failing classes so far:" -ForegroundColor Red
        $bad | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
    } else {
        Write-Host "  Failing classes so far: none" -ForegroundColor Green
    }
    $summary = $lines | Where-Object { $_ -match "^\[(INFO|ERROR|WARNING)\] Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+$" } | Select-Object -Last 1
    if ($summary) { Write-Host ("  Final    : {0}" -f ($summary -replace "^\[\w+\] ", "")) }
    Write-Host "  Last line:"
    ($lines | Where-Object { $_.Trim() } | Select-Object -Last 2) | ForEach-Object { Write-Host ("    " + $_.Substring(0, [math]::Min(150, $_.Length))) -ForegroundColor DarkGray }
    Write-Host "  Details  : backend\target\surefire-reports\ (one .txt per class), full log: $log"
}

if ($Watch) {
    while ($true) {
        Clear-Host
        Show-Status
        Write-Host "`nRefreshing every $IntervalSeconds s. Ctrl+C to stop."
        Start-Sleep -Seconds $IntervalSeconds
    }
} else {
    Show-Status
}
