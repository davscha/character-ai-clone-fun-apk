@echo off
rem Startet den Stimmen-Server für Character Fun
cd /d "%~dp0"
call venv\Scripts\activate
python stimmen_server.py
pause
