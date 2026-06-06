@echo off
REM ==== RCPSP Scheduling System - stop backend & frontend ====
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-all.ps1"
