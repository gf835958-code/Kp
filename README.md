# KP Movie Recap Studio — verified state (2026-11-15)

## User's actual acceptance criterion (NOT YET MET)
On the user's vivo V27, select one movie video, tap **Generate Recap** once, and receive a playable MP4 with an **accurate movie story script, Burmese voice narration and correctly synchronized Burmese subtitles**. No manual scene descriptions, subscriptions, API keys or repeated large test downloads.

Do **not** call this feature complete until an end-to-end physical Android test passes. Existing ML Kit image labels alone **cannot** understand a movie's plot, dialogue, cast or causal story.

## What's actually implemented
- **Native scene frame detection:** `NativeSceneScanner.java` avoids 12-second WebView seek timeouts on long videos. Tested with synthetic planning tests; long movie runtime has not been validated.
- **Local image labeling:** ML Kit produces coarse visual observations. This is not a true narrative recap.
- **Burmese TTS engine:** `BurmeseNarration.java` uses a bundled noncommercial Burmese MMS ONNX model and the sherpa-onnx Android runtime. It produces scene-aligned PCM WAV; unusually long speech is retried, and fails rather than silently clipping words.
- **Rendered video:** `CaptionedVideoExporter.java` uses Media3 to burn captions into selected scene video and mux a separate narrated audio track.
- **One-click UI:** `index.html` dispatches scan -> scene caption plan -> TTS + MP4 generation; save to Movies/KP Movie Recap. The resulting text is only object-level *visual descriptions*.

## Real Android emulator evidence
- **2026-11-15, run 43732731869: PASS.** An Android 15 x86_64 emulator installed the real APK and test APK. The instrumentation test ran bundled Burmese ONNX speech synthesis on-device, created a nonempty WAV, rendered a 10-second H.264 MP4 with Media3, and reopened the MP4 to verify both audio and video tracks. Test name: `NarratedRecapInstrumentedTest.generatesRealAudioVideoMp4OnAndroid`. It did **not** verify real movie story understanding, pronunciation quality or physical phone performance.
- CI details: https://github.com/gf835958-code/Kp/actions/runs/43732731869
- The newest workflow `.github/workflows/android-emulator-voice-test.yml` can retain the generated miniature MP4 and WAV as artifacts for checking real output and renders. No movie is uploaded.
- Build-only v0.9 run 43543283126: PASS, but build success alone is not proof of runtime functionality.

## Known blockers before full one-click release
1. **Grounded movie storyline:** Need scene-aware visual language understanding and/or speech recognition + dialogue transcription with a local reasoning/summarization model. Current 8–16 ML Kit object labels are insufficient. Avoid inventing plot details.
2. **Burmese speech quality and subtitle timing:** Currently per-scene visual captions are spoken; not word-level voice-aligned captions. Investigate segmentation, normal spoken narration, timing, voice quality and fonts.
3. **Long 30-minute movie on physical vivo V27:** File import, native scanning, TTS memory/performance and final Media3 export must succeed without timeout or out-of-memory.
4. **Licensing:** Burmese MMS model derived from Meta MMS is CC BY-NC 4.0, suitable only for allowed noncommercial uses with attribution; don't claim unrestricted commercial rights.

## Development and tests
- Android source: `app/src/main/java/com/kp/movierecap`
- Node static test: `node tools/verify-oneclick.cjs`
- Emulator integration test: `app/src/androidTest/java/com/kp/movierecap/NarratedRecapInstrumentedTest.java`
- Android emulator workflow: `.github/workflows/android-emulator-voice-test.yml`
- Real TTS model Linux inference: `.github/workflows/verify-burmese-tts.yml`
- Model and sherpa-onnx AAR are downloaded from pinned sources on CI and their SHA256 are checked before compilation; users do not need to download these independently.

**Policy:** Don't ask the user to waste phone data on experimental APKs unless the actual requested features are tested and clearly described. Continue improving the existing `kp-movie-recap-app` branch; don't create a new repository.
