@echo off
chcp 65001 >nul
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo 请以管理员身份运行！
    pause
    exit /b 1
)

set "TEMP_DIR=%TEMP%\audio_driver"
if not exist "%TEMP_DIR%" mkdir "%TEMP_DIR%"
set "URL=https://download.vb-audio.com/Download_CABLE/VBCable_Setup.exe"
set "SETUP=%TEMP_DIR%\VBCable_Setup.exe"

powershell -Command "(New-Object System.Net.WebClient).DownloadFile('%URL%','%SETUP%')"
if not exist "%SETUP%" (
    echo 驱动下载失败
    pause
    exit /b 1
)

"%SETUP%" /verysilent /norestart
rd /s /q "%TEMP_DIR%" >nul 2>&1
echo 驱动安装完成
exit