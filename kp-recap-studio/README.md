# KP Recap Studio — Native Android APK

A native Java/Android app designed for offline-first use. **No WebView website wrapper**, no INTERNET permission, no paid API key or required cloud service.

## Working code added through v0.4

- Purple-neon launcher icon, Home dashboard, local-only file input with Android Storage Access Framework.
- **Movie Recap**: import and preview local video, offline visual shot-change **hints** sampled from video keyframes, seek to chosen frames, scene timestamp notes, editable manual transcript and **basic extractive** (not generative) script, rough-timed SRT subtitle export.
- **Scene video output**: MP4 **silent montage** of short scenes using Android MediaExtractor + MediaMuxer (stream copy; no added captions/voice). H.264/H.265 source only, cuts are sync-frame-aligned, so timestamps are approximate. Tested by CI compile; needs device/runtime QA.
- **Audiobook**: import TXT or paste long text, choose installed **offline** Android TTS voice (Burmese if actually present on the device), read aloud, generate sequential chunks and merge into uncompressed WAV. Saved valid chunks are reused after interruption when the same text and voice are selected. App-private cache can be cleared explicitly.
- **PDF Reader**: on-device PDF page rendering + text extraction for selectable text PDFs using PdfBox-Android. The extracted text becomes an audiobook draft. **Scanned PDF OCR not implemented.**
- **Voice Lab**: record mic sample or import/listen to local authorized audio samples. This is **not** an AI voice-cloning system. No voice model is included.
- Source code builds as Android 15-compatible native APK; target API 35, minimum API 26. Tested Android platform compilation via GitHub Actions; runtime on vivo V27 not yet verified.

## Major requested features NOT DONE

- Offline neural Burmese **voice cloning** from user-provided voice samples; needs verified permissively licensed model, Android inference engine, segmentation, quality/performance evaluation, and user consent controls.
- Video speech transcription and AI comprehension of the movie; scene finder is visual sampling only.
- One-click final Movie Recap with generated script, selected clone voice, edited original footage, subtitles burned in, audio/muxed MP4.
- Robust multi-hour export with device-level battery/heat/storage stress testing. Standard WAV has ~4GB container size limit.
- Testing/install verification on vivo V27.

**Do not present this branch as a final complete app and do not distribute its debug APK as a feature-complete voice-cloning app.**

## Build

Requirements: JDK 17 and Android SDK 35.

```sh
cd kp-recap-studio
gradle assembleDebug
```

Or inspect GitHub Actions `Build KP Recap Studio Native Android APK` for current branch/PR. CI output is a debug build artifact, not a release.

- Dependency: `com.tom-roush:pdfbox-android:2.0.27.0` (Apache License 2.0), bundled at build time for offline text extraction.
- Video processing uses Android platform `MediaMetadataRetriever`, `MediaExtractor` and `MediaMuxer`.
- Local TTS voices depend on what is installed on the phone; network-required voices are rejected.
- This app neither uses nor modifies the separate KP-AI or KP-Translator repositories.
