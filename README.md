# KP Movie Recap v0.8 — Experimental offline Burmese narration

## What has been tested
- v0.7 Android build passed GitHub Actions: run 43541323469.
- Burmese ONNX TTS generated real non-silent audio on a GitHub Linux runner: run 43541476038.
- Android v0.8 source now generates **Burmese narration from machine-generated visual scene captions**, and combines narration audio, on-screen captions, and video clips with Media3.
- Build downloads the model only on GitHub CI, not on the user's phone, to avoid repeated mobile downloads.

## Incomplete and unverified
- **NOT full automatic movie recap:** visual image labeling cannot infer movie plot, dialogue, causality, names or character intentions. It does not write a true movie synopsis.
- Android speech engine initialization, voice quality, timing, MP4 rendering and 30-minute clips still require physical device verification.
- The WAV generator pads or truncates each utterance to the corresponding clip duration: this is approximate timeline alignment, NOT voice-synchronized subtitles or good quality narration.
- Android build success does not mean feature-complete or ready for distribution. Do not distribute test APK as a completed solution.

## Free local components
- Native MediaMetadataRetriever frame sampling, ML Kit object labeling.
- sherpa-onnx 2.0.1 Android runtime, Burmese MMS VITS model, built into a test APK via GitHub Actions.
- Media3 Transformer 1.11.1 for caption rendering and separate video/audio tracks.
- Android minimum SDK 26, target 34, compile 36; v0.8 ARM64 only.

## Licenses and rights
The Burmese MMS model is made available under CC BY-NC 4.0 noncommercial terms, with attribution requirements; sherpa-onnx uses Apache 2.0. Verify all licenses before any public release. Source: https://huggingface.co/willwade/mms-tts-multilingual-models-onnx and https://github.com/k2-fsa/sherpa-onnx .

Only process and redistribute videos with the appropriate permissions. No server upload or API key is required for inference with the bundled model.
