@echo off
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
cd /d E:\build_workplace\radiodroid0.96
echo Building with Java 8...
call gradlew.bat assembleFreeDebug --no-daemon > build_out.log 2>&1
echo DONE:%ERRORLEVEL% >> build_out.log
