# KP Movie Recap Studio — Product requirements & release gates (2026-11-21)

**Single source of truth for existing project** — `gf835958-code/Kp`, branch `kp-movie-recap-app`. This is **not a second app**. User wants one installable, reliable, easy-to-use, no-key Android APK, not a webpage, ZIP, simulated export or a collection of demo APKs.

## User-facing goal
1. Select a movie of practical length from Android Files, including large videos.
2. Tap **one visible Generate** button — no scripting, API key, account, manually typed captions, manually supplied movie script, or separate model installation.
3. Actually understand important actors, story events and their causal order using **evidence from video frames and soundtrack**, and avoid inventing unsupported plot details.
4. Detect spoken source languages automatically, transcribe dialogue, translate it to **natural, correct Myanmar Unicode Burmese** when possible, and generate an accurate, context-consistent Burmese recap script.
5. Speak that script using a natural Burmese TTS voice, independent of the phone's system TTS language list.
6. Burn correctly shaped, readable Burmese Unicode subtitles into the right portions of the resulting video, with timestamps matched to narration.
7. Export a genuinely playable **H.264/AAC MP4**, save it in a discoverable folder and enable opening/sharing it on the phone.
8. Work on **vivo V27 / Android 15**, with preference for offline processing, no root, no subscriptions, no credentials, no mandatory cloud server or paid key.
9. Keep mobile data use to a minimum; avoid repeated ~200 MB provisional downloads. Bundle what is necessary into **one APK**.
10. UI: clear friendly Burmese, accessible large touch targets, no tech jargon on the main screen, simple **Video → Generate → MP4**, legible feedback and progress, help/advanced optional.
11. Respect source rights, user privacy and applicable noncommercial model licenses.

## Implemented, evidence and caveats
| Feature | Code state | Verified scope |
|---|---|---|
| Native large-video seeking | Existing | Android emulator synthetic 1802-second fixture; not 438 MB on vivo |
| ML Kit image labels | Existing | Describes visible objects, not movie storyline |
| Whisper-tiny multilingual dialogue excerpts | Existing | Up to 6×12-second windows, not full-film transcription |
| Original-language timestamped speech evidence | Existing | Does not translate arbitrary languages to Burmese |
| Silent soundtrack / hallucination guard | Existing | Android emulator silent AAC regression PASS |
| Burmese offline ONNX speech | Existing | Android emulator short scene test PASS, model CC BY-NC |
| Scene-caption burn-in MP4 | Existing | Media3 Android emulator short clip PASS |
| End-to-end one button → saved MP4 | Existing | Android emulator short synthetic video PASS |
| Friendly Burmese UI, simple flow, open last MP4 | v0.11 source | Build and emulator retest required; no physical vivo guarantee |
| Reliable plot/character/causal story reasoning | **BLOCKED / NOT IMPLEMENTED** | Image labels + sparse speech excerpts are insufficient |
| Full arbitrary-language dialogue translation → Burmese | **NOT IMPLEMENTED** | Whisper ASR is not Myanmar translation |
| Natural full-movie narration script generation | **NOT IMPLEMENTED** | Currently short, cautious scene descriptions |
| Precise narration/word-level subtitle sync | **NOT IMPLEMENTED** | Current synchronization is only scene-level |
| Large movie playback and uninterrupted Android export | **UNVERIFIED** | Actual file-size, thermal and memory checks needed |
| APK fully validated on user's real device | **UNVERIFIED** | Only a real-device test proves it |

## Mandatory release criteria (do not mark complete early)
- App cold starts and file picker works on actual Android 15 vivo V27, including airplane mode.
- Single Generate action works on a **full real movie** and produces a complete MP4 with audible natural Burmese narration and readable correctly timed Burmese captions.
- Grounded generated Burmese recap agrees with the movie's story on independently reviewed scenes/dialogue. No fabricated character names, events, causality or falsely translated audio.
- At least several source languages are validated against known ground truth. Unsupported language must be reported honestly, not silently described as translated.
- App recovers cleanly from corrupted files, muted videos, unsupported formats, full storage, background interruption, model/OOM failures and long running media processing. Must have explicit retry/cancel and prevent duplicate jobs.
- MP4 length, audio/video tracks, time alignment, compatibility and finish-state are checked programmatically **and** visually / audibly on device.
- UI is verified at common small and large Android screen widths; has proper Burmese font shaping, contrast, focus/touch size, loading/success/error messaging.
- Source/model licenses, attributions, distribution and commercial-use constraints confirmed; don't distribute any model outside permitted terms.
- Only after passes: provide **one direct, versioned APK** link and a concise verification report. Never call an emulator-only prototype a finished full movie recap app.

## UI layout contract (v0.11)
- Friendly light theme; header says KP Movie Recap, offline/local processing.
- Mini flow chips: **၁ Video ရွေး → ၂ ဖန်တီး → ၃ MP4 ကြည့်**.
- One main file picker and prominent Generate CTA; disabled until file chosen.
- Compact progress/status updated in Burmese; success card shows saved path with one **ဖွင့်ကြည့်မယ်** action.
- Advanced controls, scene evidence/script editing, alternate exports remain available but hidden from the simple home screen.
- Avoid misleading claims: **current generated voice is a scene-level narrated video, not a complete reliable movie storyline**.

## Implementation priority
1. Stable v0.11 UI, true APK build and Android emulator UI/MP4 regression, with screenshot review.
2. Reliability: video file-size/codec validation, storage checks, background export, cancel/retry, model and output integrity.
3. Source-grounded, longer speech/scene context with efficient selective sampling.
4. Genuine local multilingual → Burmese translation model (quality, feasibility, licensing, device RAM/data budget).
5. Reliable visual+dialogue narrative reasoning and structured Burmese script.
6. High-quality Burmese TTS with speech-aligned subtitle renderer and complete real-device acceptance testing.

**Do not delete or degrade already implemented features to simplify the UI.** This is an existing-project integration, not a mockup.
