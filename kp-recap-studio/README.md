# KP Recap Studio — Android Native APK

This is a **native Android app** built in Java. No web view, website, server or internet permission.

Functions:
- Import video for local preview, write transcript, create a clearly-labelled basic extractive recap and SRT.
- Import TXT, manually edit book text, use **installed device offline TTS voices** for speech and long WAV generation in sequential segments.
- Import/play/record reference voice samples; *voice cloning is NOT installed or functional*. Importing samples does not change TTS voice.
- Import/preview local PDF pages on-device (PDF text extraction/TTS not included).
- Output files using Android Storage Access Framework; inputs remain on device.

**Important:** This APK is an initial native Android app, NOT an offline AI voice clone. A licensed, Android-compatible Burmese voice-cloning inference engine, its model weights, and on-device performance evaluation are separate future work. Availability of offline Burmese TTS depends on the system voices installed by the user. No claims that multi-hour audio generation is verified on vivo V27.

Build: Run 'gradle assembleDebug' in this directory with JDK 17 and Android SDK 35, or use the GitHub Actions workflow on the branch. APK: app/build/outputs/apk/debug/app-debug.apk.
