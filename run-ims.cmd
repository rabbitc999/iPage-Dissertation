@echo off
rem Build and run iPage-IMS from this checkout, preserving the server working directory.
setlocal
cd /d "%~dp0" || exit /b 1
chcp 65001 >nul 2>&1

if not defined SERVER_PORT set "SERVER_PORT=8080"
if /i "%~1"=="--dev" goto :dev
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-running-ims.ps1"
if errorlevel 1 goto :failed

where npm.cmd >nul 2>&1
if errorlevel 1 (
    echo Node.js and npm are required. Install Node.js and reopen this script.
    goto :failed
)

if not defined JAVA_HOME (
    for /f "tokens=1,* delims==" %%A in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /l /c:"java.home ="') do (
        for /f "tokens=*" %%J in ("%%B") do set "JAVA_HOME=%%J"
    )
)
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo JDK 17 or later is required. Set JAVA_HOME or add the JDK to PATH.
    goto :failed
)

echo [1/3] Installing dependencies and building the web app...
pushd web || goto :failed
call npm.cmd ci
if not errorlevel 1 call npm.cmd run build
if errorlevel 1 (
    popd
    goto :failed
)
popd

echo [2/3] Packaging the server...
rem clean 必须保留：Maven 只会往 target\classes 里覆盖，不会删掉上一版的前端哈希文件，
rem 少了 clean 就会把旧 chunk 一起打进 jar，浏览器可能加载到上一版页面。
call mvnw.cmd -pl server -am clean package -DskipTests
if errorlevel 1 goto :failed

rem Keep Maven's output unlocked while Java is running.
copy /y "server\target\ims-server-1.0.0.jar" "server\target\ims-server-run-%SERVER_PORT%.jar" >nul
if errorlevel 1 goto :failed

echo [3/3] Starting iPage-IMS at http://127.0.0.1:%SERVER_PORT%
echo Press Ctrl+C to stop the server.
pushd server || goto :failed
"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%~dp0server\target\ims-server-run-%SERVER_PORT%.jar"
if errorlevel 1 (
    popd
    goto :failed
)
popd
exit /b 0

:dev
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0ensure-ims-backend.ps1"
if errorlevel 1 goto :failed
pushd web || goto :failed
if not exist "node_modules\.bin\vite.cmd" call npm.cmd ci
if errorlevel 1 (
    popd
    goto :failed
)
echo Frontend hot reload: http://127.0.0.1:5173/
call npm.cmd run dev -- --host 127.0.0.1 --strictPort --open
set "DEV_EXIT=%ERRORLEVEL%"
popd
if not "%DEV_EXIT%"=="0" goto :failed
exit /b 0

:failed
echo.
echo Startup failed. Review the output above.
pause
exit /b 1
