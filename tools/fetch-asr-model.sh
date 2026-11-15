#!/usr/bin/env bash
set -euo pipefail
# Pinned HF revision; tiny multilingual Whisper INT8 model, ASR only.
# It has no plot understanding and no Burmese translation functionality.
dir=app/src/main/assets/asr
mkdir -p "$dir"
base=https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/65176e2deb88badc814a94058666cadccc29b61c
for name in tiny-encoder.int8.onnx tiny-decoder.int8.onnx tiny-tokens.txt; do
  curl --fail --location --retry 3 --silent --show-error "$base/$name" -o "$dir/$name"
  test -s "$dir/$name"
done
echo "Tiny multilingual Whisper ASR assets downloaded from pinned revision:"
sha256sum "$dir"/tiny-*.onnx "$dir"/tiny-tokens.txt
