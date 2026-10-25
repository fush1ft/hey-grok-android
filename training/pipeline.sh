#!/bin/bash
set -e
cd /workspace/hgtrain
P="venv/bin/python"
date
[ -f clips/speech_train.pkl ] || $P gen.py phrases_speech.txt train 2000 clips/speech_train.pkl
[ -f clips/pos_train.pkl ] || $P gen.py phrases_pos.txt train 15000 clips/pos_train.pkl
[ -f clips/adv_train.pkl ] || $P gen.py phrases_adv.txt train 15000 clips/adv_train.pkl
[ -f clips/pos_test.pkl ] || $P gen.py phrases_pos.txt test 600 clips/pos_test.pkl
[ -f clips/adv_test.pkl ] || $P gen.py phrases_adv.txt test 1000 clips/adv_test.pkl
[ -f clips/speech_test.pkl ] || $P gen.py phrases_speech.txt test 300 clips/speech_test.pkl
[ -f clips/jarvis_test.pkl ] || $P gen.py phrases_jarvis.txt test 600 clips/jarvis_test.pkl
date; echo GEN_DONE
[ -f feats/pos_train.npy ] || $P feat.py clips/pos_train.pkl feats/pos_train.npy end 2 1
[ -f feats/adv_train.npy ] || $P feat.py clips/adv_train.pkl feats/adv_train.npy end 1 2
[ -f feats/speech_train.npy ] || $P feat.py clips/speech_train.pkl feats/speech_train.npy any 1 3
[ -f feats/pos_test.npy ] || $P feat.py clips/pos_test.pkl feats/pos_test.npy end 1 4
[ -f feats/adv_test.npy ] || $P feat.py clips/adv_test.pkl feats/adv_test.npy end 1 5
date; echo FEAT_DONE
while ! grep -q ALL_DL_DONE logs/dl.log; do sleep 30; done
LAYER=64 MAX_NEG_W=1000 $P train.py 60000
date; echo TRAIN_DONE
