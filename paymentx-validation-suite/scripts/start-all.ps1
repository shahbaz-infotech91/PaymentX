<#
.SYNOPSIS
    Starts the entire PaymentX platform: Docker infra (Postgres, Kafka,
    Redis, RabbitMQ, Zipkin, Prometheus, Grafana, MailHog, pgAdmin, Kafka
    UI) then all 9 application services, verifying real actuator health on
    each before declaring it started. Idempotent - safe to run when some or
    all components are already up (each check is skip-if-healthy).

.PARAMETER SkipBuild
    Skip checking/building jars before starting services - use when you
    know the jars in target/ are already current.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File start-all.ps1
#>

[CmdletBinding()]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Continue'
. "$PSScriptRoot\..\lib\common.ps1"

Write-Host ""
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host "PAYMENTX PLATFORM - START ALL" -ForegroundColor Cyan
Write-Host ("=" * 78) -ForegroundColor Cyan

# ---- Infra ----
Write-Section 'DOCKER INFRASTRUCTURE'
Push-Location "$Script:Root\infra"
try {
    $out = docker compose up -d 2>&1
    Add-Result -Category 'Infrastructure' -Name 'docker compose up -d' -Status 'PASS' -Detail (($out | Select-Object -Last 5) -join ' | ')
} catch {
    Add-Result -Category 'Infrastructure' -Name 'docker compose up -d' -Status 'FAIL' -Detail $_.Exception.Message -Fix 'Confirm Docker Desktop is running.'
} finally {
    Pop-Location
}

foreach ($chkName in $Script:InfraChecks.Keys) {
    $chk = $Script:InfraChecks[$chkName]
    $deadline = (Get-Date).AddSeconds(60)
    $ok = $false
    while ((Get-Date) -lt $deadline) {
        try { if (& $chk.Check) { $ok = $true; break } } catch {}
        Start-Sleep -Seconds 2
    }
    if ($ok) {
        Add-Result -Category 'Infrastructure' -Name $chkName -Status 'PASS' -Detail "container $($chk.Container) up and responding"
    } else {
        $running = docker inspect -f '{{.State.Running}}' $chk.Container 2>&1
        Add-Result -Category 'Infrastructure' -Name $chkName -Status 'FAIL' -Detail "running=$running, health check failed within 60s" -Fix "docker logs $($chk.Container)"
    }
}

# ---- Build check (jars must exist to start services) ----
if (-not $SkipBuild) {
    Write-Section 'BUILD CHECK'
    $missing = @()
    foreach ($key in $Script:Services.Keys) {
        $svc = $Script:Services[$key]
        $jarPath = "$Script:Root\$($svc.Module)\target\$($svc.Jar)"
        if (-not (Test-Path $jarPath)) { $missing += $key }
    }
    if ($missing.Count -gt 0) {
        Add-Result -Category 'Build' -Name 'jars present' -Status 'FAIL' -Detail "missing jars for: $($missing -join ', ')" -Fix 'Run: mvn clean install  (from C:\PaymentX), or pass -SkipBuild if you know this is stale/intentional.'
        Push-Location $Script:Root
        try {
            Write-Host "  Building missing jars via mvn clean install (this can take several minutes)..." -ForegroundColor Yellow
            & mvn clean install -q
            if ($LASTEXITCODE -eq 0) {
                Add-Result -Category 'Build' -Name 'mvn clean install' -Status 'PASS' -Detail 'jars built'
            } else {
                Add-Result -Category 'Build' -Name 'mvn clean install' -Status 'FAIL' -Detail "exit code $LASTEXITCODE" -Fix 'Run mvn clean install manually and inspect the error.'
            }
        } finally { Pop-Location }
    } else {
        Add-Result -Category 'Build' -Name 'jars present' -Status 'PASS' -Detail 'all 9 service jars found in target/'
    }
}

# ---- Services ----
Write-Section 'APPLICATION SERVICES'
if (-not (Test-Path $Script:OutDir)) { New-Item -ItemType Directory -Path $Script:OutDir -Force | Out-Null }

foreach ($key in $Script:Services.Keys) {
    $svc = $Script:Services[$key]
    $healthUrl = "http://localhost:$($svc.Port)/actuator/health"
    $already = Invoke-HealthCheck -Url $healthUrl -TimeoutSec 2
    if ($already.Ok) {
        Add-Result -Category 'Application' -Name $key -Status 'PASS' -Detail "already running and healthy on port $($svc.Port)"
        continue
    }
    $jarPath = "$Script:Root\$($svc.Module)\target\$($svc.Jar)"
    if (-not (Test-Path $jarPath)) {
        Add-Result -Category 'Application' -Name $key -Status 'FAIL' -Detail "jar not found at $jarPath" -Fix 'Run mvn clean install first.'
        continue
    }
    $svcLog = "$Script:OutDir\svc-$key.log"
    Start-Process -FilePath 'java' -ArgumentList @("-jar", "`"$jarPath`"") -WorkingDirectory "$Script:Root\$($svc.Module)" `
        -RedirectStandardOutput $svcLog -RedirectStandardError "$Script:OutDir\svc-$key.err.log" -WindowStyle Hidden | Out-Null
    $healthy = Wait-ForHealthy -Url $healthUrl -TimeoutSec 90 -PollSec 3
    if ($healthy) {
        Add-Result -Category 'Application' -Name $key -Status 'PASS' -Detail "started, healthy on port $($svc.Port)"
    } else {
        Add-Result -Category 'Application' -Name $key -Status 'FAIL' -Detail "did not become healthy within 90s, see $svcLog" -Fix "Check $svcLog and $Script:OutDir\svc-$key.err.log"
    }
}

# ---- Summary ----
Write-Host ""
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host "START-ALL SUMMARY" -ForegroundColor Cyan
Write-Host ("=" * 78) -ForegroundColor Cyan
$fail = ($Script:Results | Where-Object Status -eq 'FAIL').Count
$total = $Script:Results.Count
$pass = ($Script:Results | Where-Object Status -eq 'PASS').Count
Write-Host "$pass/$total PASS" -ForegroundColor $(if($fail -eq 0){'Green'}else{'Red'})
if ($fail -gt 0) {
    Write-Host "Failures:" -ForegroundColor Red
    $Script:Results | Where-Object Status -eq 'FAIL' | ForEach-Object { Write-Host "  - $($_.Name): $($_.Detail)" -ForegroundColor Red }
}
Write-Host ("=" * 78) -ForegroundColor Cyan
Write-Host ""
Write-Host "Platform ready. Run health-check.ps1 anytime to re-verify, or stop-all.ps1 to shut down."

if ($fail -eq 0) { exit 0 } else { exit 1 }
