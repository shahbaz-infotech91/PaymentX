<#
.SYNOPSIS
    Stops the entire PaymentX platform: all 9 application service JVMs,
    then the Docker infra containers. Safe by default - infra containers
    are stopped (docker compose stop), not removed, so named volumes
    (Postgres data, Kafka data) and the containers themselves survive for
    next time. Pass -RemoveContainers for a full teardown (still keeps the
    named volumes; add -RemoveVolumes too for a truly clean slate - this
    DESTROYS all Postgres/Kafka data, so it is never implied by any other
    flag).

.PARAMETER SkipInfra
    Only stop the application services, leave Docker infra running.

.PARAMETER RemoveContainers
    After stopping, also `docker compose down` (removes containers, keeps
    named volumes).

.PARAMETER RemoveVolumes
    Only valid together with -RemoveContainers. Adds `-v` to the down
    command, deleting the Postgres/Kafka named volumes too. DESTRUCTIVE -
    every database and topic offset is gone. Requires explicit confirmation
    unless -Force is also passed.

.PARAMETER Force
    Skip the confirmation prompt for -RemoveVolumes.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File stop-all.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File stop-all.ps1 -SkipInfra
#>

[CmdletBinding()]
param(
    [switch]$SkipInfra,
    [switch]$RemoveContainers,
    [switch]$RemoveVolumes,
    [switch]$Force
)

$ErrorActionPreference = 'Continue'
. "$PSScriptRoot\..\lib\common.ps1"

Write-Host ""
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host "PAYMENTX PLATFORM - STOP ALL" -ForegroundColor Cyan
Write-Host ("=" * 78) -ForegroundColor Cyan

Write-Section 'APPLICATION SERVICES'
foreach ($key in $Script:Services.Keys) {
    $svc = $Script:Services[$key]
    $jarName = $svc.Jar
    $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -like "*$jarName*" }
    if (-not $procs) {
        Add-Result -Category 'Application' -Name $key -Status 'SKIP' -Detail 'not running (no matching java.exe process found)'
        continue
    }
    $stopped = $true
    foreach ($p in $procs) {
        try {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
        } catch {
            $stopped = $false
            Add-Result -Category 'Application' -Name $key -Status 'FAIL' -Detail "could not stop PID $($p.ProcessId): $($_.Exception.Message)"
        }
    }
    if ($stopped) {
        Add-Result -Category 'Application' -Name $key -Status 'PASS' -Detail "stopped $($procs.Count) process(es): $($procs.ProcessId -join ', ')"
    }
}

if (-not $SkipInfra) {
    Write-Section 'DOCKER INFRASTRUCTURE'
    Push-Location "$Script:Root\infra"
    try {
        $out = docker compose stop 2>&1
        Add-Result -Category 'Infrastructure' -Name 'docker compose stop' -Status 'PASS' -Detail 'containers stopped (volumes preserved)'

        if ($RemoveContainers) {
            if ($RemoveVolumes) {
                if (-not $Force) {
                    Write-Host ""
                    Write-Host "WARNING: -RemoveVolumes will PERMANENTLY DELETE all Postgres data and Kafka topic data." -ForegroundColor Red
                    $confirm = Read-Host "Type YES to confirm"
                    if ($confirm -ne 'YES') {
                        Add-Result -Category 'Infrastructure' -Name 'docker compose down -v' -Status 'SKIP' -Detail 'user did not confirm - volumes preserved'
                        Pop-Location
                        return
                    }
                }
                $out2 = docker compose down -v 2>&1
                Add-Result -Category 'Infrastructure' -Name 'docker compose down -v' -Status 'PASS' -Detail 'containers AND named volumes removed'
            } else {
                $out2 = docker compose down 2>&1
                Add-Result -Category 'Infrastructure' -Name 'docker compose down' -Status 'PASS' -Detail 'containers removed, named volumes preserved'
            }
        }
    } catch {
        Add-Result -Category 'Infrastructure' -Name 'docker compose stop' -Status 'FAIL' -Detail $_.Exception.Message
    } finally {
        Pop-Location
    }
} else {
    Add-Result -Category 'Infrastructure' -Name 'docker compose stop' -Status 'SKIP' -Detail '-SkipInfra passed'
}

Write-Host ""
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host "STOP-ALL SUMMARY" -ForegroundColor Cyan
Write-Host ("=" * 78) -ForegroundColor Cyan
$fail = ($Script:Results | Where-Object Status -eq 'FAIL').Count
$total = $Script:Results.Count
$pass = ($Script:Results | Where-Object Status -eq 'PASS').Count
$skip = ($Script:Results | Where-Object Status -eq 'SKIP').Count
Write-Host "$pass PASS / $skip SKIP / $fail FAIL (of $total)" -ForegroundColor $(if($fail -eq 0){'Green'}else{'Red'})
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host ""

if ($fail -eq 0) { exit 0 } else { exit 1 }
