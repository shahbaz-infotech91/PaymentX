# ===========================================================
# PaymentX Enterprise Repository Setup
# Author: PaymentX
# Version: 1.0
# ===========================================================

Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "   PaymentX Enterprise Project Setup" -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""

$folders = @(
    ".github",
    ".github/workflows",

    "docs",
    "docs/adr",
    "docs/architecture",
    "docs/api",

    "docker",

    "src",
    "src/main",
    "src/main/java",
    "src/main/resources",

    "src/test",
    "src/test/java",
    "src/test/resources"
)

foreach ($folder in $folders) {
    if (!(Test-Path $folder)) {
        New-Item -ItemType Directory -Path $folder -Force | Out-Null
        Write-Host "Created: $folder" -ForegroundColor Green
    }
}

$files = @(
    "README.md",
    ".gitignore",
    "Dockerfile",
    "docker-compose.yml",
    "LICENSE",
    "pom.xml"
)

foreach ($file in $files) {
    if (!(Test-Path $file)) {
        New-Item -ItemType File -Path $file | Out-Null
        Write-Host "Created: $file" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host " Enterprise Folder Structure Created Successfully!" -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Cyan