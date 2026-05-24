$apk = Get-ChildItem E:\build_workplace\radiodroid0.96\app\build -Recurse -Include *.apk | Select-Object -First 1 -ExpandProperty FullName
Write-Output "APK path: $apk"
adb -s 192.168.5.14:5555 install -r "$apk" 2>&1
Write-Output "Install exit code: $LASTEXITCODE"
