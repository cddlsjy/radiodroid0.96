@echo off
chcp 65001
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
cd /d E:\build_workplace\radiodroid0.96
echo Starting Gradle build... > build_log.txt
echo JAVA_HOME=%JAVA_HOME% >> build_log.txt
"%JAVA_HOME%\bin\java.exe" -version >> build_log.txt 2>&1
time /t >> build_log.txt
call gradlew.bat assembleFreeDebug --no-daemon >> build_log.txt 2>&1
echo Build finished with code: %ERRORLEVEL% >> build_log.txt
time /t >> build_log.txt
type build_log.txt
