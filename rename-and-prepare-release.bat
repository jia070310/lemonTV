@echo off
setlocal enabledelayedexpansion

cd /d d:\xiangmu\mytv-android-main

echo ========================================
echo 柠檬TV - Release 发布准备工具
echo ========================================
echo.

REM 设置版本号（从 build.gradle.kts 读取或手动设置）
set VERSION=1.1.7

echo 当前版本号: v%VERSION%
echo.

REM 检查 Release APK 是否存在
if not exist "app\build\outputs\apk\release\app-release.apk" (
    echo [错误] 未找到 Release APK 文件
    echo 请先运行 build-release.bat 构建 Release 版本
    pause
    exit /b 1
)

REM 创建 Release 目录
if not exist "release" mkdir release

REM 重命名并复制文件
echo 正在准备发布文件...
copy "app\build\outputs\apk\release\app-release.apk" "release\LemonTV-v%VERSION%-release.apk" >nul

echo.
echo ========================================
echo 发布文件准备完成！
echo ========================================
echo.
echo 文件位置: release\LemonTV-v%VERSION%-release.apk
echo.

REM 显示文件信息
for %%F in ("release\LemonTV-v%VERSION%-release.apk") do (
    echo 文件大小: %%~zF 字节 ^(%%~zF / 1024 / 1024 MB^)
)

echo.
echo 下一步操作:
echo 1. 访问 GitHub 仓库: https://github.com/jia070310/lemonTV
echo 2. 点击右侧 "Releases"
echo 3. 点击 "Draft a new release"
echo 4. 上传 release 目录中的 APK 文件
echo.

pause
