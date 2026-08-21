<#
.SYNOPSIS
    Read-only status check for the PaymentX platform - infra containers +
    all 9 application services. Starts/stops nothing. Prints PASS/FAIL for
    each component and exits with code 0 (all healthy) or 1 (something down),
    so it's usable as a CI/scripting gate as well as an interactive check.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File health-check.ps1
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Continue'
. "$PSScriptRoot\..\lib\common.ps1"

Write-Host ""
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host "PAYMENTX PLATFORM HEALTH CHECK" -ForegroundColor Cyan
Write-Host ("=" * 78) -ForegroundColor Cyan

Write-Section 'INFRASTRUCTURE'
foreach ($chkName in $Script:InfraChecks.Keys) {
    $chk = $Script:InfraChecks[$chkName]
    $running = $null
    try { $running = docker inspect -f '{{.State.Running}}' $chk.Container 2>$null } catch {}
    if ($running -ne 'true') {
        Add-Result -Category 'Infrastructure' -Name $chkName -Status 'FAIL' -Detail "container $($chk.Container) is not running" -Fix "docker compose up -d $($chk.Container.Replace('paymentx-',''))  (or run start-all.ps1)"
        continue
    }
    $ok = $false
    try { $ok = & $chk.Check } catch { $ok = $false }
    if ($ok) {
        Add-Result -Category 'Infrastructure' -Name $chkName -Status 'PASS' -Detail "container running, health check responded"
    } else {
        Add-Result -Category 'Infrastructure' -Name $chkName -Status 'FAIL' -Detail "container running but health check did not respond" -Fix "docker logs $($chk.Container)"
    }
}

Write-Section 'APPLICATION SERVICES'
foreach ($key in $Script:Services.Keys) {
    $svc = $Script:Services[$key]
    $h = Invoke-HealthCheck -Url "http://localhost:$($svc.Port)/actuator/health" -TimeoutSec 3
    if ($h.Ok) {
        $json = Get-JsonSafe $h.Body
        $status = if ($json) { $json.status } else { 'UP' }
        Add-Result -Category 'Application' -Name $key -Status 'PASS' -Detail "port $($svc.Port), status=$status"
    } else {
        Add-Result -Category 'Application' -Name $key -Status 'FAIL' -Detail "port $($svc.Port) not responding (code=$($h.Code))" -Fix "java -jar $Script:Root\$($svc.Module)\target\$($svc.Jar)  (or run start-all.ps1)"
    }
}

Write-Host ""
Write-Host ("=" * 78) -ForegroundColor Cyan
$fail = ($Script:Results | Where-Object Status -eq 'FAIL').Count
$total = $Script:Results.Count
$pass = ($Script:Results | Where-Object Status -eq 'PASS').Count
Write-Host "RESULT: $pass/$total healthy" -ForegroundColor $(if($fail -eq 0){'Green'}else{'Red'})
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host ""

if ($fail -eq 0) { exit 0 } else { exit 1 }
