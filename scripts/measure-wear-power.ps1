param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label,
    [ValidateRange(20, 1800)][int]$Seconds = 120,
    [int]$ServerPort = 5037,
    [string]$Adb = 'D:\Android\Sdk\platform-tools\adb.exe'
)

# Read-only diagnostic capture. Does not reset battery statistics or change device settings.
# Charging samples measure CPU behavior only; never interpret them as battery drain.
$ErrorActionPreference = 'Stop'
$destination = Join-Path $PSScriptRoot "..\reports\hardware-2026-09-07\$Label"
New-Item -ItemType Directory -Force $destination | Out-Null
function Read-Adb([string[]]$Arguments) {
    $result = & $Adb -P $ServerPort -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB capture failed: $result" }
    return ($result -join "`n")
}
$appPid = (Read-Adb @('shell', 'pidof', 'com.tidal.wear')).Trim()
if ($appPid -notmatch '^\d+$') { throw 'Expected one running Untidy process' }
$clockTicks = [int](Read-Adb @('shell', 'getconf', 'CLK_TCK'))
Read-Adb @('shell', 'dumpsys', 'power') | Out-File "$destination\power-start.txt"
Read-Adb @('shell', 'dumpsys', 'media_session') | Out-File "$destination\session-start.txt"
Read-Adb @('shell', 'dumpsys', 'media.audio_flinger') | Out-File "$destination\audio-start.txt"
$timer = [Diagnostics.Stopwatch]::StartNew()
$samples = @()
do {
    $stat = Read-Adb @('shell', 'cat', "/proc/$appPid/stat")
    $fields = ($stat -replace '^\d+ \(.*\) ', '') -split '\s+'
    $battery = Read-Adb @('shell', 'dumpsys', 'battery')
    $sample = [pscustomobject]@{
        elapsedSeconds = [Math]::Round($timer.Elapsed.TotalSeconds, 3)
        utc = [DateTime]::UtcNow.ToString('o')
        pid = $appPid
        cpuSeconds = ([long]$fields[11] + [long]$fields[12]) / [double]$clockTicks
        battery = $battery
    }
    $samples += $sample
    $samples | ConvertTo-Json -Depth 3 | Out-File "$destination\samples.json"
    Write-Output "${Label}: $($sample.elapsedSeconds)s cpu=$($sample.cpuSeconds)s"
    if ($timer.Elapsed.TotalSeconds -ge $Seconds) { break }
    Start-Sleep -Seconds ([Math]::Min(20, [Math]::Ceiling($Seconds - $timer.Elapsed.TotalSeconds)))
} while ($true)
Read-Adb @('shell', 'dumpsys', 'power') | Out-File "$destination\power-end.txt"
Read-Adb @('shell', 'dumpsys', 'media_session') | Out-File "$destination\session-end.txt"
Read-Adb @('shell', 'dumpsys', 'media.audio_flinger') | Out-File "$destination\audio-end.txt"
Read-Adb @('shell', 'dumpsys', 'batterystats', 'com.tidal.wear') | Out-File "$destination\batterystats-end.txt"
$elapsed = $samples[-1].elapsedSeconds - $samples[0].elapsedSeconds
$cpu = $samples[-1].cpuSeconds - $samples[0].cpuSeconds
Write-Output "${Label}: CPU $([Math]::Round($cpu, 2))s / $([Math]::Round($elapsed, 2))s = $([Math]::Round(100 * $cpu / $elapsed, 2))% of one core; inspect charging and playback state before drawing conclusions."
