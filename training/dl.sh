#!/bin/bash
set -x
cd /workspace/hgtrain
python3 -m venv venv && venv/bin/pip install -q --index-url https://download.pytorch.org/whl/cpu torch && venv/bin/pip install -q onnxruntime numpy scipy piper-tts onnx tqdm scikit-learn && venv/bin/pip install -q --no-deps openwakeword==0.6.0
mkdir -p models && for m in melspectrogram embedding_model hey_jarvis_v0.1; do curl -sSLo models/$m.onnx https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/$m.onnx; done
mkdir -p data/rirs && for f in $(curl -s "https://huggingface.co/api/datasets/davidscripka/MIT_environmental_impulse_responses/tree/main/16khz" | python3 -c "import sys,json;[print(x['path']) for x in json.load(sys.stdin)]"); do curl -sSL -o data/rirs/$(basename $f) "https://huggingface.co/datasets/davidscripka/MIT_environmental_impulse_responses/resolve/main/$f"; done
mkdir -p voices; cd voices; for v in en_US/libritts_r/medium/en_US-libritts_r-medium en_US/amy/medium/en_US-amy-medium en_US/ryan/medium/en_US-ryan-medium en_GB/alan/medium/en_GB-alan-medium en_US/lessac/medium/en_US-lessac-medium en_US/joe/medium/en_US-joe-medium en_US/kristin/medium/en_US-kristin-medium en_GB/northern_english_male/medium/en_GB-northern_english_male-medium en_GB/jenny_dioco/medium/en_GB-jenny_dioco-medium en_US/l2arctic/medium/en_US-l2arctic-medium en_GB/vctk/medium/en_GB-vctk-medium en_US/arctic/medium/en_US-arctic-medium; do b=$(basename $v); curl -sSLo $b.onnx https://huggingface.co/rhasspy/piper-voices/resolve/main/$v.onnx; curl -sSLo $b.onnx.json https://huggingface.co/rhasspy/piper-voices/resolve/main/$v.onnx.json; done; cd ..
curl -sSL -o data/validation_set_features.npy https://huggingface.co/datasets/davidscripka/openwakeword_features/resolve/main/validation_set_features.npy
curl -sSL -o data/acav100m_features.npy https://huggingface.co/datasets/davidscripka/openwakeword_features/resolve/main/openwakeword_features_ACAV100M_2000_hrs_16bit.npy
echo ALL_DL_DONE
