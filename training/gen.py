"""Generate TTS clips in-process with Piper (16 kHz int16), one voice loaded at a time to bound RAM."""
import json, pickle, random, sys, time
import numpy as np
from scipy.signal import resample_poly
from piper import PiperVoice, SynthesisConfig

VOICES = "/workspace/hgtrain/voices/"
# voice -> (train speaker ids, test speaker ids); single-speaker voices are wholly train or wholly test
MULTI = {"en_US-libritts_r-medium": 904, "en_GB-vctk-medium": 109, "en_US-l2arctic-medium": 24, "en_US-arctic-medium": 18}
SINGLE_TRAIN = ["en_US-amy-medium", "en_US-ryan-medium", "en_GB-alan-medium", "en_US-lessac-medium",
                "en_GB-northern_english_male-medium", "en_GB-jenny_dioco-medium"]
SINGLE_TEST = ["en_US-kristin-medium", "en_US-joe-medium"]

def speakers(voice, split):
    n = MULTI[voice]
    cut = int(n * 0.85)
    return list(range(cut)) if split == "train" else list(range(cut, n))

def plan(split, n):
    """Return a list of (voice, speaker_id) of length n."""
    rng = random.Random(f"{split}-{n}")
    out = []
    multi_share = {"en_US-libritts_r-medium": 0.5, "en_GB-vctk-medium": 0.15, "en_US-l2arctic-medium": 0.05, "en_US-arctic-medium": 0.05}
    singles = SINGLE_TRAIN if split == "train" else SINGLE_TEST
    for v, share in multi_share.items():
        sp = speakers(v, split)
        out += [(v, rng.choice(sp)) for _ in range(int(n * share))]
    while len(out) < n:
        out.append((rng.choice(singles), None))
    return out

def synth(voice, text, sid, rng):
    cfg = SynthesisConfig(speaker_id=sid, length_scale=rng.uniform(0.75, 1.35),
                          noise_scale=rng.uniform(0.5, 0.9), noise_w_scale=rng.uniform(0.6, 1.0))
    a = np.concatenate([c.audio_int16_array for c in voice.synthesize(text, syn_config=cfg)])
    sr = voice.config.sample_rate
    if sr != 16000:
        a = resample_poly(a.astype(np.float32), 16000, sr)
    return np.clip(a, -32768, 32767).astype(np.int16)

def main(phrases_file, split, n, out):
    phrases = [l.strip() for l in open(phrases_file) if l.strip()]
    rng = random.Random(out)
    jobs = plan(split, n)
    by_voice = {}
    for v, sid in jobs:
        by_voice.setdefault(v, []).append(sid)
    clips, t0 = [], time.time()
    for v, sids in by_voice.items():
        voice = PiperVoice.load(VOICES + v + ".onnx")
        for sid in sids:
            text = rng.choice(phrases)
            clips.append((text, synth(voice, text, sid, rng)))
        del voice
        print(f"{out}: {len(clips)}/{n} after {v} ({time.time()-t0:.0f}s)", flush=True)
    pickle.dump(clips, open(out, "wb"))

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4])
