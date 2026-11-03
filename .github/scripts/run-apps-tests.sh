#!/usr/bin/env bash
adb shell rm -rf /sdcard/JarvisReport
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jarvis.files.OrganizeRegressionTest,com.jarvis.files.AppInsightsTest,com.jarvis.files.AppsFlowTest,com.jarvis.files.ConversationContextTest,com.jarvis.files.CommandFlowTest > test.log 2>&1
test_status=$?
mkdir -p results
adb pull /sdcard/JarvisReport results/ || true
adb logcat -d -s JarvisTest AndroidRuntime TestRunner > results/logcat.txt || true
cp -r app/build/outputs/androidTest-results/connected results/junit 2>/dev/null || true
tail -150 test.log > results/gradle-tail.txt
exit "$test_status"
