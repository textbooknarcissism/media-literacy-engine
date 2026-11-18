# Contributing to the Media Literacy Engine

Contributions are welcome from linguists, journalists, media-literacy researchers and software engineers. A careful correction or a good counter-example is worth as much here as new code.

## What the project is trying to do

1. Teach, not filter. MLE shows a reader the words doing the work in a headline. It does not try to catch every manipulative one ([position 17](docs/positions.md#17-mle-is-a-teaching-tool-not-a-filter)). It speaks only when the technique is plain and a reasonable reader could not argue the headline is fair. When a headline is borderline it stays silent.
2. Leave ordinary reporting alone. A false alarm on a responsible headline does more harm than a missed manipulative one. The benchmark gate in CI fails any change that lowers "left alone correctly" on a committed dataset. Catch rate is reported but is never a goal.
3. Explain every flag. A detection must match a readable character range that you could defend to the person who wrote the headline.
4. Stay neutral. The engine names a technique. Text shown to readers must never condemn an outlet, a journalist or a political position.
5. Report results honestly. State measured results with their limits, and do not present tuning-set performance as what to expect in the real world.

## Reporting problems

Please open an issue. A false positive is the priority: MLE flagged an honest headline, or named the wrong technique, or marked the wrong words. A missed headline is a candidate teaching example and not a bug, so say whether you think it is a clear case. Include the headline, the outlet, the flagged words if any, and what the article itself says, since that often settles the call.

## Proposing a rule change

1. The codebook only changes in numbered versions, and each one says why ([codebook](docs/benchmark/codebook.md), section 7). If your change needs a ruling the codebook does not give, open an issue and propose the ruling first.
2. A rule must describe a clear case seen across several headlines. Do not add a phrase or a pattern taken from a single headline, because the memorization lint will reject it. Held-out rows are never used to write or tune a rule.
3. Measure the false-flag risk first. Add your pattern and run the benchmark: it may match at most 0.5% of the tuning rows labeled `NONE`.
4. Run the checks listed under Development below.
5. Record the decision as a test. Add the headline to `core/flagging/src/test/resources/edge-cases.tsv` with what must and must not be flagged. `EdgeCasesTest` then stops a later rule pack from quietly undoing it.
6. Check the EVEN-HANDEDNESS section that `rule-eval` prints, to confirm the change does not introduce political or regional bias.
7. Put before-and-after numbers in your pull request. "Left alone" must not drop. The catch rate may move, but it is not the point.

Rule packs ship monthly, together with that month's flag review of what MLE flagged. A change that arrives between releases is welcome and waits for the next one.

## Development

You need JDK 17. `tools/check.mjs` also needs Node.js. Everything else runs through the Gradle wrapper.

| Command | What it does |
| :--- | :--- |
| `node tools/check.mjs` | Picks the right checks for what you changed and prints a short result. |
| `node tools/check.mjs gate` | Every unit test and the offline benchmark gate. Run this before a pull request. |
| `./gradlew test` | Unit tests for every module. |
| `./gradlew :tools:benchmark:run --args="flag '<headline>'"` | Shows what the rules find in one headline. |
| `./gradlew :tools:benchmark:run` | Regenerates `docs/benchmark-results.md`. Downloads the external datasets the first time. |
| `./gradlew :tools:benchmark:run --args="export-cases"` | Writes every labeled headline with the rules' verdict. Run it before and after a change and diff the two files to see exactly which headlines moved. |
| `./gradlew :tools:adversarial-fuzz:run` | 53 hand-written evasion and false-alarm cases. A regression check, not proof of robustness. |
| `./gradlew :tools:rule-eval:run --args="fixtures/headlines/harvested.tsv --fail-on-fp"` | Rule accuracy on the harvested set, with the even-handedness report. |
| `./gradlew :core:flagging:checkRulePack` | Lints the rule pack. |
| `./gradlew suite` | The four tools in order (harvest, fuzz, rule accuracy, benchmark). The harvest reports on live feeds and writes nothing. |
| `./gradlew publishToMavenLocal` | Publishes the library to your local Maven repository. |

Where things are:

| Path | What |
| :--- | :--- |
| `core/model` | The shared types. |
| `core/flagging` | `HeadlineFlagger`, the rule pack (`src/main/resources`) and the built-in patterns (`Signals.kt`). |
| `core/feeds` | Feed parsing, article text, story grouping. |
| `engine` | The single entry point, `MediaLiteracyEngine`. |
| `tools/` | The benchmark, the harvester, the labeling tool, the fuzz cases and the rule evaluator. |
| `fixtures/headlines` | The labeled headline sets. |
| `docs/benchmark` | The codebook, the review process and the monthly flag reviews. |

### The monthly flag review

This is the project's main measure. Once a month a person answers two yes-or-no questions about up to 50 headlines MLE flagged in that month's news.

1. Harvest: `./gradlew :tools:feed-harvest:haystack --args="--context-intake --since=YYYY-MM-01 --output=docs/benchmark/flag-review/YYYY-MM/intake.tsv"`
2. Sample: `./gradlew :tools:benchmark:run --args="flag-review sample --intake docs/benchmark/flag-review/YYYY-MM/intake.tsv --month YYYY-MM"`. This writes `sample.json` beside the intake. The same intake always gives the same sample.
3. Answer. Save the answers as `answers.json` in the same folder, in the shape `{"month": "YYYY-MM", "answers": {"f01": {"clear": true, "helps": true, "note": ""}}}`.
4. Score: `./gradlew :tools:benchmark:run --args="flag-review score --month YYYY-MM"`. This writes `report.md` and `result.json`.
5. Record each decision in `edge-cases.tsv`.

On Windows, `tools\monthly-flag-review.ps1` does steps 1 and 2. Headlines from a flag review have been seen, so they are never reused as a held-out set.

## Conduct and licensing

Be respectful and assume good faith. By contributing you agree that your contribution is licensed under the [Apache License 2.0](LICENSE), the same license as the rest of the code.
