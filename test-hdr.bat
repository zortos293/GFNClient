@echo off
REM HDR Pipeline Quick Test Script
REM Run from root directory of GFNClient

echo ========================================
echo OpenNOW HDR Pipeline Test
echo ========================================
echo.

cd src-tauri\target\release

if not exist test-hdr.exe (
    echo ERROR: test-hdr.exe not found!
    echo Please build first: cargo build --release --bin test-hdr
    pause
    exit /b 1
)

:menu
echo.
echo Select Test Mode:
echo   1. Auto-Detect (Recommended)
echo   2. Force SDR Mode
echo   3. Force HDR Mode
echo   4. SDR/HDR Comparison
echo   5. Stress Test (5 minutes)
echo   6. Help
echo   0. Exit
echo.
set /p choice="Enter choice (0-6): "

if "%choice%"=="0" goto end
if "%choice%"=="1" goto auto
if "%choice%"=="2" goto sdr
if "%choice%"=="3" goto hdr
if "%choice%"=="4" goto compare
if "%choice%"=="5" goto stress
if "%choice%"=="6" goto help

echo Invalid choice!
goto menu

:auto
echo.
echo Running auto-detect test...
test-hdr.exe
goto after_test

:sdr
echo.
echo Running SDR mode test...
test-hdr.exe --mode sdr
goto after_test

:hdr
echo.
echo Running HDR mode test...
test-hdr.exe --mode hdr
goto after_test

:compare
echo.
echo Running comparison mode...
echo Press SPACE to toggle SDR/HDR, ESC to exit
test-hdr.exe --mode compare
goto after_test

:stress
echo.
echo Running 5-minute stress test...
test-hdr.exe --duration 300 --verbose
goto after_test

:help
echo.
echo HDR Pipeline Test Help
echo ========================================
echo.
echo Test Modes:
echo   Auto-Detect: Automatically detects and uses HDR if available
echo   SDR Mode:    Forces SDR rendering (Rec. 709)
echo   HDR Mode:    Forces HDR rendering (Rec. 2020 + PQ)
echo   Comparison:  Toggle between SDR and HDR with SPACE key
echo   Stress Test: 5-minute test with detailed logging
echo.
echo What to Look For:
echo   - Smooth animated gradients
echo   - 55+ FPS (60 is ideal)
echo   - HDR mode should be noticeably brighter/more vibrant
echo   - No flickering or artifacts
echo.
echo Performance Targets:
echo   FPS:        55+ (Excellent), 45-55 (Good), 30-45 (Acceptable)
echo   CPU Usage:  5-10%%
echo   GPU Usage:  15-30%%
echo.
pause
goto menu

:after_test
echo.
echo Test completed!
echo.
set /p again="Run another test? (y/n): "
if /i "%again%"=="y" goto menu

:end
cd ..\..\..
echo.
echo Exiting...
echo.
