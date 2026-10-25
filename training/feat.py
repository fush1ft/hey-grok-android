"""Augment clips into 2 s windows (RIR, noise, babble, gain) and compute openWakeWord embeddings (N, 16, 96)."""
import glob, pickle, sys
from scipy.io import wavfile
import numpy as np
from scipy.signal import fftconvolve
from openwakeword.utils import AudioFeatures

WIN = 32000  # 2 s -> exactly 16 embedding frames
MODELS = "/workspace/hgtrain/models/"

def load_rirs():
    out = []
    for f in sorted(glob.glob("/workspace/hgtrain/data/rirs/*.wav")):
        sr, a = wavfile.read(f)
        a = a.astype(np.float32)
        out.append(a / (np.abs(a).max() + 1e-9))
    return out

def colored_noise(n, rng):
    kind = rng.integers(3)
    x = rng.standard_normal(n)
    if kind:
        f = np.fft.rfft(x)
        f /= np.maximum(np.arange(len(f)), 1) ** (0.5 * kind)  # pink (1/f) or brown (1/f^2)
        x = np.fft.irfft(f, n)
    return x / (x.std() + 1e-9)

def place(clip, rng, at_end):
    out = np.zeros(WIN, np.float32)
    if at_end:  # wake word ends near the end of the window, like openWakeWord's end_jitter
        end = WIN - int(rng.uniform(0, 0.2) * 16000)
    else:
        end = int(rng.uniform(min(len(clip), WIN), WIN + 1))
    clip = clip[-end:]
    out[end - len(clip):end] = clip
    return out

def augment(clip, rng, rirs, babble, at_end):
    x = clip.astype(np.float32)
    if rirs and rng.random() < 0.5:
        x = fftconvolve(x, rirs[rng.integers(len(rirs))])[: len(x)]
    x = place(x, rng, at_end)
    sig = x.std() + 1e-9
    if rng.random() < 0.8:
        x += colored_noise(WIN, rng) * sig / 10 ** (rng.uniform(5, 30) / 20)
    if babble and rng.random() < 0.4:
        b = babble[rng.integers(len(babble))].astype(np.float32)
        b = np.resize(b, WIN)
        x += b / (b.std() + 1e-9) * sig / 10 ** (rng.uniform(5, 20) / 20)
    x *= rng.uniform(0.05, 0.9) * 32767 / (np.abs(x).max() + 1e-9)
    return x.astype(np.int16)

def main(inp, out, at_end, rounds, seed):
    clips = [c for _, c in pickle.load(open(inp, "rb"))]
    babble = [c for _, c in pickle.load(open("/workspace/hgtrain/clips/speech_train.pkl", "rb"))]
    rng = np.random.default_rng(seed)
    rirs = load_rirs()
    F = AudioFeatures(melspec_model_path=MODELS + "melspectrogram.onnx",
                      embedding_model_path=MODELS + "embedding_model.onnx", inference_framework="onnx", ncpu=2)
    feats = []
    for r in range(rounds):
        for i in range(0, len(clips), 256):
            batch = np.stack([augment(c, rng, rirs, babble, at_end) for c in clips[i:i + 256]])
            feats.append(F.embed_clips(batch, batch_size=64, ncpu=2).astype(np.float16))
        print(f"{out}: round {r+1}/{rounds}", flush=True)
    np.save(out, np.concatenate(feats))

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] == "end", int(sys.argv[4]), int(sys.argv[5]))
