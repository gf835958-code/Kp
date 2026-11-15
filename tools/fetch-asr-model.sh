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
# Verify all three downloaded assets before packing them into an APK.
echo 'd24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434  app/src/main/assets/asr/tiny-encoder.int8.onnx' | sha256sum -c -
echo 'd2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925  app/src/main/assets/asr/tiny-decoder.int8.onnx' | sha256sum -c -
echo 'b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126  app/src/main/assets/asr/tiny-tokens.txt' | sha256sum -c -
echo "Verified multilingual Whisper tiny INT8 assets."
