#!/usr/bin/env bash
set -euo pipefail

echo "[KP Recap] Installing the app and instrumentation APK"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

echo "[KP Recap] Running Burmese ONNX TTS + Media3 MP4 instrumentation"
adb shell am instrument -w -r \
  -e class com.kp.movierecap.NarratedRecapInstrumentedTest \
  com.kp.movierecap.test/androidx.test.runner.AndroidJUnitRunner \
  | tee /tmp/kp-emulator-test.log

echo "[KP Recap] Checking instrumentation assertion summary"
grep -E 'OK \(1 test\)' /tmp/kp-emulator-test.log
echo "[KP Recap] PASS: emulator produced and validated narrated MP4"
