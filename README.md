# Hey Grok for Android

## What it is

An Android app that listens on-device for a wake phrase and, when it hears it, opens the Grok Bot app
(package `ai.x.grok.bot`). Detection uses the openWakeWord models running on ONNX Runtime.

## Status

- UNTESTED on a real device. It was built and unit-tested on a Linux box only.
- The bundled wake model is a placeholder: openWakeWord's pretrained "hey_jarvis_v0.1". Say "Hey Jarvis",
  not "Hey Grok". See "Training a real hey grok model" below.

## Build

Requirements: JDK 17 and the Android SDK with platform 35.

```
./gradlew assembleDebug                  # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :wakeword:test                 # detector test against the WAVs in wakeword/src/test/resources
./gradlew lint
./gradlew :app:recordPaparazziDebug      # screenshots of the settings screen, light and dark
```

## Install

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## How it works

1. `WakeWordService` (a microphone foreground service) captures 16 kHz mono 16-bit PCM with `AudioRecord`.
2. Audio is split into 80 ms frames of 1280 samples.
3. `melspectrogram.onnx` turns each frame (plus 480 samples of left context) into 32-bin mel frames.
4. `embedding_model.onnx` maps a 76-frame mel window to a 96-dimensional embedding, one per audio frame.
5. The wake model (`wakeword.onnx`) scores the last 16 embeddings (about 1.3 s of audio).
6. If the score reaches the threshold set in the app (default 0.50), the app optionally chimes and opens Grok.

The pipeline is a Kotlin port of openWakeWord 0.6.0's streaming feature extraction and lives in the
`:wakeword` module (`WakeWordDetector`), which is a plain JVM library so it can be tested without a device.

## How Grok Bot is opened

- The app sends `Intent.ACTION_ASSIST` targeted at `ai.x.grok.bot`. In Grok Bot 1.17.0 this resolves to
  `expo.modules.nativehelpers.AssistTrampolineActivity`, which was found in that version's manifest. It is not
  a documented public API and may change or disappear in any update.
- If that intent does not resolve, the app falls back to Grok Bot's normal launch intent.
- Android 10 and later block apps from starting activities from the background. If "Display over other apps"
  is granted, the app starts Grok directly. Otherwise a detection posts a notification "Heard the wake word,
  tap to open Grok".

## Wake word model and licences

- The placeholder model is openWakeWord's pretrained `hey_jarvis_v0.1`, licensed CC BY-NC-SA 4.0. That makes
  this build usable for non-commercial purposes only.
- openWakeWord code (from which the feature models and the pipeline come) is Apache-2.0.
- Instrument Serif is under the SIL Open Font License.
- Licence texts are in `third_party/`.

## Training a real "hey grok" model

Training needs a Linux machine with an NVIDIA GPU (a Colab T4 works). It was not run on the build box (no GPU,
about 2 GB of free RAM, and about 17 GB of training features to download). The steps follow openWakeWord's
`notebooks/automatic_model_training.ipynb` (v0.6.0).

1. Set up:
   - `git clone https://github.com/dscripka/openWakeWord && pip install -e ./openWakeWord[full]`
   - Clone `https://github.com/rhasspy/piper-sample-generator` and download `en_US-libritts_r-medium.pt`
     (release v2.0.0) into its `models/` directory.
   - Install the notebook's pinned extras: piper-phonemize, webrtcvad, torch, audiomentations 0.33.0,
     torch-audiomentations 0.11.0, speechbrain 0.5.14, acoustics, mutagen, torchinfo, torchmetrics,
     pronouncing, datasets 2.14.6, deep-phonemizer, onnx.
2. Download data:
   - MIT room impulse responses (Hugging Face `davidscripka/MIT_environmental_impulse_responses`).
   - Background audio: part of AudioSet and FMA small, converted to 16 kHz.
   - Precomputed negative features `openwakeword_features_ACAV100M_2000_hrs_16bit.npy` and
     `validation_set_features.npy` from Hugging Face `davidscripka/openwakeword_features`.
3. Copy `examples/custom_model.yml` to `hey_grok.yml` and set `target_phrase: ["hey grok"]`,
   `model_name: hey_grok`, `n_samples: 20000` or more (the notebook demo uses 1000), `n_samples_val: 2000`,
   `steps: 50000`, the background audio paths and the feature file paths. Consider adding
   `custom_negative_phrases` such as "hey google", "okay", "grok", "hey rock".
4. Run, in order:
   ```
   python openWakeWord/openwakeword/train.py --training_config hey_grok.yml --generate_clips
   python openWakeWord/openwakeword/train.py --training_config hey_grok.yml --augment_clips
   python openWakeWord/openwakeword/train.py --training_config hey_grok.yml --train_model
   ```
   The output is `my_custom_model/hey_grok.onnx`.
5. Check it: the input must be `[1, 16, 96]` and the output `[1, 1]`. Try it live with
   `examples/detect_from_microphone.py`.
6. Drop it in:
   - Copy it to `app/src/main/assets/wakeword.onnx`.
   - In `app/src/main/java/cz/fushift/heygrok/WakeModel.kt` set `PHRASE = "Hey Grok"` and
     `IS_PLACEHOLDER = false`.
   - Update the WAV-based expectations in `WakeWordDetectorTest` (add a "hey grok" WAV; the "Hey Jarvis"
     clips should then stay below the threshold).
   - Rebuild.

## Limits

- Android 14 and later: a microphone foreground service can only be started while the app is visible or from
  a notification tap, and never from `BOOT_COMPLETED`. That is why "Offer to start after reboot" posts a
  "Tap to start listening" notification instead of starting directly.
- The service is `START_NOT_STICKY`: a system restart from the background would get no mic access, so if the
  system kills it, start it again from the app.
- Background activity launch rules: without "Display over other apps", Grok opens only after you tap the
  notification.
- OEM battery killers (Xiaomi/MIUI/HyperOS, Samsung, Huawei, OnePlus, Oppo/Vivo) may stop the service. Allow
  unrestricted battery use, lock the app in recents, and see https://dontkillmyapp.com.
- Power use: the microphone and about 12.5 inferences per second run continuously. Expect noticeable battery
  drain. This is a rough estimate only; it has not been measured.
- The microphone privacy indicator stays on while listening.
- Other apps using the microphone (calls, recorders) can starve or block capture.

## Privacy

All audio is processed on the device. Nothing is recorded, stored or sent anywhere.
