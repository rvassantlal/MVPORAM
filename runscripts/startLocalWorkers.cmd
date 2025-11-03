@echo off
setlocal enabledelayedexpansion

if "%~3"=="" (
    echo Usage: %~nx0 ^<number of workers^> ^<controller ip^> ^<controller port^>
    echo Example: %~nx0 5 127.0.0.1 12000
    exit /b 1
)

set N=%~1
set CONTROLLER_IP=%~2
set CONTROLLER_PORT=%~3
set BASE_DIR=%cd%
set BUILD_PATH=%BASE_DIR%\build\local
set LOG_DIR=!BASE_DIR!\logs

REM Create log directory if it doesn't exist
if not exist "%LOG_DIR%" (
	mkdir %LOG_DIR%
)

echo %LOG_DIR%

echo Launching %N% workers...

cd %BUILD_PATH%

set /a LAST=%N%-1

for /L %%i in (0,1,%LAST%) do (
	set WORKER_DIR=!BUILD_PATH!\worker%%i
	cd worker%%i
	echo Starting worker %%i in !WORKER_DIR!...
	start /B cmd /c smartrun.cmd worker.WorkerStartup %CONTROLLER_IP% %CONTROLLER_PORT% > %LOG_DIR%/worker%%i.log 2>&1
	cd..
)

echo All workers launched.