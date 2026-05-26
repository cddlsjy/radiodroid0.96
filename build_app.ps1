Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue
$env:ANDROID_AAPT2 = "C:\Users\M8400\AppData\Local\Android\Sdk\build-tools\36.1.0\aapt2.exe"
& "C:\Users\M8400\.gradle\wrapper\dists\gradle-8.12-bin\7vg77h8jomrdpgh5hmwhreghw\gradle-8.12\bin\gradle.bat" --no-daemon assembleFreeDebug
