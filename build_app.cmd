@echo off
set JAVA_HOME=C:\PROGRA~1\JAVA\JDK-11~4.26_4
set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
cd /d E:\build_workplace\radiodroid0.96
echo Starting build with Java 11...
call gradlew.bat assembleFreeDebug --no-daemon
echo Build exit code: %ERRORLEVEL%
