@echo off
setlocal enabledelayedexpansion
if "%AARCH%"=="" set "AARCH=amd64"
set "TARGET_DIR=src\main\resources\win-%AARCH%"

cmake -Bbuild -DCMAKE_INSTALL_PREFIX="%TARGET_DIR%"
cmake --build build --config Release -j %NUMBER_OF_PROCESSORS%
cmake --install build
exit /b 0

