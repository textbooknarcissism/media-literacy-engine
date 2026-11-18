# Model bake-off, October 2026

> The harness described here (`tools/ml`) was removed on 2026-10-23, because the question is closed (no language model on the device). It is not in this repository.

Can a small open-source language model give the engine the reading of nuance its phrase rules lack? This report answers that for three small models and a rules + model hybrid, against pass criteria fixed before any result. Run on 2026-10-19 with `tools/ml/bakeoff.py` (since removed). Rule pack 27, codebook version 6.

**Verdict: the hybrid fails all three pass criteria that test catching more, so no model goes into the engine now.** The models rank headlines better than the rules, by a wide margin on fresh headlines. But at the strictness MLE requires (at least 97.5% of honest headlines left alone) they catch too little. The bottleneck is training data: 345 flagged headlines. A large language model reading the codebook agreed with the final labels on 329 of 332 fresh rows, which points to the next step: distillation (§5).

## 1. Why

On 2026-10-19 the maintainer reported that obvious clickbait goes unflagged and some honest headlines are flagged. Their 62 screenshot headlines (`fixtures/headlines/owner-reported-2026-10.tsv`) and a fresh week of headlines (`week-2026-10-19`) confirm it:

| Set | The rules: left alone | The rules: caught |
| :--- | :--- | :--- |
| Pooled held-out (broadsheet, beats, context, harvested) | 1251/1270 (98.5%) | 89/154 (57.8%) |
| week-2026-10-19, every row (unseen by the rules) | 262/271 (96.7%) | 17/86 (19.8%) |
| owner-reported | 41/46 (89.1%) | 2/16 (12.5%) |

The rules were written phrase by phrase. They miss variants ("should know" where they know "need to know") and judgment calls ("a sleeper Senate battleground", "Nobody is safe").

## 2. What was compared

| Id | Candidate |
| :--- | :--- |
| R | The rules, pack 27 |
| P | The rules plus a grammar guard (spaCy): an `unnamed_demonstrative` hit is dropped when its noun phrase holds a proper noun ("these Silicon Valley haunts") |
| M1 | `ENTUM-AI/roberta-clickbait-classifier`, off the shelf (trained mostly on Chakraborty 2016: BuzzFeed against the NYT) |
| M2 | `distilroberta-base` fine-tuned on our tuning rows (2,729 rows, 345 flagged; input: article type and headline) |
| M3 | The same, first trained one epoch on Webis-Clickbait-17 (19,538 crowd-rated posts) |
| H:Mx | Hybrid. The rules decide, except in two cases. **Promote:** a sub-threshold rule hit (score at least 0.35) becomes a SUBTLE flag when the model's probability is at least t_hi; the hit's words are what gets underlined. **Veto:** a rule flag is dropped when the model's probability is at most t_lo. |
| H+ | H plus a model-only flag when no rule hit at all (reported only; nothing would be underlined) |
| HP | H with the grammar guard |

Thresholds were chosen on a fixed fifth of tuning rows (validation, 629 rows). The rule: catch the most bait while leaving alone at least as many honest headlines as the rules do there (98.6%). Held-out, maintainer and fresh-week rows were never used for training or thresholds; the harness asserts it. Its first run caught five week rows that were also in `context-2026` (stories still in their feeds a week later). They were dropped from the week set, and the intake now skips them (`ContextIntake.labelledIds`).

## 3. Results

Left alone / caught; held-out rows only, except where a set says "all rows".

| Candidate | Pooled held-out | week, held-out | week, all rows | owner-reported |
| :--- | :--- | :--- | :--- | :--- |
| R | 98.5% / **57.8%** | 95.8% / 25.0% | 96.7% / 19.8% | 89.1% / 12.5% |
| P | 98.5% / 55.2% | 98.6% / 21.4% | 97.8% / 17.4% | 93.5% / 12.5% |
| M1 alone | 98.6% / 7.8% | 98.6% / 10.7% | 97.4% / 8.1% | 100% / 6.2% |
| M2 alone | 98.1% / 31.2% | 98.6% / 14.3% | 99.3% / 9.3% | 97.8% / 6.2% |
| M3 alone | 98.1% / 31.8% | 97.2% / 21.4% | 99.3% / 16.3% | 97.8% / 18.8% |
| H:M1 | 98.5% / 57.8% | 95.8% / 25.0% | 96.7% / 19.8% | 89.1% / 12.5% |
| H:M2 | 98.2% / 51.9% | 97.2% / 17.9% | 98.2% / 19.8% | 93.5% / 18.8% |
| H:M3 | 98.7% / 46.1% | 97.2% / 21.4% | 98.5% / 14.0% | 95.7% / 18.8% |

H+ equals H everywhere: the chosen t_max switched model-only flags off. HP trades a few catches for fewer false flags. The validation step chose a promote threshold of 0.98 for all three models, so promotion almost never fired; the hybrids mostly *vetoed*. M1 changed nothing.

**How well each score ranks headlines**, with no threshold. AUC is 0.5 for a coin toss and 1.0 for a perfect ranking.

| Score | AUC, pooled held-out | Caught at 97.5% left alone | AUC, week (all rows) | Caught at 97.5% left alone |
| :--- | :--- | :--- | :--- | :--- |
| Rule score | 0.819 | 0.58 | **0.618** | 0.08 |
| M1 | 0.709 | 0.12 | 0.661 | 0.05 |
| M2 | 0.826 | 0.33 | 0.772 | 0.20 |
| M3 | **0.849** | 0.36 | **0.800** | 0.29 |

The models order headlines better than the rule score does, most of all on unseen headlines, where the rule score barely ranks (0.62, near a coin toss). But a flag has to be nearly always right, and at that strictness they catch about a third.

**On the phone (M3):** int8 ONNX is 82.5 MB, runs in 4.8 ms per headline on one CPU thread, gives byte-identical output on repeat runs, and agrees with the full-precision model on 99% of headlines. Size and speed are not the obstacle; accuracy is.

## 4. Pass criteria (fixed before the run)

| Criterion | Needed | Best hybrid | Result |
| :--- | :--- | :--- | :--- |
| 1. Pooled held-out caught | at least 70% | 57.8% (H:M1, the same as the rules); 51.9% (H:M2) | **Fail** |
| 2. Pooled held-out left alone | at least 97.5%, no set down more than 1 point | 98.2–98.7% | Pass |
| 3. Unseen week (held-out): beat the rules on caught, at most 1 point lost on left alone | more than 25.0% caught | 21.4% (H:M3) | **Fail** |
| 4. Maintainer set: fix at least half the rules' 19 errors | at least 10 | 4 (H:M3: 3 false flags cleared, 1 miss caught) | **Fail** |

## 5. What this means, and what to do

1. **No model ships in the engine now.** METHODOLOGY.md's position stands for the present, for a new reason: not that a model can't be deterministic or small (this one is both), but that one trained on 345 examples isn't accurate enough to flag on its own word.
2. **The rules need the family-by-family work regardless.** On headlines written after they were, their ranking falls from 0.82 to 0.62. They fit the older sets, not this week's news. `week-2026-10-19` (tuning rows) and the maintainer set are now the material for that work; the week held-out rows stay the honest test.
3. **Revised the same day: distil into rules, not into a model.** The maintainer ruled out any model on the device: rules run in the app, language models are development tools. So the large model's reading goes into rules instead. A large model labeled 1,600 unseen harvested headlines (`mined-2026-10`) with the pattern and the exact trigger words, and the triggers were turned into rule families, each checked against tuning rows before it was kept (`docs/positions.md` §14). The original recommendation follows for the record.

   *Original recommendation:* **The route to nuance is distillation.** A large model reading the codebook already makes these calls: its blind first pass agreed with the adjudicated label (flagged or not) on 329 of 332 week rows, with 24 more marked unsure. That figure is optimistic, because adjudication covered only rows where the first pass and the rules disagreed. Have such a model label tens of thousands of harvested headlines, train the small model on those, and re-run this bake-off with the same criteria. It needs:
   - a steady supply of headlines: Haystack runs, plus the 1,538 unreviewed rows in `harvested-live-intake.tsv`;
   - a labeling budget.
4. **The grammar guard (P) is not adopted as it is.** It cleared 3 week false flags and 2 maintainer false flags, but cost 4 harvested catches. It goes into the rule work as a narrower guard.

## 6. Caveats

- **Labels are model-made.** Two language models labeled them (a blind first pass, then adjudication); the maintainer spot-check is pending, and the label-leniency audit has not run. Codebook v6 calls the maintainer has not reviewed in bulk (unnamed researchers or officials as anonymous sourcing; pronoun subjects) shape the week labels.
- **M1's training data overlaps `chakraborty-legacy`**, which is not scored here.
- **The held-out sets are small** (28 flagged week rows), so one row moves a rate by 3–4 points.
- **Scoring reproduces exactly across runs** (same seed and threshold tables). Retraining was not repeated to confirm the weights reproduce.
