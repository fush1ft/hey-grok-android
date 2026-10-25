# Hey Grok for Android

## What it is

An Android app that listens on-device for a wake phrase and, when it hears it, opens the Grok Bot app
(package `ai.x.grok.bot`). Detection uses the openWakeWord models running on ONNX Runtime.

## Status

- UNTESTED on a real device or emulator. It was built, linted and unit-tested on a Linux box only (the
  emulator would not boot there).
- The wake phrase is "Hey Grok", detected by a model trained on the build box from synthetic speech (see
  "The hey grok model" below). It has been evaluated on synthetic speech only, not on real voices.

What was verified on the build box:

- `./gradlew assembleDebug` and `./gradlew lint` pass (lint: 0 errors).
- `:wakeword:test` runs the same three ONNX models through `WakeWordDetector` on the JVM against TTS clips:
  "Hey Grok" in two voices held out from training (must score above 0.9), "Hey Jarvis" in two voices and an
  unrelated sentence (must score below 0.2). Python reference (openwakeword 0.6.0, ONNX) max scores on the
  same files: 1.000 and 1.000 for the "Hey Grok" clips, 0.095 to 0.097 for the others.
- The settings screen renders in light and dark mode (Paparazzi snapshots in `app/src/test/snapshots/images/`).

Not verified: microphone capture, the foreground service, notifications, the boot prompt, permissions, the
lock-screen flow and opening Grok Bot on an actual Android device.

## Build

Requirements: JDK 17 and the Android SDK with platform 35. The APK ships ONNX Runtime for `arm64-v8a`
(phones) and `x86_64` (emulator) only; 32-bit ARM devices are not supported.

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
6. If the score reaches the threshold set in the app (default 0.90), the app optionally chimes and opens Grok.

The pipeline is a Kotlin port of openWakeWord 0.6.0's streaming feature extraction and lives in the
`:wakeword` module (`WakeWordDetector`), which is a plain JVM library so it can be tested without a device.

## How Grok Bot is opened

- The app first tries `Intent.ACTION_ASSIST` targeted at `ai.x.grok.bot`. Grok Bot 1.17.0's manifest has
  `expo.modules.nativehelpers.AssistTrampolineActivity` for it, but ships it `enabled="false"`, so the intent
  does not resolve unless Grok enables that component at runtime. It is not a documented public API.
- In practice the app uses Grok Bot's normal launch intent.
- What happens on a detection, in order:
  1. Grok Bot not installed: a "Grok Bot is not installed" notification.
  2. Phone locked or screen off: see "Lock screen" below.
  3. Unlocked and screen on: Android 10 and later block apps from starting activities from the background. If
     "Display over other apps" is granted, the app starts Grok directly. Otherwise it posts a notification
     "Heard the wake word, tap to open Grok".
- "Open Grok now" in the app starts Grok directly (the app is visible then).

## Lock screen

When the phone is locked or the screen is off, the app does not start Grok itself. It starts `UnlockActivity`,
an invisible activity that turns the screen on, shows over the keyguard and asks the system to dismiss it
(`requestDismissKeyguard`). Once the keyguard is gone, it opens Grok. How `UnlockActivity` is started:

1. Full-screen notifications allowed: a full-screen notification "Heard the wake word, unlock to open Grok",
   which the system shows by launching `UnlockActivity`.
2. Otherwise, "Display over other apps" allowed: the app starts `UnlockActivity` directly.
3. Otherwise: the "tap to open Grok" notification, whose tap also goes through `UnlockActivity`.

| Lock | Screen off / locked, full-screen notifications allowed | Not allowed, overlay allowed | Neither |
|---|---|---|---|
| None / swipe | Screen turns on, keyguard dismissed, Grok opens | Same (if the activity start over the keyguard is allowed) | Notification; tap opens Grok |
| PIN / pattern / password | Screen turns on, unlock prompt; Grok opens after unlock | Same | Notification on the lock screen; tap, unlock, Grok opens |
| Fingerprint / face | As PIN, unlock with biometrics; face unlock with "skip lock screen" may be near-instant | Same | As PIN |

- Listening continues while locked: the microphone service was started from the foreground and keeps mic access.
- Permissions involved:
  - `USE_FULL_SCREEN_INTENT`: on Android 14 and later, sideloaded apps are denied by default. Grant it in
    Settings > Apps > Special app access > Full-screen notifications (the setup screen links there).
  - `SYSTEM_ALERT_WINDOW` ("Display over other apps").
  - Android 13 and later grey out both switches for sideloaded apps until you allow restricted settings:
    App info > menu (three dots) > Allow restricted settings.
- Grok Bot cannot show over the keyguard: its 1.17.0 manifest has no `showWhenLocked`, and its assist activity
  and VoiceInteractionService ship disabled. With a secure lock you always unlock first. Smart Lock can skip the
  PIN in trusted places.
- Untested on a real device.

## The hey grok model

`app/src/main/assets/wakeword.onnx` was trained on the build box's CPU (no GPU) with the scripts in
`training/`. It uses openWakeWord's model format and its frozen feature models, so only the small classifier
on top is new.

### Evaluation

All test speech is synthetic: Piper TTS voices and speakers that were not used for training. Each clip is
streamed through openwakeword 0.6.0 in 80 ms frames with 1 s of silence on each side, and the clip's maximum
score is compared with the threshold.

| | Hey Grok, threshold 0.9 (default) | Hey Grok, threshold 0.5 | Previous placeholder hey_jarvis_v0.1, 0.5 |
|---|---|---|---|
| Recall, clean (600 clips) | 0.957 | 0.965 | 0.965 |
| Recall, pink noise at 10 dB SNR | 0.813 | 0.860 | 0.963 |
| False accepts on similar phrases and other speech (1,300 clips) | 0.032 | 0.045 | 0.001 |
| False triggers per hour (10.7 h of mixed background audio) | 0.19 | 0.47 | 0.47 |

- The placeholder column measures "Hey Jarvis" clips from the same held-out voices, so it is a rough
  reference, not a like-for-like comparison.
- False triggers: openWakeWord's `validation_set_features.npy` (speech, music and noise), counting score
  crossings at least 2 s apart.
- Weak spots: phrases that sound almost the same still trigger, e.g. "hey grog", "hey grub", "hey crock",
  "hey crook", "hey greg" and "I grok it now" all reach about 1.0. Noise lowers recall more than it does for
  the placeholder.
- Not measured: real voices, accents beyond the TTS voices, far-field speech and real rooms.
- Default threshold 0.9: it keeps false triggers to about one every five hours on the validation audio while
  losing under 1 point of clean recall (about 5 points in noise). Lower it in the app if it misses you.

### Training recipe

Scripts are in `training/`; they expect to live in `/workspace/hgtrain` (adjust the paths at the top).

1. `dl.sh`: a Python venv (CPU torch, piper-tts 1.8.0, openwakeword 0.6.0 without its dependencies), the
   openWakeWord feature models, 12 Piper English voices, the MIT room impulse responses, and openWakeWord's
   precomputed negative features (`openwakeword_features_ACAV100M_2000_hrs_16bit.npy`, 17 GB, and
   `validation_set_features.npy`).
2. `gen.py` (called by `pipeline.sh`): synthesises clips with random speed and variation.
   - Positives: 15,000 "hey grok" variants (`phrases_pos.txt`).
   - Adversarial: 15,000 clips of about 40 similar phrases (`phrases_adv.txt`: hey google, hey rock, hey
     grog, okay, grocery and so on) and 2,000 ordinary sentences.
   - Voices: libritts_r (904 speakers), vctk (109), l2arctic (24) and arctic (18), split 85/15 by speaker
     into train and test; amy, ryan, alan, lessac, northern_english_male and jenny_dioco for training only;
     kristin and joe for testing only.
   - Test sets: 600 positives, 1,000 adversarial, 300 sentences, 600 "hey jarvis".
3. `feat.py`: 2 s windows with the phrase ending near the end of the window, room reverb, coloured noise
   (5 to 30 dB SNR), speech babble (5 to 20 dB) and gain changes, turned into openWakeWord embeddings
   (16 x 96). Positives are augmented twice (30,000 windows).
4. `train.py 60000` with `LAYER=64 MAX_NEG_W=1000`: openWakeWord's DNN (two hidden layers of 64 with
   LayerNorm), batches of 128 positives, 128 adversarial windows and 1,024 rows of the negative features, a
   negative weight rising from 1 to 1,000, hard-example mining, warmup then cosine learning rate. It keeps the
   checkpoint with the best held-out recall among those with at most 0.5 false triggers per hour, and exports
   `out/hey_grok.onnx` (input `[1, 16, 96]`, output `[1, 1]`).
5. `eval.py`: the evaluation above.

Run inside a memory-capped cgroup at the lowest CPU priority (`run_limited.sh`, 2.6 GB cap). Wall time on the
box: clip generation about 22 min, features about 9 min, the 17 GB download about 6 min in parallel, training
10.5 min (60,000 steps), evaluation about 5 min. A first 30,000-step run with 32-unit layers had no false
triggers but only 0.71 recall in noise, so the bigger, longer run was kept.

## Licences

- The hey grok model: our own weights, but its negative training features
  (`davidscripka/openwakeword_features`) are CC BY-NC-SA 4.0, and some of the Piper voices used to make
  training speech come from non-commercial datasets (l2arctic: CC BY-NC 4.0; ryan: CC BY-NC-SA 4.0; lessac:
  Blizzard 2013 licence). Treat the model as non-commercial use only. openWakeWord's own pretrained models
  carry CC BY-NC-SA 4.0 for the same reason.
- The test WAVs `hey_grok_kristin.wav` (public-domain dataset) and `hey_grok_joe.wav` (CC0 dataset) were
  synthesised with Piper.
- openWakeWord code and its feature models (`melspectrogram.onnx`, `embedding_model.onnx`) are Apache-2.0.
- Instrument Serif is under the SIL Open Font License.
- Licence texts are in `third_party/`.

## Limits

- Android 14 and later: a microphone foreground service can only be started while the app is visible or from
  a notification tap, and never from `BOOT_COMPLETED`. That is why "Offer to start after reboot" posts a
  "Tap to start listening" notification instead of starting directly.
- The service is `START_NOT_STICKY`: a system restart from the background would get no mic access. If the
  system kills it, opening the app restarts listening (the app is visible then, so this is allowed).
- Background activity launch rules: while unlocked and without "Display over other apps", Grok opens only after
  you tap the notification. On the lock screen, see "Lock screen".
- OEM battery killers (Xiaomi/MIUI/HyperOS, Samsung, Huawei, OnePlus, Oppo/Vivo) may stop the service. Allow
  unrestricted battery use, lock the app in recents, and see https://dontkillmyapp.com.
- Power use: the microphone and about 12.5 inferences per second run continuously. Expect noticeable battery
  drain. This is a rough estimate only; it has not been measured.
- The microphone privacy indicator stays on while listening.
- Other apps using the microphone (calls, recorders) can starve or block capture.

## Privacy

All audio is processed on the device. Nothing is recorded, stored or sent anywhere.
