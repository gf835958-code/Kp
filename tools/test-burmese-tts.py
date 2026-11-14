#!/usr/bin/env python3
"""Noncommercial Burmese TTS inference smoke test on CI (NOT the Android APK)."""
import sys, math
from pathlib import Path
import soundfile as sf
import sherpa_onnx
model_dir = Path(sys.argv[1]) if len(sys.argv)>1 else Path(".cache/mms-mya")
vits = sherpa_onnx.OfflineTtsVitsModelConfig(
    model=str(model_dir / "model.onnx"), tokens=str(model_dir / "tokens.txt"))
config = sherpa_onnx.OfflineTtsConfig(
    model=sherpa_onnx.OfflineTtsModelConfig(vits=vits, num_threads=2, debug=False),
    max_num_sentences=1)
if not config.validate(): raise RuntimeError("Invalid Burmese TTS config")
tts = sherpa_onnx.OfflineTts(config)
gen = sherpa_onnx.GenerationConfig()
gen.speed = 1.0
audio = tts.generate("မင်္ဂလာပါ။ မြန်မာအသံ စမ်းသပ်နေပါတယ်။", gen)
assert audio.sample_rate > 8000, "Invalid TTS output sample rate"
assert len(audio.samples) > audio.sample_rate // 2, "TTS generated no meaningful duration"
peak = max(abs(float(x)) for x in audio.samples)
assert peak > 1e-4 and math.isfinite(peak), "TTS produced silence/invalid audio"
sf.write("burmese-tts-ci-sample.wav", audio.samples, audio.sample_rate, subtype="PCM_16")
print(f"PASS Burmese offline speech: {len(audio.samples)/audio.sample_rate:.2f}s, peak={peak:.4f}, {audio.sample_rate} Hz")
