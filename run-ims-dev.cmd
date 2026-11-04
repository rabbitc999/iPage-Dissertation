@echo off
rem Start the IMS frontend with Vite hot reload.
call "%~dp0run-ims.cmd" --dev
exit /b %ERRORLEVEL%
