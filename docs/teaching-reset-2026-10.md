# The teaching-tool reset (2026-10)

**Status:** design approved by the maintainer, 2026-10-22, section by section; all six steps built by 2026-10-23. The lasting position is `docs/positions.md` §17; this document keeps the reasoning and the plan as built.

## 1. Why

MLE is an educational tool. The README has said so from the start: "Not a perfect detector", "Cautious by design", and principle 7, "Honesty never counts against a headline". Between 2026-10-11 and 2026-10-20 the project drifted away from that and began to work like a filter:

- the codebook went through eight versions in nine days (v2 to v9) and the rules through eight packs (24 to 31), each an attempt to make labels and rules meet;
- catch-rate targets were set, missed and reset three times (§14, §16, the 70% decision rule);
- codebook v9 reversed the default ("clickbait unless proven otherwise", a tie goes to the technique) and flagged 1,035 more rows;
- a Strict mode with a learned word table was added to raise the catch rate.

Held-out catch rate stayed between about 24% and 45% through all of it. Chasing it is what churned the project, not the engine. (An earlier draft of this spec said the engine leaves 98.7% of held-out honest headlines alone; that was measured against the version 9 labels, which had relabeled many of the engine's own false flags as clickbait. Against the restored version 8 labels it is 96.6%, 1,230 of 1,273: about 1 wrong flag in 30, which is what §6 now watches.)

The maintainer named the cause: the project had come to be read like Pi-hole, an ad blocker. That model is right for a blocker and wrong for a teacher.

| | A filter (Pi-hole) | MLE, a teacher |
| :--- | :--- | :--- |
| Job | Stop the thing reaching you | Show you the thing and why it works on you |
| A miss | A bug: the ad got through | Normal: the reader reads as they would have anyway |
| A wrong flag | Minor: a page loads oddly | Serious: the reader learns the wrong lesson and stops trusting the tool |
| How it grows | Bigger lists, stricter modes | A small, stable set of clear, explainable cases |
| Success | Share blocked | When it speaks, it is right and it teaches |

## 2. What MLE is for

Most readers already know what clickbait, ragebait and loaded headlines are. Few have had one shown to them clearly, on the headline in front of them, in a way that helps them reflect on their own reading. MLE does that: it underlines the words, names the device and asks a question.

**MLE works when, whenever it speaks, the flag is clearly right and the explanation helps the reader stop and think.** Missing a headline costs little. A wrong or muddled flag costs a lot.

**Grey zone:** when a headline is borderline, MLE stays silent. There is one tier, not two.

## 3. Guardrails

These follow from §1 and are written into position 17 so a future filter move has a written reason to stop.

1. **A reported miss is not a bug.** It is a candidate teaching example. It becomes a rule only through §7.
2. **No stricter modes or tiers.** One standard; MLE speaks or stays silent.
3. **Catch rate is never a goal.** It is published with its interval for honesty, and no decision is taken to raise it.

## 4. The standard: codebook version 10, "the teaching standard"

- Keeps the decision rules 1–6 of version 8, the owner-approved rulings (fear word beside a stated harm, quoted alleged conduct, labeled opinion, service headlines, explainers, words not outlets).
- Replaces version 9's rule 7 with one bar: **flag only when the technique is plain on a first reading and a reasonable reader could not argue the headline is fair. When torn, choose `NONE`.**
- The four readings version 9 added (a thesis stated as fact in news, the outlet's own unattributed "could", a question about how something will turn out, an unease word over something still to come) are no longer grounds for a flag on their own. They count only where they are blatant under the existing technique definitions in §4 of the codebook.
- §2 of the codebook ("When you are torn … choose the technique") and §5 (worked examples written for v9) are revised to match. The v9 entry in §7 stays, with a note that version 10 reverses it.
- **The standard is frozen.** A change needs a new version approved by the maintainer and a §7 entry saying why. The aim is a few versions a year, not a week. Benchmark results are tagged with the codebook version.

**Labels.** The fixture changes of commit `aa2fb99` (the v9 re-label) are reverted, which returns every label to version 8. No new labeling run. The baseline (`fixtures/benchmark-baseline.json`) is hand-edited to match, never written with `--write-baseline`. The version 9 labeling run is kept as a record of what was tried.

**Docs.**
- `docs/positions.md` gains §17, "MLE is a teaching tool": §1–§3 of this document, in short. The targets of §14 and §16 and the 70% decision rule are marked retired. Earlier positions stay as history. The version line at the top is updated.
- `README.md`: "What MLE Is Not" gains "Not a filter", and the Evaluation section leads with flag accuracy.

## 5. Removing Strict mode

Removed entirely: `FlagMode`, `LearnedScorer`, `core/flagging/src/main/resources/weights-v1.json`, `FlagResult.learnedWords`, the `mode` parameter on `HeadlineFlagger` and `MediaLiteracyEngine` (and its alias in `Aliases.kt`), `StrictModeTest`, the `train-weights` tool (`TrainWeights.kt` and its entry in `Main.kt`), and the Strict section in `Report.kt`. Mentions in `README.md`, `docs/METHODOLOGY.md` and `docs/benchmark-results.md` are removed or rewritten.

Strict existed for two days and v1.0.0 is unpublished, so there is nothing to deprecate. Default-mode verdicts do not change: the gate must pass with an unchanged baseline before the label revert in §4 is applied.

## 6. Measurement

**Gate (unchanged):** no regression against `fixtures/benchmark-baseline.json`.

**Left-alone targets (kept):** held-out honest headlines left alone, at least 97.5% pooled and at least 96% in every set. Under the version 8 labels they are short (96.6% pooled; beats-2026 94.8%), so the wrong flags behind them are the first work the flag review and §7 take up.

**Removed:** the two caught targets (`Targets.POOLED_CAUGHT`, `Targets.FRESH_CAUGHT`) and their rows in the report's Targets section.

**Report order:**
1. Flag review (below), latest month first.
2. Precision per technique, already computed, moved to the top of each set.
3. Left alone, with the gate's verdicts.
4. Catch rate, with intervals, under a heading that says it is information and not a goal.

**The flag review (new, the main measure).**
- Monthly. Each rule-pack release goes out with that month's review.
- 50 headlines that MLE flagged, drawn from the month's fresh harvest, never from tuning rows. If the month has fewer than 50 flagged headlines, all of them are used.
- The maintainer sees each as a reader would (headline, highlighted span, pattern name, question) and answers two yes/no questions:
  1. Is this a clear case?
  2. Would the highlight and question help a reader stop and think?
- **Target: at least 90% yes on each question**, reported with its 95% interval.
- A "no" on question 1 is a candidate precision fix; a "no" on question 2 is a candidate wording fix (pattern name or question). Both go to §7.
- Results are stored under `docs/benchmark/flag-review/YYYY-MM/` (sample, answers, report) and carry the codebook version and rule pack.

**Kept in the background:** sealed sets, Wilson intervals, held-out discipline.

**No longer routine:** full re-labels, two-model labeling with adjudication, κ studies, model bake-offs. The tools stay in the repo for when a codebook version change calls for them.

## 7. How rules change

- **Candidates come from** misses the maintainer reports and "no" answers in the flag review.
- **A candidate is accepted only if** it is a clear case under the teaching standard; it matches at most 0.5% of tuning `NONE` rows; and the gate passes.
- Otherwise it is logged as "not a clear case" and dropped. It does not become a codebook argument.
- **Rule packs ship monthly** with the flag review, not as each miss arrives.

## 8. Keeping the monthly work small

- **`flag-review` Gradle tasks:** one draws the 50-row sample from the month's harvest and writes the review page's data; one scores the maintainer's answers and writes the report.

## 9. Order of work

Each step leaves the tests and the gate passing.

1. Remove Strict mode (§5). Gate passes with the baseline unchanged.
2. Codebook version 10 and the label revert (§4); baseline hand-edited; gate passes.
3. Targets and report order (§6).
4. `flag-review` tasks and the review page (§6, §8); the first review runs on the harvest from 2026-10-19 on.
5. The build and tooling fixes (§8).
6. Position 17 and the README (§4, §3).

## 10. Out of scope

- New detection rules. None are written as part of the reset.
- Any learned or on-device model (the maintainer's position in §14 stands).
- Apps built on MLE.
