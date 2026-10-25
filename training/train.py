"""Train an openWakeWord-style DNN (16x96 -> 1) on CPU. Architecture and loss schedule follow openwakeword/train.py."""
import json, sys, time
import numpy as np
import torch
from torch import nn

torch.set_num_threads(3)
D = "/workspace/hgtrain/"
STEPS = int(sys.argv[1]) if len(sys.argv) > 1 else 20000
import os
LAYER = int(os.environ.get("LAYER", 32))
MAX_NEG_W = int(os.environ.get("MAX_NEG_W", 1500))
VAL_HOURS = 481345 * 0.08 / 3600  # 10.7 h

class Net(nn.Module):
    def __init__(self):
        super().__init__()
        self.layers = nn.Sequential(
            nn.Flatten(), nn.Linear(16 * 96, LAYER), nn.LayerNorm(LAYER), nn.ReLU(),
            nn.Linear(LAYER, LAYER), nn.LayerNorm(LAYER), nn.ReLU(),
            nn.Linear(LAYER, 1), nn.Sigmoid())
    def forward(self, x):
        return self.layers(x)

def load(name):
    return torch.from_numpy(np.load(D + "feats/" + name).astype(np.float32))

pos, adv = load("pos_train.npy"), torch.cat([load("adv_train.npy"), load("speech_train.npy")])
pos_test, adv_test = load("pos_test.npy"), load("adv_test.npy")
acav = np.load(D + "data/acav100m_features.npy", mmap_mode="r")
val = np.load(D + "data/validation_set_features.npy", mmap_mode="r")
print("pos", pos.shape, "adv", adv.shape, "acav", acav.shape, acav.dtype, "val", val.shape, flush=True)

def acav_batch(rng, n=1024):
    start = int(rng.integers(0, acav.shape[0] - n))
    return torch.from_numpy(np.asarray(acav[start:start + n], dtype=np.float32))

def fp_events_per_hour(model, threshold=0.5, refractory=25):
    """Slide a 16-frame window over the 11.3 h validation stream; count rising edges with a 2 s refractory."""
    scores = []
    with torch.no_grad():
        for s in range(0, val.shape[0] - 16, 50000):
            chunk = np.asarray(val[s:min(s + 50000 + 15, val.shape[0])], dtype=np.float32)
            win = np.lib.stride_tricks.sliding_window_view(chunk, (16, 96))[:, 0]
            scores.append(model(torch.from_numpy(np.ascontiguousarray(win))).numpy().ravel())
    scores = np.concatenate(scores)
    events, last = 0, -10**9
    for i in np.flatnonzero(scores >= threshold):
        if i - last > refractory:
            events += 1
        last = i
    return events / VAL_HOURS, scores

def evaluate(model):
    with torch.no_grad():
        recall = (model(pos_test).ravel() >= 0.5).float().mean().item()
        adv_fa = (model(adv_test).ravel() >= 0.5).float().mean().item()
    fph, _ = fp_events_per_hour(model)
    return recall, adv_fa, fph

def lr_at(step, base=1e-4, warm=STEPS // 5, hold=STEPS // 3):
    if step < warm:
        return base * step / warm
    if step < warm + hold:
        return base
    return 0.5 * base * (1 + np.cos(np.pi * (step - warm - hold) / max(1, STEPS - warm - hold)))

model = Net()
opt = torch.optim.Adam(model.parameters(), lr=1e-4)
rng = np.random.default_rng(0)
bce = nn.BCELoss(reduction="none")
history, best, t0 = [], None, time.time()
for step in range(1, STEPS + 1):
    for g in opt.param_groups:
        g["lr"] = lr_at(step)
    p = pos[rng.integers(0, len(pos), 128)]
    a = adv[rng.integers(0, len(adv), 128)]
    x = torch.cat([p, a, acav_batch(rng)])
    y = torch.cat([torch.ones(len(p)), torch.zeros(len(x) - len(p))])
    w = torch.where(y == 1, torch.tensor(1.0), torch.tensor(1 + (MAX_NEG_W - 1) * step / STEPS))
    pred = model(x).ravel()
    # Like openWakeWord: only learn from examples the model still gets wrong (hard-example mining).
    wrong = ((y == 1) & (pred < 0.9)) | ((y == 0) & (pred > 0.1))
    if wrong.any():
        loss = (bce(pred, y) * w)[wrong].mean()
        opt.zero_grad(); loss.backward(); opt.step()
    if step % 1000 == 0 or step == STEPS:
        recall, adv_fa, fph = evaluate(model)
        history.append(dict(step=step, recall=recall, adv_fa=adv_fa, fp_per_hour=fph))
        print(json.dumps(history[-1]), f"{time.time()-t0:.0f}s", flush=True)
        if step >= STEPS // 2 and fph <= 0.5 and (best is None or recall > best[0]):
            best = (recall, step)
            torch.save(model.state_dict(), D + "out/best.pt")
        torch.save(model.state_dict(), D + "out/last.pt")
json.dump(history, open(D + "out/history.json", "w"), indent=1)
if best is None:
    torch.save(model.state_dict(), D + "out/best.pt")
model.load_state_dict(torch.load(D + "out/best.pt"))
model.eval()
torch.onnx.export(model, torch.zeros(1, 16, 96), D + "out/hey_grok.onnx", input_names=["x"], output_names=["score"],
                  opset_version=13, dynamo=False)
print("exported best step", best, flush=True)
