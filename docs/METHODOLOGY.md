# How MLE works, and why

This document covers the research behind the Media Literacy Engine, how the rules map to it, and how the engine is tested. The modules are `:core:model`, `:core:flagging`, `:core:feeds` and `:engine`.

## 1. The approach

MLE began with how news is reported and with what outlets treat as newsworthy, more than with what any one story says. Watching headlines over time showed a small set of techniques coming back again and again, and each of them has been studied: linguists have described how they work on a reader, and clickbait research has built systems to detect them. That research mostly ends at a label, though. A headline gets marked as clickbait, or hidden, and the reader learns nothing they can use on the next one. MLE takes the research a step further, toward the reader: it names the technique, shows the exact words, and asks one question. Choosing what counts as news is out of its reach, because a headline cannot show what was left out, so the engine works on wording alone.

Most work on clickbait treats it as a yes-or-no classification and solves it with a neural network or a large language model. MLE does something narrower, for three reasons.

1. It is meant to teach. The goal is a reader who recognizes a technique on sight. Filtering or blocking headlines would not get there.
2. It has to show its work. A classifier that returns a confidence score usually cannot say which words produced it. MLE returns the exact character range (`PatternHit.range`), so an app can underline the words inside the headline.
3. It has to run on a phone, offline. The engine is plain Kotlin, takes under a millisecond per headline, and makes no network requests.

## 2. The research behind each technique

The engine's eleven-technique taxonomy started from published work in linguistics, rhetoric and journalism research:

### A. Forward-reference and withheld detail (`curiosity_gap`)
Blom & Hansen (2015, [*Click bait: Forward-reference as lure in online news headlines*](https://doi.org/10.1016/j.pragma.2014.11.010), *Journal of Pragmatics*, 76:87–100) showed that clickbait headlines often use words that point forward to the article (`this`, `these`, `here's what they found`, `the one thing`) without saying what they refer to. Scott (2021) later found the same pattern in another corpus (see §F).

In the engine, signals such as `cataphoric_deictic_lead`, `unnamed_demonstrative`, `withheld_answer_here`, `ingroup_identity_hook` and `teaser_prefix` look for those unresolved pointers, and `SummaryContextResolver` checks whether the RSS summary supplies the missing answer. Plain explainers that name their subject ("here's why Apple's Mac Studio has become so expensive") are left alone (codebook rules 4–5).

### B. Hedging and unnamed sources (`speculative_hedge`, `vague_sourcing`)
Halliday & Matthiessen (2014, *An Introduction to Functional Grammar*) and Kovach & Rosenstiel (2014, *The Elements of Journalism*) describe how modal verbs (*could, may, might, set to*) mark how certain a writer is, and how attribution lets a reader check a claim. In headlines, speculative verbs can turn an unconfirmed possibility into the main event, while unnamed sources (*"sources say", "experts warn"*) borrow authority without giving the reader anyone to check.

The engine weights `speculative_hedge` low (0.35) and turns it off when the headline states its own scope or conditions (`cancelIfPresent`). For `vague_sourcing`, it separates institutional attributions (*"officials say"*) from anonymous gossip (*"pals fear"*).

### C. Combat metaphors and staged fights (`manufactured_rage`)
Lakoff & Johnson (1980, [*Metaphors We Live By*](https://press.uchicago.edu/ucp/books/book/chicago/M/bo3637992.html)) and Entman (1993, [*Framing: Toward Clarification of a Fractured Paradigm*](https://doi.org/10.1111/j.1460-2466.1993.tb01304.x), *Journal of Communication*) examine how framing choices shape how readers see a disagreement. One common frame turns ordinary political or legal disputes into physical fights (*"slams", "blasts", "eviscerates", "war of words", "warning shot"*).

The engine combines American (*"slams"*) and British (*"fury", "backlash", "swipe"*) conflict terms with `conflict_staging` patterns that match one person's response to another person's remarks.

### D. Alarming words and tabloid typography (`manufactured_fear`, `opinion_as_fact`)
Chakraborty et al. (2016, [*Stop Clickbait: Detecting and Preventing Clickbaits in Online News Media*](https://arxiv.org/abs/1610.09786), [GitHub Dataset](https://github.com/bhargaviparanjape/clickbait)) and Potthast et al. (2018, [*Crowdsourcing a Large Corpus of Clickbait on Twitter*](https://aclanthology.org/C18-1127/), [Webis Archive](https://webis.de/data/webis-clickbait-17.html)) are examples of clickbait detection work built on surface cues of this kind: frightening words (*"chilling", "nightmare", "horror", "terrifying"*) with no indication of likelihood or who is affected, all-caps emphasis (*"MORE", "ALL", "BLADE"*), question headlines, and repeated exclamation marks. MLE's signals for these are the project's own, and §5 says how they are tested.

The engine filters out literal reporting of real harm, isolates `tabloid_shout_caps` with an acronym allowlist so normal abbreviations do not trigger it, and flags two kinds of question headline: leading questions that carry a verdict (`leading_question`) and Betteridge-style superlative questions (`betteridge_superlative_question`).

### E. Early-stage science claims (`science_overhype`)
Sumner et al. (2014, [*The association between exaggeration in health-related science news and academic press releases*](https://doi.org/10.1136/bmj.g7015), *BMJ*, 349:g7015, [PubMed](https://pubmed.ncbi.nlm.nih.gov/25498121/)) found that health news often exaggerates by presenting lab or animal findings as direct breakthroughs for people.

The engine pairs breakthrough claims (`HEADLINE_SCIENCE_CLAIM`) with `SummaryContextResolver`, which checks the RSS summary for early-stage qualifiers (`in mice`, `in rodents`, `cell cultures`, `in vitro`) and surfaces that caveat next to the flag when it is missing from the headline.

### F. Recent Academic Literature (2021–2026)
What each study reports, and how far MLE relies on it. A citation explains why a category or signal exists; it does not validate a rule. Where a rule goes further than a study tested, it is a project heuristic, judged by the benchmark (§5) and not by the citation. Bibliographic details were checked against the publishers' records on 2026-10-11. Where MLE takes its own position because research is silent or draws the line elsewhere, the position, its reasons and MLE's own evidence are set out in [positions.md](positions.md).

1. **The curiosity gap as unresolved reference (2021):**
   - *Reference:* Scott, K. (2021), [*You won't believe what's in this paper! Clickbait, relevance and the curiosity gap*](https://doi.org/10.1016/j.pragma.2020.12.023) (*Journal of Pragmatics*, 175:53–66).
   - *What it reports:* A relevance-theoretic corpus study, run on the Chakraborty et al. (2016) headlines, finding two kinds of expression significantly over-represented in clickbait: definite referring expressions, and superlatives and intensifiers. Both invite the reader to open a mental file on something the headline gives little or no content for.
   - *How MLE uses it:* The rationale for `unnamed_demonstrative` ("in this city", "these stocks", never named) and `withheld_answer_here` ("here's what they found"). The patterns and their exclusions are MLE's own, tuned on Webis-Clickbait-22 and the 2026 harvested headlines.
2. **Deceptive clickbait in conventional dress (2023):**
   - *Reference:* Scott, K. (2023), [*'Deceptive' clickbait headlines: Relevance, intentions, and lies*](https://doi.org/10.1016/j.pragma.2023.10.004) (*Journal of Pragmatics*, 218:71–82).
   - *What it reports:* Describes a newer style of clickbait that closely resembles traditional headlines but presents mundane stories as having high news value. Argues that the reader is entitled to take that reading as the intended one, so responsibility lies with the writer, and that some such headlines count as lies.
   - *How MLE uses it:* Why MLE looks for techniques in broadsheet register as well as tabloid register, and judges the words rather than the outlet (codebook rule 6). The paper does not discuss reassurance or negation.
3. **Tactic-level taxonomy (2025):**
   - *Reference:* Nofar, L., Portal, T., Elbaz, A., Apartsin, A., & Aperstein, Y. (2025), [*An Interpretable Benchmark for Clickbait Detection and Tactic Attribution*](https://arxiv.org/abs/2509.10937) (arXiv:2509.10937).
   - *What it reports:* Builds a synthetic benchmark by rewriting real headlines with a fixed list of ten tactics (among them curiosity gap, ambiguous references, direct appeals, unfinished narratives and provocative questions), then trains models to detect clickbait and to name the tactic used.
   - *How MLE uses it:* A cross-check that MLE's withheld-detail signals correspond to recognized tactics: ambiguous references (`unnamed_demonstrative`) and direct appeals (`prescriptive_behavioral_imperative`, `cataphoric_second_person_gap`). It reports nothing about which tactics drive engagement, and its headlines are model-rewritten rather than observed.
4. **Systematic review (2024):**
   - *Reference:* Jácobo-Morales, D., & Marino-Jiménez, M. (2024), [*Clickbait: Research, challenges and opportunities – A systematic literature review*](https://doi.org/10.30935/ojcmt/15267) (*Online Journal of Communication and Media Technologies*, 14(4):e202458).
   - *What it reports:* Reviews 165 studies published 2015–2023, groups them into four research approaches, and identifies eleven properties of clickbait.
   - *How MLE uses it:* Background on how the field defines clickbait. No MLE signal is derived from it.
5. **Feature-based detection (2021):**
   - *Reference:* Pujahari, A., & Sisodia, D. S. (2021), [*Clickbait detection using multiple categorization techniques*](https://doi.org/10.1177/0165551519871822) (*Journal of Information Science*, 47(1):118–128).
   - *What it reports:* A hybrid classifier combining eleven hand-crafted features with sentence-structure features and clustering, evaluated with decision trees, random forests and SVMs.
   - *How MLE uses it:* Evidence that hand-crafted surface features carry real signal. It does not study direct address combined with threat words.
6. **What a large model attends to (2025):**
   - *Reference:* Alarfaj, F. K., Muqadas, A., Khan, H. U., & Naz, A. (2025), [*Clickbait detection in news headlines using RoBERTa-Large language model and deep embeddings*](https://doi.org/10.1038/s41598-025-30229-5) (*Scientific Reports*, 16:691).
   - *What it reports:* RoBERTa-Large reaches 97% accuracy on the Chakraborty et al. (2016) headlines. LIME and SHAP explanations attribute clickbait predictions to affective and curiosity words and sensational modifiers and verbs, and non-clickbait predictions to factual nouns and named entities.
   - *How MLE uses it:* Consistent with MLE scoring the wording rather than the topic. It does not single out particular words such as "this", "slams" or "could".
7. **Compact features at low latency (2026):**
   - *Reference:* Kuntur, S., et al. (2026), [*Clickbait detection: quick inference with maximum impact*](https://arxiv.org/abs/2604.08148) (arXiv:2604.08148).
   - *What it reports:* Combines text embeddings with six heuristic features; graph-based models stay competitive at much lower inference time.
   - *How MLE uses it:* Supports MLE's lightweight, on-device design.
8. **Interpretable surface cues (2026):**
   - *Reference:* Michaluk, W., Urban, T., Kubita, M., Kuntur, S., & Wróblewska, A. (2026), [*Click it or Leave it: Detecting and Spoiling Clickbait with Informativeness Measures and Large Language Models*](https://arxiv.org/abs/2602.18171) (arXiv:2602.18171).
   - *What it reports:* Adds fifteen explicit informativeness features to text embeddings; the best model (XGBoost) reaches an F1 of 0.91. The most salient cues are second-person pronouns, superlatives, numerals and attention-grabbing punctuation.
   - *How MLE uses it:* Independent support for MLE's second-person and superlative-listicle signals. A number on its own is not flagged: a list headline that names its subject is ordinary service journalism (codebook rule 4).
9. **A headline against its own article (2023):**
   - *Reference:* Broscoțeanu, D.-M., & Ionescu, R. T. (2023), [*A Novel Contrastive Learning Method for Clickbait Detection on RoCliCo: A Romanian Clickbait Corpus of News Articles*](https://aclanthology.org/2023.findings-emnlp.640/) (*Findings of EMNLP 2023*, 9547–9555; [arXiv:2310.06540](https://arxiv.org/abs/2310.06540)).
   - *What it reports:* Introduces a corpus of 8,313 Romanian news articles labeled for clickbait, and a contrastive model that places a headline near its own article's text when it is not clickbait and far from it when it is.
   - *How MLE uses it:* Close in spirit to `SummaryContextResolver`, which compares a headline with its own feed summary. The paper does not compare outlets; `StoryFramingComparator` (§6) is a project design.
10. **Opinion labels and credibility (2024):**
   - *Reference:* Otis, A. (2024), [*The effects of transparency cues on news source credibility online: An investigation of 'opinion labels'*](https://doi.org/10.1177/14648849221129001) (*Journalism*, 25(1):198–217).
   - *What it reports:* In an experiment with 389 participants reading a feed of biased and unbiased stories from one source, labeling the opinion pieces significantly raised the source's perceived credibility.
   - *How MLE uses it:* Support for codebook rule 2. A signed or labeled opinion piece has already told the reader whose judgment it is, so neither `opinion_as_fact` nor `loaded_question` applies to it.

**Project heuristics with no study behind them.** Three fear signals added in rule pack 23 were credited to items 2–6, none of which examines these constructions. Those citations have been withdrawn. `pseudo_reassurance_alarm` ("you probably aren't going to get the plague") and `temporal_suspension_threat` ("won't collapse this winter (for now)") have not matched one headline in any tuning split: harvested, broadsheet-2026, Webis-Clickbait-22 or the legacy Chakraborty set. `survival_challenge_frame` ("can your savings survive the budget?") has matched one legacy headline. The benchmark therefore neither supports nor contradicts them. They stay as the project's own position (2026-10-11): reassurance wrapped round a catastrophe is treated as a fear appeal, and labeled examples will test that as they are found. By contrast, `prescriptive_behavioral_imperative` and `cataphoric_second_person_gap` match 15 Webis-Clickbait-22 tuning posts and one harvested headline labeled prominent, and no headline labeled NONE in the 2026 tuning splits.

---

## 3. The Eleven Techniques

The eleven techniques, with what each looks for and what it deliberately leaves alone:

| ID | Display Name | What it looks for | What it leaves alone |
| :--- | :--- | :--- | :--- |
| `manufactured_fear` | **Alarmist framing** | Threat words without who is at risk or how likely it is; fear words on a real event or its footage (*"Terrifying footage…"*, *"horror crash"*, codebook v3); pseudo-reassurance alarms, deferred-disaster phrasing, and "can X survive" framing. | Plain reports of real harm (`deadly crash`, `bridge collapse`, `amber alert`); sad words and genres (*"heartbreaking"*, *"horror film"*). |
| `manufactured_rage` | **Conflict framing** | Combat metaphors applied to political or institutional disagreements, and somebody's words staged as a fight (*"responds to X's comments"*, codebook v3). | Direct quotes, sports fixtures, institutions responding to events, and real military strikes (*"launches attack on Kyiv"*). |
| `speculative_hedge` | **Speculation** | Hedging verbs (*could, may, might*) framing an unconfirmed scenario as the news. | Weighted low (0.35) so lone hedges in ordinary economic or weather reporting are never flagged. |
| `vague_sourcing` | **Vague sourcing** | Anonymous or uncheckable attributions (*"sources say"*, *"pals fear"*), and short quoted dramatic claims with nobody named behind them. | Institutional attributions (*"officials say"*) score lower than gossip; proper names are excluded, and scare quotes count in full only when they carry an allegation, charge, loaded label or alarm with nobody named behind them (codebook v3). |
| `study_as_proof` | **Study as proof** | An unnamed study, survey, poll or data set offered as proof of the headline's claim (*"…, study finds"*, *"Poll:"*, *"Data shows…"*); split from `vague_sourcing` in codebook v13. | Stands down when the headline, summary or story opening names who did it (codebook v12); never claims the finding was cherry-picked, and asks the reader to check what else the study found. |
| `curiosity_gap` | **Withheld detail** | Forward-pointing words whose referent is never named (*"this city"*), announced answers (*"Here's why…"*, *"X explains why…"*, *"X reveals how…"*), withheld statements (*"breaks silence"*, *"reacts to"*), and second-person teases. | Plain questions, topic labels and service phrasing (codebook rules 4–5), though an announced answer is not an explainer (codebook v3); RSS summary resolution checks teases and pulls out clean answers; double-teases are suppressed. |
| `urgency_pressure` | **Manufactured urgency** | Breaking-news phrasing and artificial time pressure on stories that are not urgent. | Scheduled civic deadlines (*"filing deadline nears"*) and official emergency alerts. |
| `science_overhype` | **Early science** | Breakthrough or cure claims that omit lab-stage, animal-model or simulation caveats. | Canceled immediately if the headline states the caveat (*"in mice"*, *"in lab tests"*); checks the summary for buried animal testing. |
| `opinion_as_fact` | **Opinion as fact** | Value judgments or directives stated as plain fact in a news voice. | Never applied to signed or labeled opinion (bylines, *"Opinion:"*, reviews, letters) or to quoted words (codebook rules 1–2); separated from factual event reporting. |
| `loaded_question` | **Loaded question** | A question that carries a claim, verdict or worry the headline does not state outright: a verdict (*"Was X right to…?"*, *"Is it time for X to quit?"*), a scare (*"Has the next financial crisis begun?"*), insinuation or sarcasm (*"Is X really…?"*, *"Why now?"*, *"What could possibly go wrong?"*), a nudge after a statement (*"… Should he?"*) and a hook aimed at the reader (*". Is your backup ready?"*); split from `opinion_as_fact` in codebook v14. | Plain and open questions (*"Can Labour end rough sleeping?"*), questions that doubt a claim (*"…: Does it?"*), service questions, a reader's dilemma (that is `curiosity_gap`), and signed opinion, reviews and letters (codebook rules 2, 5 and 7). |
| `social_scraping` | **Social scraping** | A crowd of unknown size and its reaction reported as the news: *"Fans are furious over…"*, *"People are just realizing…"*, *"The internet reacts to…"*, *"Strictly viewers say same thing as…"*; added in codebook v15 | Left alone for a real count (*"35 million people"*), a named person making the claim, specific people in the story stating a fact (*"Florida parents say their baby…"*), a study or poll, a bug report, and what players or users do in a game or product. |

---

## 4. Why not a sentiment library or a parser

A fair question is why MLE does not use an existing sentiment tool such as VADER, or a general part-of-speech tagger.

VADER was built on social media text: tweets, microblogs and product reviews. News headlines break it in three ways.

1. It confuses a sad event with manipulative writing. "Earthquake kills 45 in central Italy" is a sober report of a catastrophe, and a sentiment tool scores it as strongly negative. MLE judges how the headline is written, whatever the event.
2. It misses quiet framing. Broadsheets rarely shout. They hedge ("analysts suggest"), and a sentiment tool reads that as neutral.
3. Headline grammar trips parsers. Headlines drop verbs and articles, and dependency parsers trained on ordinary prose handle that badly.

A neural grammar model such as spaCy or Stanford CoreNLP would also add 50 to 200 MB to an app. MLE uses small hand-written patterns in Kotlin for the few things it needs:

- Word boundaries that behave correctly next to punctuation and digits.
- Sentence splitting that does not break on `Dr.`, `Mr.` or `U.S.`
- Finding "this" or "these" where the thing pointed at is never named.

---

## 5. How it is tested

Every accuracy figure for this engine comes from one command, and none is typed by hand:

    ./gradlew :tools:benchmark:run --args="--report docs/benchmark-results.md"

The current results are in [`docs/benchmark-results.md`](benchmark-results.md).

### How it is measured

- **Tuning and held-out.** Each dataset is split by a stable hash of its case ids: 70% tuning, 30% held-out. Rule changes are made against tuning cases only; held-out results are reported as totals and never list individual headlines.
- **Regression gate.** CI compares every change with `fixtures/benchmark-baseline.json`. "Left alone correctly" may not drop at all; held-out recall may not drop by more than one point.
- **Memorization lint.** A rule phrase that matches held-out headlines and nothing else fails CI: it was copied from test data rather than describing a technique. Signals get the same check, and a signal written as one regular expression is checked alternative by alternative, because a copied alternative can hide inside a signal that matches hundreds of headlines. A whole signal, or a phrase or alternative of three or more words, that matches exactly one headline is reported as a warning.

### Datasets

| Dataset | What it tests | Year | License |
| :--- | :--- | :--- | :--- |
| `context-2026` | Headlines labeled after reading the summary and story page (375 rows with cached pages), scored in three context modes. Single model reviewer, codebook version 4 plus version 5 rule 5; a person's spot-check in progress. | 2026 | project |
| `broadsheet-2026` | Headline techniques by article type, UK and US outlets. Double-annotated blind with adjudication per [`docs/benchmark/codebook.md`](benchmark/codebook.md). | 2026 | project |
| BABE (Spinde et al., 2021) | Tone on expert-labeled news sentences, including word-level bias marks (proxy: sentences, not headlines) | 2021 | AGPL-3.0, cached only |
| CheckThat! 2024 Task 2 (Struß et al., 2024) | Subjective vs objective news sentences (proxy) | 2024 | CC-BY-4.0 |
| Webis-Clickbait-22 (Hagen et al., 2022; Fröbe et al., 2023) | Catching clickbait, and whether attached context is the withheld answer | 2022 | CC-BY-4.0 |
| `harvested`, `starter` | Regression on the project's own corpora (the rules were tuned on these before the split existed, so their held-out numbers are optimistic) | 2026 | project |
| Chakraborty et al. (2016) | **Legacy regression check, not evidence** | 2016 | see file |

### Adversarial battery

`./gradlew :tools:adversarial-fuzz:run` runs hand-written evasion and false-alarm traps. It is a regression check on known cases, not a measure of accuracy.

---

## 6. Using the article as context (`core/model`, `core/flagging`, `core/feeds`)

A headline on its own only says so much: the same words can be fair on one story and misleading on another. MLE can take the article into account in three ways, all of them rule-based:

1. **`ArticleContext` & Context Evaluation Modes:**
   - The engine accepts optional context alongside any headline: `HeadlineFlagger.flag(headline, context: ArticleContext?)`.
   - `ArticleContext` captures three layers:
     - **Metadata:** declared `section`, `articleType` (e.g. opinion, review), and `byline`.
     - **Summary:** clean text from RSS `<description>` or lead blurbs.
     - **Story text:** opening body paragraphs (extracted via `ArticleText.extract()`, stripping boilerplate, scripts, navigational chrome, and ads).
   - In benchmark evaluation, three context ablation modes are supported (`--context none|summary|story`):
     - `NONE`: Headline text only.
     - `SUMMARY`: Headline + RSS summary + page metadata.
     - `STORY`: Headline + RSS summary + page metadata + opening story text.
   - **Measured effect:** on `context-2026` the three modes give identical verdicts; what context adds there is the answer shown to the reader (`resolvedContext`), which the benchmark does not score.
   - **Guarantees:** Context settles facts (position 12). It can confirm or clear flags (such as verifying a signed opinion piece or exposing a headline-body walk-back), or resolve teases (`resolvedContext`), but *never adds a technique absent from headline words*.

2. **`SummaryContextResolver`:**
   - Automatically cross-references headline curiosity gaps against RSS feed descriptions or body opening.
   - If the summary answers the headline's tease, it extracts the clean resolving lead sentence without HTML boilerplate and attaches it to `FlagResult.resolvedContext`.
   - Cross-checks medical/scientific breakthrough claims against preclinical animal testing disclosed in the blurb or opening paragraphs (`in mice`, `in rodents`, `cell cultures`).

3. **`StoryFramingComparator`:**
   - In multi-outlet story clusters (`StoryGrouper`), compares sensational headlines against calm wire baselines (AP, Reuters, UPI).
   - Computes divergent editorial vocabulary injected exclusively by sensational outlets that does not appear in straight wire reporting.

---

## 7. Questions people ask

**Is it biased against tabloids, or against one side of politics?**

There are no rules for particular outlets. The same rule file (`rulepack-v1.json`) runs on UPI, the BBC, the New York Times and the Daily Mail. The engine reads what an article says about itself (its type, from the feed or the link, so an opinion piece is never charged with opinion as fact). It never reads who published it or which way they lean. Lean tags exist only in the developer's harvest tool, where they are used to test that the word lists are even-handed: `./gradlew :tools:rule-eval:run` prints the unexplained gap between matched left and right outlets on every run. Nothing here computes a trust score or ranks publications.

**Why not run a language model on the device?**

A model can give different answers to the same headline on different runs, costs battery and time, and cannot guarantee why it highlighted a particular word. Fixed rules give the same answer every time and every flag can be traced to the rule behind it.

Language models are used in development only (decided 2026-10-19). They label headlines against the codebook without seeing the engine's answer, and they point at the words that carry each technique. Those words are turned into rule families, and each family must pass a check against the tuning rows before it ships. The benchmark labels were drafted the same way and are tested by a person's blind spot-checks ([review process](benchmark/review-process.md)). In October 2026 a bake-off tried small open-source models as flaggers and found none accurate enough ([model bake-off](benchmark/model-bakeoff-2026-10.md)).

**It misses a lot of clickbait. Isn't that a failure?**

It will always miss some. A missed headline leaves the reader where they already were. A wrong flag on a serious story costs the reader's trust in every other flag. So the rules lean toward staying quiet, and the catch rate is published in [benchmark-results.md](benchmark-results.md) for anyone to check. Credits for the research behind this work are in [REFERENCES.md](REFERENCES.md).
