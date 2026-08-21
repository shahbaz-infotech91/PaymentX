# ENGLISH: A convenience script for local Control Center development.
# What it does: starts the backend (mvn spring-boot:run, port 8089) and
# the frontend (npm run dev, port 5173) each in their own background
# job, and prints where to reach both once they're up. Why it exists:
# matches the same convenience-script convention already established in
# paymentx-validation-suite/scripts/ (start-all.ps1/stop-all.ps1) for
# the 9 business services - this is that same pattern applied to the
# 2-process Control Center. How it will communicate with the backend:
# N/A - this script only launches the two real processes; the frontend
# process itself (once running) is what actually calls the backend
# process over HTTP, exactly as documented in
# frontend/src/api/axiosClient.ts.
#
# HINGLISH: Local Control Center development ke liye ek convenience
# script. Ye kya karti hai: backend (mvn spring-boot:run, port 8089)
# aur frontend (npm run dev, port 5173) ko har ek apne alag background
# job me start karta hai, aur dono up hone ke baad kahan reach karna
# hai print karta hai. Ye dashboard me kyu hai: paymentx-validation-
# suite/scripts/ (start-all.ps1/stop-all.ps1) me already established
# convenience-script convention se match karta hai 9 business services
# ke liye - ye wahi pattern hai 2-process Control Center par apply
# kiya gaya. Backend se kaise connect hogi: N/A - ye script sirf do
# real processes launch karta hai; frontend process khud (jab chal
# raha ho) actually backend process ko HTTP ke through call karta hai,
# exactly jaisa frontend/src/api/axiosClient.ts me documented hai.

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot\.."

Write-Host "Starting PaymentX Control Center backend (port 8089)..." -ForegroundColor Cyan
$backendJob = Start-Job -ScriptBlock {
    param($backendPath)
    Set-Location $backendPath
    mvn spring-boot:run
} -ArgumentList "$repoRoot\backend"

Write-Host "Starting PaymentX Control Center frontend (port 5173)..." -ForegroundColor Cyan
$frontendJob = Start-Job -ScriptBlock {
    param($frontendPath)
    Set-Location $frontendPath
    npm run dev
} -ArgumentList "$repoRoot\frontend"

Write-Host ""
Write-Host "Backend job id:  $($backendJob.Id)  -> http://localhost:8089/api/v1/health" -ForegroundColor Green
Write-Host "Frontend job id: $($frontendJob.Id)  -> http://localhost:5173" -ForegroundColor Green
Write-Host ""
Write-Host "Use 'Receive-Job -Id <id> -Keep' to view output, 'Stop-Job -Id <id>' to stop." -ForegroundColor DarkGray
