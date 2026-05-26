@echo off
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
cd /d E:\build_workplace\radiodroid0.96
call gradlew.bat assembleFreeDebug
pause
