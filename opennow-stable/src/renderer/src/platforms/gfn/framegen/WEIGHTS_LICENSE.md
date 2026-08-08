# Model weights license

The Framegen **code** is MIT (see `LICENSE`). The **model weights**
(`.bin`/`.json` blobs shipped in releases: `rt_tfact*`, `rt_slim`, `rt_1blk`,
`rt_sr`, etc.) are distributed under separate terms:

**Non-commercial research and personal use only.**

You may:
- use the weights personally (watching video, demos, benchmarks),
- study, evaluate and compare them in research,
- redistribute them unchanged with this notice.

You may not:
- sell the weights or bundle them into a paid product or service.

## Why the split

The weights were trained by distillation from a RIFE-family teacher
([hzwer/ECCV2022-RIFE](https://github.com/hzwer/ECCV2022-RIFE), the RIFE_m
checkpoint) on open
movies (Sintel, Tears of Steel, Elephants Dream - Blender Foundation, CC-BY)
and xiph.org test footage. The teacher's own license/dataset chain is not
clean enough for us to honestly claim fully-free weights.

A fully-freely-licensed weight set (clean teacher or from-scratch training on
clearly-licensed data) is on the roadmap; when it ships, those weights will be
usable without restriction. Training your own weights with
`tools/train_student.py` on your own data produces weights that are yours.
