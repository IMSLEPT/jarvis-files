#!/bin/bash
# Copia Gemma nell'emulatore e prova l'AI vera.
set -x
adb shell rm -rf /sdcard/JarvisReport
adb shell mkdir -p /sdcard/JarvisModel
adb push model/*.litertlm /sdcard/JarvisModel/
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jarvis.files.LlmTest > test.log 2>&1
mkdir -p results
adb pull /sdcard/JarvisReport results/ || true
adb logcat -d -s JarvisTest AndroidRuntime TestRunner LocalAi litert > results/logcat.txt || true
cp -r app/build/outputs/androidTest-results/connected results/junit 2>/dev/null || true
tail -200 test.log > results/gradle-tail.txt
exit 0
