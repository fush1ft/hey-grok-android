"""Streaming evaluation (openwakeword 0.6.0 Model.predict, 80 ms frames) + false triggers per hour on the 10.7 h validation stream."""
import json, pickle, sys
import numpy as np, onnxruntime as ort
from openwakeword.model import Model

M = "/workspace/hgtrain/models/"
D = "/workspace/hgtrain/"
# Extra models to compare against can be added here; "hey_jarvis" is scored on the "hey jarvis" clips.
MODELS = {"ours": D + "out/hey_grok.onnx", "hey_jarvis": M + "hey_jarvis_v0.1.onnx"}
TH = [0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]
rng = np.random.default_rng(7)

def clips(name):
    return pickle.load(open(D + "clips/" + name, "rb"))

def noisy(a, snr_db):
    x = a.astype(np.float32)
    n = rng.standard_normal(len(x)); f = np.fft.rfft(n); f /= np.maximum(np.arange(len(f)), 1) ** 0.5
    n = np.fft.irfft(f, len(x)); n *= x.std() / (n.std() + 1e-9) / 10 ** (snr_db / 20)
    return np.clip(x + n, -32768, 32767).astype(np.int16)

def max_scores(model, audio_list):
    out = []
    for a in audio_list:
        model.reset()
        a = np.concatenate([np.zeros(16000, np.int16), a, np.zeros(16000, np.int16)])
        s = [list(model.predict(a[i:i + 1280]).values())[0] for i in range(0, len(a) - 1279, 1280)]
        out.append(max(s))
    return np.array(out)

def fp_per_hour(path):
    sess = ort.InferenceSession(path); name = sess.get_inputs()[0].name
    val = np.load(D + "data/validation_set_features.npy", mmap_mode="r")
    scores = []
    for s in range(0, val.shape[0] - 16, 20000):
        chunk = np.asarray(val[s:min(s + 20000 + 15, val.shape[0])], dtype=np.float32)
        win = np.ascontiguousarray(np.lib.stride_tricks.sliding_window_view(chunk, (16, 96))[:, 0])
        scores.append(np.concatenate([sess.run(None, {name: win[i:i + 1]})[0].ravel() for i in range(len(win))])
                      if sess.get_inputs()[0].shape[0] == 1 else sess.run(None, {name: win})[0].ravel())
    scores = np.concatenate(scores)
    res = {}
    for t in TH:
        events, last = 0, -10**9
        for i in np.flatnonzero(scores >= t):
            if i - last > 25:
                events += 1
            last = i
        res[t] = round(events / (val.shape[0] * 0.08 / 3600), 2)
    return res

def rate(scores):
    return {t: round(float((scores >= t).mean()), 3) for t in TH}

report = {}
pos = [c for _, c in clips("pos_test.pkl")]
jar = [c for _, c in clips("jarvis_test.pkl")]
adv = clips("adv_test.pkl") + clips("speech_test.pkl")
SEL = sys.argv[1:] or list(MODELS)
OUT = D + "out/eval_" + "_".join(SEL) + ".json"
for key, path in MODELS.items():
    if key not in SEL:
        continue
    m = Model(wakeword_models=[path], inference_framework="onnx",
              melspec_model_path=M + "melspectrogram.onnx", embedding_model_path=M + "embedding_model.onnx")
    target = jar if key == "hey_jarvis" else pos
    r = {"recall_clean": rate(max_scores(m, target)),
         "recall_noisy_10db": rate(max_scores(m, [noisy(a, 10) for a in target]))}
    adv_texts = [(t, a) for t, a in adv if not (key == "hey_jarvis" and "jarvis" in t.lower())]
    s = max_scores(m, [a for _, a in adv_texts])
    r["adversarial_false_accept"] = rate(s)
    worst = {}
    for (t, _), v in zip(adv_texts, s):
        worst[t] = max(worst.get(t, 0), float(v))
    r["worst_adversarial_phrases"] = sorted(((round(v, 3), t) for t, v in worst.items()), reverse=True)[:6]
    r["false_triggers_per_hour_10_7h"] = fp_per_hour(path)
    report[key] = r
    print(key, json.dumps(r), flush=True)
json.dump(report, open(OUT, "w"), indent=1)
