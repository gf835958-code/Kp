# KP Movie Recap v0.5 — One-Click UX Test Build

## One-click flow
Choose an authorized MP4 video, press **Generate Video** once. The app runs frame-based scene selection and bundled ML Kit image labels, then exports original-audio rough-cut highlights to **Movies/KP Movie Recap** via MediaStore on Android 10+ without a second file-save dialog. For Android 8–9 it falls back to the system Save As picker. The UI shows a completion/error status. An optional advanced section is hidden by default.

## NOT YET FULL AUTO MOVIE RECAP
**Do not misrepresent:** This version does **not** understand the plot, narrate Burmese, add voiceover, burn subtitles into video, or reliably select story-important moments. It produces a scene-based montage with the source audio only. The ML Kit captions are coarse object observations rather than a real screenplay. English voice preview may work through Android TTS, while Burmese TTS requires additional model/runtime work.

## QA / build
- Verify with an H.264/AAC MP4 sample on vivo V27 Android 15.
- Verify video saved in Movies/KP Movie Recap; unsupported input codecs may fail.
- Check saved file playback and total duration.
- Build with GitHub Actions; local Android SDK may not be present.

## Future architecture
A real 1-click narrated recap needs visual-language scene understanding, Burmese ASR (for dialogue), plot summarization, a tested bundled Burmese TTS runtime, synced captions, video composition/mixing, retries, and on-device performance validation.
