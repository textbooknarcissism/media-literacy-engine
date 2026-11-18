# docs/benchmark

How the labels are made and how MLE is measured. Results are generated, not written by hand: [../benchmark-results.md](../benchmark-results.md).

| File | What |
| :--- | :--- |
| [codebook.md](codebook.md) | The labeling standard, version 13. Frozen between versions the maintainer approves. Section 7 lists every change and why. |
| [review-process.md](review-process.md) | How labels are made and checked. |
| [flag-review/](flag-review/) | The monthly flag review, MLE's main measure: one folder per month with the sample, the maintainer's answers and the report. |
| [spot-check-2026-10/](spot-check-2026-10/) | The maintainer's blind 200-row spot-check of the labels. It led to codebook version 8. |
| [model-bakeoff-2026-10.md](model-bakeoff-2026-10.md) | Whether a small language model could flag headlines. None was accurate enough, so no model runs on the device. |

MLE is a teaching tool, not a filter ([position 17](../positions.md#17-mle-is-a-teaching-tool-not-a-filter)). The reasoning behind each rule is in [../positions.md](../positions.md).
