# KP Recap Studio — Native Android (v0.5 development branch)

**Status: experimental Android APK, not a completed one-click Movie Recap / Burmese AI Voice Cloning product.**
The user requested to receive ONLY a feature-complete APK. Do not market or link a debug build as the final deliverable.

## Offline native code currently implemented

- Native Java Android app, custom logo, dark purple dashboard; does not embed any website or require an Internet permission.
- Open and preview user-authorized video on device.
- Sample keyframes and detect rough **visual shot-change hints** using difference of scaled frames; show scene cards and seek to timestamps.
- Create scene-note template, manually paste/edit transcript, basic extractive short-script generation (**NOT true AI video understanding**).
- Export approximate-timing SRT captions.
- Export short silent H.264/H.265 MP4 scene montages (sync-frame-aligned with approximate cuts).
- Import authorized AAC/M4A narration or use the generated audiobook WAV, encode 16-bit PCM WAV to AAC using Android MediaCodec, mux audio with video into MP4 using MediaMuxer. Original audio is discarded in narration mux output; it is not mixed.
- Burn rough-timed captions (based on 5-second script chunks) into MP4 via AndroidX Media3 Transformer, drawing native text onto decoded frames before re-encoding. If available, use the last narrated video as input, otherwise last montage or original video. **Caption timing is NOT auto-aligned to speech.**
- Read text-based PDFs locally, extract their selectable text (PdfBox-Android), and send content to an audiobook text editor. Does not OCR scanned PDFs.
- Import long TXT and use already-installed local/offline TTS voices where available; speak and generate chunked WAV files; resume from cached successfully generated portions after interruption using text + voice-specific cache key. Standard RIFF WAV format has a ~4GB size limit.
- Microphone recording and import/playback of consented voice samples.

## Explicitly missing from the user's final required app

- Real **Burmese AI Voice Cloning** from a reference voice. Importing a reference audio sample is NOT voice cloning, and the Android system TTS voice is NOT a clone.
- Offline video audio transcription (ASR) and AI semantic understanding of video scenes.
- Automatic AI Movie Recap script generation without transcript or manual review.
- Fully automatic one-click chain that creates and voiceovers video with correctly time-aligned subtitles, despite separate functioning building blocks.
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

- JDK 17; AGP 8.10.1; Gradle 8.11.1; compile SDK 36; target API 35; minimum API 26.
- AndroidX Media3 1.11.1; PdfBox-Android 2.0.27.0.
- Run `gradle assembleDebug` in `kp-recap-studio/` or use GitHub Actions.
- Debug artifact is generated on commits, intended **for testing only**, not the final user-approved release.
- This repository's v0.5 CI has passed Java compilation and packaging, which does not prove media export works on actual vivo V27 hardware.

This project is separate from `KP-AI` and `KP-Translator`.
