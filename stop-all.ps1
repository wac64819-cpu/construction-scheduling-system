# ============================================================
#  Stop Backend(8080) and Frontend(5173)
# ============================================================
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 > $null

function Stop-Port($port, $name) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) { Write-Host "$name (port $port): not running"; return }
    foreach ($c in $conns) {
        try {
            Stop-Process -Id $c.OwningProcess -Force -ErrorAction Stop
            Write-Host "$name (port $port): stopped (PID $($c.OwningProcess))" -ForegroundColor Green
        } catch { Write-Host "$name (port $port): failed to stop PID $($c.OwningProcess)" -ForegroundColor Red }
    }
}

Write-Host "Stopping services..." -ForegroundColor Yellow
Stop-Port 8080 "Backend"
Stop-Port 5173 "Frontend"
Write-Host "`nMongoDB is left running (it is a manual service)."
Write-Host "To stop MongoDB too, run as admin:  net stop MongoDB"
Write-Host "`nPress Enter to exit..."
Read-Host
