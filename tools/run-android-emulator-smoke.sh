#!/usr/bin/env bash
set -euo pipefail

echo "[KP Recap] Installing the app and instrumentation APK"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

echo "[KP Recap] Running Burmese ONNX TTS + Media3 MP4 instrumentation"
adb shell am instrument -w -r \
  -e class com.kp.movierecap.NarratedRecapInstrumentedTest,com.kp.movierecap.OneClickRecapUiInstrumentedTest \
  com.kp.movierecap.test/androidx.test.runner.AndroidJUnitRunner \
  | tee /tmp/kp-emulator-test.log

echo "[KP Recap] Checking instrumentation assertion summary"
grep -E 'OK \(4 tests\)' /tmp/kp-emulator-test.log
echo "[KP Recap] Pulling proof MP4 and WAV from Android private test cache"
adb exec-out run-as com.kp.movierecap cat cache/kp_ci_narrated.mp4 > kp-emulator-narrated-proof.mp4
adb exec-out run-as com.kp.movierecap cat cache/kp_burmese_voice.wav > kp-emulator-burmese-proof.wav
test -s kp-emulator-narrated-proof.mp4
test -s kp-emulator-burmese-proof.wav
if adb shell run-as com.kp.movierecap ls cache/kp_friendly_studio_ui.png >/dev/null 2>&1; then
  adb exec-out run-as com.kp.movierecap cat cache/kp_friendly_studio_ui.png > kp-friendly-ui-screen.png
  test -s kp-friendly-ui-screen.png
fi
echo "[KP Recap] PASS: emulator produced and validated narrated MP4"
