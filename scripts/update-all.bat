@echo off
rem Update haengjeongnuli + e-Petition, then start both. Double-click is OK.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0update-all.ps1" %*
echo.
pause
