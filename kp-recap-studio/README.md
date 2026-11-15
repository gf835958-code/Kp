# KP Recap Studio — Native Android (v0.6.1 experimental branch)

**Status: experimental Android APK, not a completed one-click Movie Recap / Burmese AI Voice Cloning product.**
The user requested to receive ONLY a feature-complete APK. Do not market or link a debug build as the final deliverable.

## Offline native code currently implemented

- Native Java Android app, custom logo, dark purple dashboard; does not embed any website or require an Internet permission.
- Open and preview user-authorized video on device.
- **New local speech transcription**: Decode an authorized video's audio track with Android MediaExtractor/MediaCodec into 16 kHz mono PCM WAV, then transcribe it using bundled multilingual Whisper Tiny (MIT model+whisper.cpp-based MIT Android AAR). This is a **spoken-language** transcript, not visual movie understanding. It is available from the Movie Recap screen and may be used to bootstrap a script when no transcript was entered.
- **Model is bundled at APK build time**, not downloaded by the app: GitHub Actions fetches upstream `ggml-tiny.bin` (77,691,713 bytes), validates pinned SHA-256 `be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21`, bundles it in `assets/models` and checks the APK contains an arm64 native Whisper runtime. The app has no INTERNET permission.
- **One-tap draft pipeline** from authorized video with soundtrack: extract speech → Whisper Burmese transcript → simple extractive short recap → installed offline system TTS voice → WAV/AAC → audio/video mux → proportional estimated caption burn-in. This pipeline is implemented but **needs runtime testing**, and the script comes from spoken dialogue rather than scene-level AI understanding.

- Sample keyframes and detect rough **visual shot-change hints** using difference of scaled frames; show scene cards and seek to timestamps.
- Create scene-note template, manually paste/edit transcript, basic extractive short-script generation (**NOT true AI video understanding**).
- Export approximate-timing SRT captions.
- Export short silent H.264/H.265 MP4 scene montages (sync-frame-aligned with approximate cuts).
- Import authorized AAC/M4A narration or use the generated audiobook WAV, encode 16-bit PCM WAV to AAC using Android MediaCodec, mux audio with video into MP4 using MediaMuxer. Original audio is discarded in narration mux output; it is not mixed.
- Burn rough-timed captions (based on 5-second script chunks) into MP4 via AndroidX Media3 Transformer, drawing native text onto decoded frames before re-encoding. If available, use the last narrated video as input, otherwise last montage or original video. **Caption timing is NOT auto-aligned to speech.**
- Read text-based PDFs locally, extract their selectable text (PdfBox-Android), and send content to an audiobook text editor. Does not OCR scanned PDFs.
- Import long TXT and use already-installed local/offline TTS voices where available; speak and generate chunked WAV files; resume from cached successfully generated portions after interruption using text + voice-specific cache key.
- **v0.6.1 experimental:** Long-book M4A export now streams cached PCM WAV chunks through one continuous Android AAC encoder. It does not assemble a giant intermediate WAV; this removes the single classic RIFF ~4 GB **intermediate-file limitation** for M4A exports. WAV export itself still has the 4 GB container limit. The phone's free storage, uninterrupted processing, codec/device capabilities, application lifecycle, and MediaMuxer limitations can still prevent very large exports. This path is **compiled but not yet runtime verified** on vivo V27.
- New per-utterance TTS job tokens ignore delayed callbacks from an older cancelled/finished job, preventing accidental double advancement of audio chunks.
- Microphone recording and import/playback of consented voice samples.

## Explicitly missing from the user's final required app

- Real **Burmese AI Voice Cloning** from a reference voice. Importing a reference audio sample is NOT voice cloning, and the Android system TTS voice is NOT a clone.
- Offline video ASR **quality/runtime verification** on vivo V27 and **AI semantic understanding of video scenes**; Whisper is only a speech recognizer, not a vision-language model.
- Automatically understanding the entire film story from sight and sound; extractive speech summary is not a semantic movie recap.
- Fully reliable one-click movie-to-recap with *voice cloning* and **word-aligned** subtitles (the implemented pipeline uses approximate durations and system TTS).
- Tested/benchmark validated multi-hour Burmese narration on vivo V27 hardware.
- Android emulator and physical-device runtime QA for MediaMuxer/Media3 effects and TTS codecs.
- Store-ready signing, model redistribution rights and release quality guarantees.

## Voice cloning model evaluation

- Candidate: https://huggingface.co/freococo/F5-Myanmar-TTS (Burmese, F5-TTS v2, ~674MB weights, CC-BY-NC-4.0).
- The documented Python inference is NOT an Android runtime implementation.
- General F5-TTS → ONNX export code exists (https://github.com/DakeQQ/F5-TTS-ONNX), but its compatibility with this Burmese checkpoint, preprocessing/vocoder setup, ARM64 performance and device memory constraints have **not been verified**.
- ZipVoice and PocketTTS Android-compatible options focus on other supported languages, not verified Burmese voice cloning.
- Do not bundle, redistribute or claim working support for a checkpoint without checking its license and a successful Android inference test.
- Only clone the user's own voice or a voice with the speaker's authorization.

## Build

- JDK 17; AGP 8.10.1; Gradle 8.11.1; Kotlin 2.1.20; compile SDK 36; target API 35; minimum API 26.
- AndroidX Media3 1.11.1; PdfBox-Android 2.0.27.0; Whisper Android AAR 1.0.0 (MIT), kotlinx.coroutines-android 1.10.2; JUnit 4.13.2.
- Local build: first download [official multilingual Whisper tiny model](https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-tiny.bin) into `app/src/main/assets/models/ggml-tiny.bin` and verify SHA-256 above; then run `gradle testDebugUnitTest assembleDebug` in `kp-recap-studio/`. GitHub Actions automates this with a verified download/cache.
- Debug artifact is generated on commits, intended **for testing only**, not the final user-approved release.
- GitHub CI validates native Java/Kotlin compile, JUnit tests and model-in-APK verification. **CI success is not a runtime guarantee** for any Android codec/export or Burmese voice cloning feature.

This project is separate from `KP-AI` and `KP-Translator`.

## Model provenance & licensing (critical)

| Engine | Purpose | Language | Availability | License |
|---|---|---|---|---|
| ggml Whisper Tiny multilingual | Video speech-to-text | Multilingual incl. Burmese (quality unverified) | Bundled, SHA-256 verified | MIT |
| Android system TTS | Text-to-audio WAV | Installed offline voices only | Depends on device | Android/system voice terms |
| freococo/F5-Myanmar-TTS | Burmese reference-based voice cloning candidate | Burmese | **Not integrated**: Python checkpoint, no validated ARM64 inference | CC BY-NC 4.0 (noncommercial) |
| VoxCPM2 | Burmese capable multi-language neural voice cloning candidate | Burmese + many languages | **Not integrated**: heavy 2B mobile runtime unvalidated | Apache 2.0 upstream |

Do not distribute the finished project as if a reference audio produces a cloned voice until the real model has been integrated and hardware-tested. Always require voice-owner authorization.
