# KP Movie Recap v0.6 — Native long-video scanning repair

The previous v0.5 fails on some full-length movies because HTML5 <video> seeking has a hard 12-second timeout. v0.6 now samples representative keyframes through Android MediaMetadataRetriever in a background executor. The scanner can return sample timestamps even if all thumbnails fail. One-click rough-cut export remains in Movies/KP Movie Recap (Android 10+).

## What is implemented
- Local SAF video selection; on-device native frame sampling and ML Kit image labeling.
- No manual notes required for coarse visual observation text.
- One tap to begin scan and export scene highlight MP4 with source audio.
- Native scanner returns video duration so export does not depend on HTML video metadata.
- No movie uploaded.

## What is NOT implemented
**This is not a complete movie recap AI app.** No full-plot understanding, automatic accurate story script, bundled Burmese speech synthesizer, subtitle burn-in, or synchronized narration. It must not be offered to the user as fulfilling those requirements.

## QA
Build with GitHub Actions. Check install and scene scan on vivo V27 with the known long video before distribution. Verify resulting MP4 plays and audio is retained. v0.6 source changes are a diagnostic engineering fix, not a finished product.

## Burmese TTS research
Meta MMS Burmese TTS checkpoint exists but must be converted, legally reviewed (CC BY-NC 4.0 restrictions) and benchmarked with an on-device runtime before integration. A standalone external TTS app is not an adequate substitute for a bundled, one-click workflow.
