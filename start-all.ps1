# ============================================================
#  RCPSP Construction Scheduling System - One-Click Launcher
#  Auto-starts: MongoDB + Backend(SpringBoot) + Frontend(Vite)
# ============================================================
$ErrorActionPreference = 'Continue'
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 > $null

$root   = $PSScriptRoot
$jdk    = 'D:\jdk23\jdk-23.0.2+7'
$mvnbin = 'D:\maven\apache-maven-3.9.9\bin'

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  RCPSP Scheduling System - Launcher" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# --- locate backend / frontend automatically ---
$backend  = (Get-ChildItem $root -Recurse -Filter pom.xml -ErrorAction SilentlyContinue |
             Where-Object { $_.FullName -notmatch 'node_modules' } | Select-Object -First 1).Directory.FullName
$frontend = (Get-ChildItem $root -Recurse -Filter package.json -ErrorAction SilentlyContinue |
             Where-Object { $_.FullName -match 'rcpspweb' -and $_.FullName -notmatch 'node_modules' } |
             Select-Object -First 1).Directory.FullName

if (-not $backend)  { Write-Host "[ERROR] backend (pom.xml) not found under $root" -ForegroundColor Red; pause; exit 1 }
if (-not $frontend) { Write-Host "[ERROR] frontend (rcpspweb) not found under $root" -ForegroundColor Red; pause; exit 1 }
Write-Host "Backend : $backend"
Write-Host "Frontend: $frontend"

# --- 1) MongoDB ---
Write-Host "`n[1/3] MongoDB ..." -ForegroundColor Yellow
$mongoUp = (Test-NetConnection localhost -Port 27017 -WarningAction SilentlyContinue).TcpTestSucceeded
if ($mongoUp) {
    Write-Host "      already running (27017)" -ForegroundColor Green
} else {
    Write-Host "      starting service (please accept the UAC prompt)..."
    Start-Process net -ArgumentList 'start','MongoDB' -Verb RunAs
    Start-Sleep -Seconds 3
}

# --- 2) Backend in its own PowerShell window ---
Write-Host "[2/3] Backend (SpringBoot, port 8080) - new window ..." -ForegroundColor Yellow
$beCmd = "`$Host.UI.RawUI.WindowTitle='Backend SpringBoot 8080'; " +
         "`$env:JAVA_HOME='$jdk'; " +
         "`$env:PATH='$jdk\bin;$mvnbin;'+`$env:PATH; " +
         "Set-Location '$backend'; " +
         "Write-Host 'Starting backend, please wait 10-30s...' -ForegroundColor Cyan; " +
         "mvn spring-boot:run"
Start-Process powershell -ArgumentList '-NoExit','-NoProfile','-Command',$beCmd

# --- 3) Frontend in its own PowerShell window ---
Write-Host "[3/3] Frontend (Vite, port 5173) - new window ..." -ForegroundColor Yellow
$feCmd = "`$Host.UI.RawUI.WindowTitle='Frontend Vite 5173'; " +
         "Set-Location '$frontend'; " +
         "Write-Host 'Starting frontend...' -ForegroundColor Cyan; " +
         "npm run dev"
Start-Process powershell -ArgumentList '-NoExit','-NoProfile','-Command',$feCmd

# --- wait for readiness then open browser ---
Write-Host "`nWaiting for services to be ready..." -ForegroundColor Yellow
$ready = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 2
    $fe = (Test-NetConnection localhost -Port 5173 -WarningAction SilentlyContinue -InformationLevel Quiet)
    if ($fe) { $ready = $true; break }
}
if ($ready) {
    Start-Process "http://localhost:5173"
    Write-Host "`nDONE. Browser opened at http://localhost:5173" -ForegroundColor Green
} else {
    Write-Host "`nFrontend not detected yet. Open http://localhost:5173 manually once the Vite window shows 'ready'." -ForegroundColor Yellow
}
Write-Host "Backend & Frontend run in their own windows. Close those windows to stop them."
Write-Host "This window can be closed."
Write-Host "`nPress Enter to exit this launcher window..."
Read-Host
