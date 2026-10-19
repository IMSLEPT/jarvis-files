#!/bin/bash
# Copia Gemma nell'emulatore e prova l'AI vera (senza Gradle in esecuzione, per risparmiare RAM).
set -x
adb shell rm -rf /sdcard/JarvisReport
adb shell mkdir -p /sdcard/JarvisModel
adb push model/*.litertlm /sdcard/JarvisModel/
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.jarvis.files.LlmTest com.jarvis.files.test/androidx.test.runner.AndroidJUnitRunner > test.log 2>&1
mkdir -p results
cp test.log results/instrument.txt
adb pull /sdcard/JarvisReport results/ || true
adb logcat -d -s JarvisTest AndroidRuntime TestRunner LocalAi > results/logcat.txt || true
free -m > results/host-mem.txt
exit 0
