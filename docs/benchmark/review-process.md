# Review process

How a person reviews model-produced labels, adopted 2026-10-12 after the beats-2026 spot-check. It applies to every spot-check sheet and to any label file under review.

## Why

The reviewer labels each headline by their own reading. Some calls depend on facts the headline doesn't give:
- who said the quoted words;
- whether the article tells the reader it is opinion or a review;
- what stage the science is at;
- whether the event supports the fear words;
- what the headline holds back and whether the article supplies it.

The model reviewer labels against the codebook text and has the same gap. Reading the story settles those facts, so neither side has to guess.

## When the model labels

Adopted 2026-10-12, after the beats-2026 story check showed that many unsure rows needed a fact rather than a rule.

- **Read before marking a row unsure.** When the headline alone doesn't settle a label because it depends on a fact like those listed under "Why", the model reviewer reads the outlet's feed summary. If the summary doesn't settle it, the reviewer reads the article, found as in step 2.
- **Record what was read** in the same three columns as the story check: `story_link`, `story_facts` and `story_suggestion`. Label the row on those facts, and say in `note` which fact decided it.
- **Mark a row unsure only when the codebook text leaves the call open,** not when a fact is missing. If the article can't be found, write `not found` in `story_link` and mark the row unsure.
- **Keep the story columns hidden from the reviewer on random rows** until the reviewer has chosen, as the model's label is (step 1). They show which way the model leaned.
- The rules in "What the story may and may not do" apply here too.

## Two-model first pass

Adopted 2026-10-19, to label fresh harvests at a fraction of the cost of running the larger model on every row.

- **A smaller model labels blind first.** It labels each row against the codebook without the engine's verdict, and marks a row `UNSURE` when the codebook or the summary does not settle it.
- **A larger model adjudicates the rest.** It reads every row where the first label differs from the engine's level, and every `UNSURE` row, and decides it under "When the model labels" above (reading the story where a fact is missing). Rows where the first label and the engine agree keep the first label.
- **The file header says so.** A set labeled this way names both models and counts how many rows each one decided. The engine's verdict is used only to pick rows for adjudication, and is never shown to the reviewer.
- **Audits work the same way.** The smaller model reads rows blind and returns only the rows it disagrees with; the larger model adjudicates those; a row that shows a gap in the codebook goes to the reviewer as a question, not a label change.

Agreement between a model and the engine is not evidence that both are right: they can share a blind spot. The reviewer's spot-check (below) is what tests the labels.

## The steps

1. **The reviewer labels first, without help.** On random rows the model's label stays hidden until the reviewer has chosen, and nobody suggests a label. This keeps the reviewer-model agreement figure free of anchoring. The figure is computed on these first-pass labels and never recomputed after the later steps.
2. **The story check.** The model reviewer reads the article, unless it already did when labeling, for:
   - every row where the reviewer's label differs from the model's;
   - every row the model marked unsure;
   - every row the reviewer asks about.

   It finds the article from the `link` column, or by searching for the exact headline on the outlet's site. For each row it records:
   - `story_link`: the URL actually read;
   - `story_facts`: one or two sentences of facts that bear on a codebook rule, each naming the rule;
   - `story_suggestion`: a suggested level and techniques, with the rule it rests on. It is a suggestion; the reviewer decides.

   If the article can't be found or read, it writes `not found` and suggests nothing.
3. **The reviewer decides.** The reviewer keeps or changes each label in `owner_level` and `owner_techniques`, and may add an `owner_note`. Where the story changed the call, the note says what fact changed it.
4. **Apply.** The reviewer's final labels go into the dataset with labeler `owner`. Each change is logged with its reason in the audit for that set, and the dataset header's counts are updated.
5. **Codebook.** A row where the reviewer's reading departs from the codebook text is listed in the audit as input to the next codebook version. It is not reverted to the text.

## What the story may and may not do

- **The label still judges the headline's own words** (codebook §1): readers see the headline, not the article. The story establishes facts the headline's words depend on, such as who said them, what kind of article it is, or how early the work is. It never adds a technique that isn't in the headline.
- **Example where the story matters:** "Man held over 'bomb plot'" is `vague_sourcing` when nobody stands behind the quote. If the article shows police charged him with a bomb plot, the quote still has no speaker in the headline, so the label stands. The fact goes in `story_facts`, and the reviewer judges whether it changes the call.
- **Store only the link and a sentence or two of facts,** never the article text.
- **Held-out rows get the same check, but stay out of rule work:** their facts are never used to tune rules or as examples in docs.
- **The engine's verdict and score are never shown** at any step.

## Sealed sets

Held-out rows are never read, but their totals are looked at after every rule pack, and a rule family kept because the held-out number rose has been chosen by them. After enough rule packs the held-out split is a validation set, not a test. A sealed set is a test nobody looks at, not even its totals, until a fixed day.

1. **Harvest.** Run `harvest` (`context_intake`, `since=` the last harvest) with the output under `build/`, never `fixtures/`.
2. **Label** as for any fresh set: the blind first pass, then adjudication, the engine's verdict never shown. Whoever adjudicates does no rule work on those rows afterwards; rule work starts fresh, without sight of them.
3. **Seal.** Write the labeled file to `fixtures/sealed/<id>.tsv` in a command carrying `MLE_SEALING=1`, delete the intake, and register the set in `Datasets.kt` with `fresh = true` and `sealedUntil` about four weeks out. Stage it with `MLE_SEALING=1 git add fixtures/sealed`, then commit.
4. **Until the day** the benchmark shows the set's row count and nothing else. `export-cases` skips it, and the memorization lint protects every row. Nobody working on rules reads or searches `fixtures/sealed/`.
5. **On the day** run the benchmark once. The whole set is held-out. Record its totals, with intervals and the rule pack version, in `docs/positions.md`. That is the number to quote.
6. **Retire** the set: move the file to `fixtures/headlines/` (again with `MLE_SEALING=1`), drop `sealedUntil` (it then splits 70/30 like any fresh set), add its baseline entry by hand, and seal the next one.

The seal is a speed bump, not a lock: anyone can open the file. It works because breaking it takes a deliberate step.

Every headline rate in the benchmark report carries a 95% Wilson interval, and the Left alone section says whether each left-alone target is met or missed beyond noise (catch rate has no target; see docs/teaching-reset-2026-10.md §6). A change of a few headlines inside overlapping intervals is not evidence that a rule pack helped.

## Running it

Reading articles needs network access to the outlets' sites. On a locked-down machine the network policy must allow them. Web search alone is not enough: it often finds a similar story rather than the outlet's article.

A plain scripted request (curl) is blocked by bot checks on The Atlantic, Axios, New Statesman, MarketWatch, the Daily Star, Manchester Evening News, the FT and Politico, even from a home connection. For those, use a regular Chrome browser. Failing that, use a Wayback Machine or archive.today snapshot, and say so in `story_link`.
