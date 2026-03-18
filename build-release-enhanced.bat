@echo off
cd /d d:\xiangmu\mytv-android-main

echo ========================================
echo 柠檬TV - Release 构建工具（增强版）
echo ========================================
echo.

REM 关闭可能占用文件的进程
echo [1/4] 检查并关闭占用的进程...
taskkill /F /IM explorer.exe >nul 2>&1
timeout /t 2 /nobreak >nul
start explorer.exe

echo [2/4] 清理旧的构建文件...
REM 使用 rmdir 强制删除
rmdir /s /q "app\build\outputs\apk\release" 2>nul
rmdir /s /q "app\build\intermediates" 2>nul
timeout /t 1 /nobreak >nul

echo [3/4] 开始构建 Release APK...
echo.
call gradlew.bat assembleRelease

echo.
echo [4/4] 构建完成！
echo ========================================

REM 检查 APK 是否生成
if exist "app\build\outputs\apk\release\app-release.apk" (
    echo ✓ Release APK 已生成
    echo 文件位置: app\build\outputs\apk\release\app-release.apk
    for %%F in ("app\build\outputs\apk\release\app-release.apk") do (
        set /a "sizeMB=%%~zF / 1024 / 1024"
    )
    echo 文件大小: !sizeMB! MB
) else (
    echo ✗ 构建失败，未找到 APK 文件
)

echo.
pause
