$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Write-Output "Starting build with Android Studio JBR..."
& "E:\build_workplace\radiodroid0.96\gradlew.bat" assembleFreeDebug 2>&1
Write-Output "Build exit code: $LASTEXITCODE"
