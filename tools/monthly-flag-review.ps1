# Monthly flag review, steps 1 and 2: harvest fresh headlines and draw the sample.
#
# MLE is a teaching tool (docs/positions.md, position 17). Once a month a person
# reviews up to 50 headlines it flagged. This script does the part that needs the network and
# this machine: it harvests news published since the first of the month, flags it the way an
# app with the page would, and draws the sample. The rest (answering the questions and
# scoring) is done afterwards; see CONTRIBUTING.md, "The monthly
# flag review".
#
# Usage:   powershell -ExecutionPolicy Bypass -File tools\monthly-flag-review.ps1 [-Month 2026-11]
#
# Rules kept by this file:
#   1. ASCII only (Windows PowerShell 5.1 compatibility).
#   2. Native stderr redirection through cmd.exe to prevent false-positive $? aborts.

param([string]$Month = (Get-Date -Format "yyyy-MM"))

$ErrorActionPreference = "Continue"

if ($Month -notmatch '^\d{4}-\d{2}$') {
    Write-Output "Month must look like 2026-11."
    exit 2
}

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$gradlew = Join-Path $root "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Output "Could not find gradlew.bat at $gradlew"
    exit 1
}

$dir = "docs/benchmark/flag-review/$Month"
$intake = "$dir/intake.tsv"
$since = "$Month-01"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $root "build") | Out-Null
$log = Join-Path $root "build\flag-review-$Month.txt"

Write-Output ""
Write-Output "Flag review $Month"
Write-Output "------------------"

# 1. Harvest fresh headlines (fetches pages; stories already in a labelled set are skipped)
Write-Output "[1/2] Harvesting news published since $since into $intake ..."
"=== $(Get-Date -Format 'u') harvest ===" | Out-File -FilePath $log -Encoding utf8
& cmd.exe /c "`"$gradlew`" :tools:feed-harvest:haystack --args=`"--context-intake --since=$since --output=$intake`" -q >> `"$log`" 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Output "Harvest failed with exit code $LASTEXITCODE. See $log"
    exit 1
}
if (-not (Test-Path $intake)) {
    Write-Output "No intake file was written. See $log"
    exit 1
}
$rows = (Get-Content $intake | Where-Object { $_ -notmatch '^#' -and $_ -notmatch '^id\t' -and $_.Trim() -ne "" }).Count
Write-Output "      $rows headlines harvested."

# 2. Flag them and draw up to 50 flagged headlines, spread across outlets
Write-Output "[2/2] Drawing the sample ..."
"=== $(Get-Date -Format 'u') sample ===" | Out-File -FilePath $log -Encoding utf8 -Append
& cmd.exe /c "`"$gradlew`" :tools:benchmark:run --args=`"flag-review sample --intake $intake --month $Month`" -q >> `"$log`" 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Output "Sampling failed with exit code $LASTEXITCODE. See $log"
    exit 1
}
Get-Content $log | Where-Object { $_ -match '^flag-review' } | ForEach-Object { Write-Output "      $_" }

Write-Output ""
Write-Output "Next:"
Write-Output "  1. Open $dir/sample.json."
Write-Output "  2. Answer the two questions on each headline and save them as $dir/answers.json."
Write-Output "  3. Score it (gradlew :tools:benchmark:run with flag-review score --month $Month), and add its decisions to edge-cases.tsv."
Write-Output ""
