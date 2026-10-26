#!/bin/bash
# Eseguito dentro l'emulatore: test strumentati + raccolta rapporto e screenshot.
set -x
adb shell rm -rf /sdcard/JarvisReport
adb shell settings put global window_animation_scale 0
# niente finestre "app non risponde" del launcher dell'emulatore sopra gli screenshot
adb shell settings put global hide_error_dialogs 1
./gradlew connectedDebugAndroidTest --continue > test.log 2>&1
test_status=$?
echo "$test_status" > test-exit-code
mkdir -p results
adb pull /sdcard/JarvisReport results/ || true
adb logcat -d -s JarvisTest AndroidRuntime TestRunner > results/logcat.txt || true
cp -r app/build/outputs/androidTest-results/connected results/junit 2>/dev/null || true
cp -r app/build/reports/androidTests/connected results/html 2>/dev/null || true
tail -200 test.log > results/gradle-tail.txt
exit "$test_status"

