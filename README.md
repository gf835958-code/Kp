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

## Subtitle sizing visual regression checked (2026-11-15)
- Android emulator proof run **43733937452** passed end-to-end. The test app generated Burmese speech and a 10-second H.264 + AAC MP4 and uploaded small proof files.
- Proof MP4: **128×72**, 10.0 seconds, AAC mono 16 kHz and H.264 video. FFmpeg found **max_volume -14.2 dB** (non-silent speech).
- The previous 128×72 MP4 had Burmese caption text visibly cropped. `CaptionedVideoExporter` was updated to scale font size using the video dimensions and wrap Myanmar grapheme clusters, producing a visible, fully contained caption in the same synthetic frame.
- Proof artifact: https://github.com/gf835958-code/Kp/actions/runs/43733937452 (artifact `KP-Emulator-Burmese-Voice-Caption-Proof`).
- This is not a feature-complete release: synthetic 10-second scene-caption proof does NOT mean a 30-minute movie can be summarized accurately. Local plot understanding and vivo V27 testing are still blockers.

## 30-minute Native Scan Regression (2026-11-15)
- Emulator run **43735880389**: **PASS** for both Android instrumented tests. A locally generated **1802-second H.264 video** was scanned using `NativeSceneScanner.sample()` without repeatedly seeking HTML WebView. The native scanner returned video duration, sorted scene timestamps, and appropriately encoded frame thumbnails. The same test suite also generated a short MP4 containing Burmese ONNX speech and Media3 burned-in captions, and verified audio and video tracks. [Verified GitHub Actions run](https://github.com/gf835958-code/Kp/actions/runs/43735880389).
- **The synthetic 1802-second test does NOT establish reliability on a 438.9 MB real movie** or on vivo V27. Scan time budget (<120 seconds) is separately asserted in the updated `NarratedRecapInstrumentedTest`.
- A true movie plot synopsis from visual context and spoken dialogue is **still not implemented**. Do not call the APK full one-click recap or force another large mobile download.

## Experimental original-audio dialogue analysis (v0.10)
- Added bounded Android `MediaExtractor` + `MediaCodec` PCM window decoder (`MovieAudioSampler.java`), extracting only 12 seconds at a time to avoid loading a 30-minute film into RAM.
- Added multilingual Whisper tiny int8 offline recognizer (`DialogueTranscriber.java`), using the existing sherpa-onnx Android runtime. Three distributed movie excerpts are transcribed automatically after visual scan; their **original-language** words are attached to scene evidence in Advanced Details and TXT script. The model is pinned to the Hugging Face revision `65176e2deb88badc814a94058666cadccc29b61c` and downloaded only by CI for build.
- Spoken dialogue is only inserted into *Burmese voice captions* when the detector indicates Burmese speech, not for English or other languages. This avoids pretending that English text has been translated into Burmese; automated Burmese translation and true plot reasoning remain TODO.
- Added Android emulator instrumentation test with a synthetic movie that contains actual spoken dialogue; it must recognize real content, not merely compile ASR code.
- **Not finished:** Three excerpts are insufficient for a faithful full-film plot. Dialogues are an evidence source, NOT a summary. Real local plot inference, scene-level speech coverage, multi-language translation into Burmese, accurate synchronization, long-film runtime and vivo V27 verification remain blockers. Do not advertise as complete or force mobile downloads.

## 2026-11-15: Real offline dialogue ASR Android test
- CI run **43765728147** passed emulator instrumentation. The movie fixture contains synthetic spoken English: "The secret map is inside the red house. We must find the map." Whisper tiny decoded the source soundtrack on Android. The strengthened test asserted that the result contained "map" or "house", not merely arbitrary nonempty text. Burmese narration, MP4 tracks, and 30-minute native scene scan tests passed as well. https://github.com/gf835958-code/Kp/actions/runs/43765728147
- The pinned multilingual Whisper INT8 encoder, decoder, and tokens SHA-256 checks are enforced by tools/fetch-asr-model.sh.
- This validates short excerpts only, not multilingual-to-Burmese translation, accurate full-movie plot understanding, long-movie end-to-end export, or vivo V27. No full-featured APK is approved for distribution.

## 2026-11-19: Offline spoken dialogue coverage and reliability update

- Replaced three manually chosen scene timestamps with up to **six distributed, automatic 12-second audio samples** for a roughly 30-minute movie (up to 72 seconds of direct dialogue evidence). The entire movie stays local; samples are decoded one at a time rather than buffered in RAM.
- Original spoken-language transcripts now retain individual timestamps and do not overwrite earlier excerpts from the same scene. **English and other non-Burmese dialogue is NOT falsely represented as Burmese translation.**
- Whisper now skips near-silent decoded PCM samples and suppresses repeated normalized lines. A new Android emulator test uses real AAC silence to verify it doesn't fabricate text.
- The one-click UI receives native per-window ASR progress updates so the user can see 0/N through N/N rather than a frozen screen.
- JavaScript smoke tests cover the distributed sampling algorithm, deduplication/silence guard presence, and native progress bridge. Android build and instrumentation: [latest runs](https://github.com/gf835958-code/Kp/actions).
- **Still missing**: trustworthy full-film plot understanding, arbitrary source-language to Burmese offline translation, and vivo V27 full-video end-to-end verification. A 6×12-second sample cannot summarize 30 minutes accurately by itself. Do not call this a full automated story recap or send the user experimental 100+ MB APKs repeatedly.

## Verified 2026-11-19 dialogue regression results
- **Android emulator runtime PASS (3 instrumented tests):** https://github.com/gf835958-code/Kp/actions/runs/44577266992 . This validates 1,802-second native scene scanning without WebView seeks, offline Burmese WAV speech + Media3 captioned MP4 with audio/video tracks, and actual decoded *silent AAC MP4* not generating fake Whisper dialogue.
- **Latest native bridge / UI progress + 6-window selection JS and Java APK build PASS:** https://github.com/gf835958-code/Kp/actions/runs/44577436324 (source includes progress callbacks; Android emulator tests were run against a preceding compatible commit). The UI itself has **not** been end-to-end exercised by tapping Generate on vivo V27.
- The compiled APK remains a 190 MB-class **experimental test build** and must not be presented as the finished one-click story-recap release. Still lacks actual accurate whole-film plot reasoning and offline all-language → Burmese translation.

## 2026-11-19: Actual one-click UI end-to-end emulator verification
- **PASS — 4 Android tests**, GitHub Actions [run 44579700457](https://github.com/gf835958-code/Kp/actions/runs/44579700457): real Android WebView launches, the test sets a real synthetic H264/AAC source through the native video field, presses the actual **Generate Video — One Click** button once, and waits for the native result callback and a nonempty MP4 in `Movies/KP Movie Recap`. The workflow also verifies Burmese ONNX voice generation, captioned Media3 MP4 with audio/video tracks, 1802-second native scene scan, and no Whisper hallucination from truly silent AAC video.
- The source has since added `briefGroundedNarration()` to keep spoken Burmese scene captions short enough for scene segments and avoid pretending untranslated English dialogue is Burmese. This produces **visual descriptions, not a coherent full-movie story**.
- A passing emulator one-click test is **not a physical vivo V27 guarantee**. No 438.9MB real-movie end-to-end test has passed. User's acceptance criteria remain unmet: accurate full-film story reasoning, multilingual translation to Burmese, and reliable long-video output.
- **Distribution policy:** Don't push another ~190MB experimental APK to the user's mobile data plan as a finished result. All build, test, and proof artifacts stay on GitHub until a full release is ready.

## 2026-11-21 — v0.11 friendly Burmese UI and real render-progress milestone
- Rebuilt the app's Android WebView interface as an accessible, responsive **soft-light Burmese studio** with one obvious flow: **၁ Video ရွေး → ၂ ဖန်တီး → ၃ MP4 ကြည့်**.
- The important Generate action appears directly below the video picker (above the preview). Advanced scene settings, transcript evidence and TXT/SRT options remain available behind **အသေးစိတ်ကြည့်ရန်**, not deleted.
- Included meaningful on-screen status for import, scanning, real speech-transcription counts, media rendering and completion. MP4 progress comes from **Media3 Transformer.getProgress**, not a synthetic timer. A scan ending no longer claims the overall one-click export has reached 100%.
- After confirmed MediaStore save, a dedicated result card appears with **ဖွင့်ကြည့်မယ်** to open only the last MP4 created by the app via an Android native content URI. No externally supplied URL or path is accepted.
- UI color/system bars use a consistent light theme; Burmese readable typography, 47px+ buttons, strong focus rings, simple three-step onboarding. Added UI static tests, Android emulator visual screenshot capture, and nonblank screenshot verification.
- The prior v0.11 code build with one-click UI **passed** https://github.com/gf835958-code/Kp/actions/runs/44916604308 . More recent UI progress and screenshot changes require their own GitHub build + emulator QA; don't infer success from an older run.
- **This still is not an accurate full movie recap release.** Local image labels and sparse Whisper excerpts lack real plot reasoning and multilingual Myanmar translation; the user also requires successful on-device testing with a full-length movie on vivo V27.
- Complete requirements and no-compromise release gates: [docs/KP-MOVIE-RECAP-REQUIREMENTS.md](docs/KP-MOVIE-RECAP-REQUIREMENTS.md). Do not call an experimental APK "final".

## 2026-11-21/22 — real WAV verification, grounded speech and export integrity
- GitHub Android Emulator run [44937283672](https://github.com/gf835958-code/Kp/actions/runs/44937283672) **PASS**: instrumented tests, H.264/AAC MP4, actual 16 kHz mono 16-bit PCM RIFF WAV in retained proof (not a previous deleted-file error). Reopened the private artifact with ffprobe; proof MP4 duration **10.0 s**, speech proof **320,044 bytes**.
- Scene dialogue attachment now respects timeline and clip time range. Distant original-language speech remains in a separate timestamped evidence timeline; it is **not falsely narrated over an unrelated scene**.
- Added `MovieExportPreflight.java`: validate selected source metadata/video track, clip range/order, and approximate free space **before** expensive offline TTS. Invalid clips cannot silently reorder their Burmese narration.
- Added `MovieExportPreflight.verifyOutput()`: demand decoded video+audio tracks before publishing an exported MP4 as successful. Instrumented invalid-input/corrupt-output regressions increase expected test count from four to **five**.
- Compact mobile-first UX now scrolls to the one-click Generate action after importing a video.
- Confirmed original three-step **Burmese mobile WebView** appears in a native Android emulator screenshot/render. Test artifact is **not** real vivo V27 validation.
- Previous tested Android artifact verifies only **short synthetic clips**. Full-film plot comprehension and multilingual-to-Burmese neural translation are **still not implemented**; the existing app produces scene-grounded observations. Do **not** label this prototype a completed full movie recap release or pretend Whisper sparse transcripts solve storytelling. Newer CI runs after source/preflight changes must be checked separately.

## 2026-11-22 — cancellation and safe output, Android build verified
- Android WebView's simple **Generate** flow now provides a real **Cancel** button while scene scanning or media export runs. A scan cancelled before the MP4 phase must never launch an export; a running Media3 Transformer can be cancelled via the native bridge.
- Each source-independent export receives a unique MP4 scratch path and WAV speech path so cancelled jobs cannot overwrite the next job's narration.
- Cancellation or invalid input must not publish a partial MP4; only files with genuine video and audio tracks can be saved as completed output. Video picker is disabled while a job runs.
- Android emulator runner now executes the **one-click UI test independently from four speech/video/preflight tests**, avoiding a previously observed emulator disconnection when all five ran in the same instrumentation process. A separate run's pass is still required; code presence is not a passed test.
- **APK build verified PASS** on [44942357391](https://github.com/gf835958-code/Kp/actions/runs/44942357391); new Android emulator smoke run: [44942357383](https://github.com/gf835958-code/Kp/actions/runs/44942357383).
- **Major missing functionality remains unchanged:** trustworthy full movie semantic plot reasoning, reliable all-language-to-Burmese neural translation, phoneme/word-level sync, real full-film and physical vivo V27 stress tests, and appropriately licensed distribution signing. Do not call v0.11 a complete full-auto story recap.
