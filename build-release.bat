@echo off
cd /d d:\xiangmu\mytv-android-main
echo Building Release APK...
call gradlew.bat assembleRelease
echo.
echo Build completed!
echo APK location: app\build\outputs\apk\release\
pause
