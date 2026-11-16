@echo off
rem Start haengjeongnuli + e-Petition together. Double-click is OK.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" %*
echo.
pause
