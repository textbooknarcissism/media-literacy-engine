# Roadmap

MLE is a teaching tool, not a filter ([position 17](positions.md#17-mle-is-a-teaching-tool-not-a-filter)), so nothing here is catch-rate work. Every item either makes a flag more likely to be right, makes it teach more, or keeps the process sustainable.

## Where it stands

| Measure | Now | Target |
| :--- | :--- | :--- |
| Flag review, "is this a clear case?" (November 2026, 30 headlines; October was 28/33) | 28/30 (93.3%) | at least 90% |
| Flag review, "would the highlight and question help a reader?" (October was 23/33) | 28/30 (93.3%) | at least 90% |
| Honest headlines left alone, held-out, pooled | 1,238/1,275 (97.1%) | at least 97.5% |
| Honest headlines left alone, weakest set (beats-2026) | 73/77 (94.8%) | at least 96% |
| Catch rate, held-out, pooled | 111/274 (40.5%) | none; reported as information |
| Miss check: headlines left alone called an obvious miss (November 2026) | 23/50 | none; reported as information |

Current release: codebook version 13, rule pack 38 (`1.0.0`). Rule packs ship monthly with the flag review.

## Near term

1. **Close the precision gap.** Reach the 97.5% pooled and 96% per-set left-alone targets on held-out headlines without reading held-out rows directly during rule tuning.
2. **Automate the monthly review.** `tools/monthly-flag-review.ps1` does the harvest and the sample, but it has not been run yet. Run it as a single scheduled routine so each month's harvest and sampling need no manual setup.
3. **Reviewer consistency check.** Mix a few headlines from earlier months into each review sample unmarked to spot borderline cases where judgments shift.
4. **Practice mode.** Expand the browser-based practice deck in `tools/practice/` (currently 44 decided edge cases) and test with readers whether it improves spotting tricks without over-flagging plain headlines.

## Next

5. **Topic as a hint.** Decide whether apps should pass the article topic so literal words in gaming, sports, and entertainment stay quiet ([position 18](positions.md#18-a-flag-needs-a-construction-and-one-clear-technique)) without adding per-topic rules.
6. **Rule census.** Audit all 92 signals and 518 word-list terms for how often each fires on fresh news and its review record, and retire any rules without support.
7. **Harvester metadata.** Improve page-type and gallery detection and strip boilerplate summaries (such as outlet footers).
8. **One level or two?** Decide whether MLE needs both `SUBTLE` and `PROMINENT` levels ([open question 5](positions.md#open-questions)).
9. **Split `positions.md`.** Separate the retired filter-period history (positions 14–16) from the active positions.

## Later

10. **Multiple reviewers.** Support multiple readers on the monthly review page, recorded separately.
11. **Maven Central.** Publish `1.0.0` artifacts to Maven Central.

## Not planned, on purpose

Each of these was tried, or came close, during the filter period (positions 14 to 16).

- Catch-rate targets, or any work whose goal is to raise the catch rate.
- A stricter mode, or a "worth a second look" tier. MLE speaks or stays silent.
- A language model or a learned table on the device. Language models are development tools only.
- Rating, ranking or showing rates for outlets. Verdicts stay source-blind.
- Growing the gold benchmark every week. Rule packs ship monthly, with the review.
