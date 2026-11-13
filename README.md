# KP Movie Recap Studio v0.3 — Android App

Standalone source for a **free, offline-first** Android 8.0+ APK (tested build target: Android 15). Purple/cyan WebView UI and native Android processing bridges.

## Features already implemented in source

- **Built-in 15s offline demonstration video** generated from 5 color scenes at GitHub build time: tap `Demo Video` and it scans automatically, fills an explicitly prewritten sample script, and enables MP4 and SRT export. This is sample narration only, not AI interpretation.
- **Auto Scan + Highlight MP4** button offers a one-tap scene scan + opens the native Save As screen for the MP4. Clip duration is configurable; source proportions are preserved.
- Android Storage Access Framework video import (`video/*`), thumbnail-based scene sampling, scene notes.
- Editable Myanmar/English script draft based on notes. No claim of full-movie AI interpretation.
- Device TTS voice preview and experimental TTS audio file export (language voice must be installed, engine-specific output format).
- Script `.txt` and scene-time draft subtitle `.srt` export through Android Save As picker.
- **Native Scene Highlights MP4** export: uses Android MediaExtractor/MediaMuxer to concatenate scene-adjacent snippets from an MP4 input, preserving supported original streams. No media is uploaded. Supports most phone-compatible H.264/AAC MP4 sources, without transcoding; unsupported codecs can fail. Does not overlay narration or subtitles.
- No Internet, storage, or microphone permissions required. Video uses Android file chooser / SAF.

## Limitations (important)

**This is NOT yet a full-auto AI movie recap creator.** It does NOT comprehend a feature-length movie, write factual plot summaries automatically, provide synced voiceover or burn subtitles into MP4. This app offers a video scene/notes workflow and a rough-cut highlight exporter. Subtitle times are scene timestamps rather than voice alignment. Device-level TTS Myanmar support varies. 9:16 selector is a future goal and does not transform exported MP4 proportions.

## Free GitHub Actions APK build

1. Create a fresh repository (recommended name: `KP-Movie-Recap-Studio`) and upload the contents **as files at the repository root**, keeping all folders.
2. Push to `main` (or run `Build KP Movie Recap APK` in GitHub Actions). Build environment uses Gradle 8.10.2, Android Gradle Plugin 8.7.3, compileSdk 35, JDK 17.
3. On successful GitHub Actions run, tap the `KP-Movie-Recap-v0.3-APK` artifact; extract it; install `app-debug.apk` on Android. GitHub stores Actions artifacts compressed even when the contents are APKs.
4. If installed earlier app with a different certificate, uninstall older test app before installing. This will erase locally stored app data.

## Security and data use

No INTERNET permission, and no movie is uploaded. Android SAF grants only selected documents. The WebView loads only bundled local assets. `addJavascriptInterface` is exposed only to the bundled UI, with network requests blocked. Do not include movies without proper ownership/permission.

## Code map

- `app/src/main/java/com/kp/movierecap/MainActivity.java`: Android WebView, TTS, file picker / Save As integration, export pipeline.
- `app/src/main/java/com/kp/movierecap/VideoHighlightsExporter.java`: Android MediaExtractor/MediaMuxer implementation.
- `app/src/main/assets/index.html`: UI and client-side frame sampler/script/SRT generator.
- `.github/workflows/build-android.yml`: build and upload APK artifact.
