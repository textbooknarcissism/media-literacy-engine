# Where MLE draws the line

Research on clickbait and persuasive framing defines techniques in general: withheld information, appeals to fear, loaded language, borrowed authority. It rarely says where the line falls for one particular headline. Is "Scientists explain why cats purr" a tease? Is "Horrifying moment girl, 14, hit by car" alarmist when the video proves it happened? Is "Pilot 'tries to crash' flight" anonymous sourcing, or careful attribution? MLE has to answer questions like these for every headline it reads.

This document records MLE's answers, the reasons for them, the research they rest on, where they go further than that research, and the evidence MLE has gathered for each. The [codebook](benchmark/codebook.md) is the operational version, written for people labeling headlines; this is the reasoning behind it. Where research is silent or draws the line elsewhere, the project decides, and its own labeled data tests the decision.

**Version:** 2026-11-18, codebook version 14, rule pack 39.

**Read position 17 first.** Since 2026-10-22 MLE is run as a teaching tool, not a filter: it speaks only when a flag is clearly right and teaches something, and its catch rate is reported but never a target. Positions 14 and 16 record the period before that; their catch-rate targets and the 70% decision rule are retired.

## How to read the evidence

Each position below cites three kinds of evidence:

- **Research:** what published studies report, and where MLE goes further than they tested. Bibliographic details are in [REFERENCES.md](REFERENCES.md), and what each study found is in [METHODOLOGY.md §F](METHODOLOGY.md).
- **Clickbait coverage:** how often the rule fires on Webis-Clickbait-22 tuning posts, every one of which its authors labeled clickbait. This is an outside check on whether a construction belongs to clickbait as others recognize it.
- **Clean-headline rate:** how often the rule fires on 2026 tuning headlines labeled `NONE` (harvested and broadsheet-2026), compared with those labeled flagged.

Read the last of these honestly. The 2026 labels were applied under this codebook, by a single model reviewer for harvested and by two model annotators with adjudication for broadsheet-2026. Agreement between the rules and those labels shows the rules do what the codebook says; it is not independent proof that the codebook is right. A beat-stratified evidence set now exists (`beats-2026`); a person's spot-check of its labels is the next step.

Held-out rows are never counted here, so these figures cannot be used to tune against the honest test.

| Rule (engine signal) | Webis-22 tuning posts | 2026 tuning, labeled `NONE` | 2026 tuning, labeled flagged |
| :--- | :--- | :--- | :--- |
| Announced answer, "here's / this is…" (`withheld_answer_here`) | 179/3,544 (5.1%) | 1/2,499 (0.04%) | 19/317 (6.0%) |
| Announced answer, "X reveals / explains why…" (`tell_all_why_hook`) | 29/3,544 (0.8%) | 0/2,499 | 21/317 (6.6%) |
| Unnamed "this/these" (`unnamed_demonstrative`) | 537/3,544 (15.2%) | 2/2,499 (0.1%) | 24/317 (7.6%) |
| Held-back statement (`withheld_statement`) | 5/3,544 (0.1%) | 0/2,499 | 9/317 (2.8%) |
| Staged exchange (`staged_exchange`) | 0/3,544 | 0/2,499 | 1/317 (0.3%) |
| Any short quoted fragment (`scare_quotes`) | 69/3,544 (1.9%) | 288/2,499 (11.5%) | 76/317 (24.0%) |
| Quoted fragment carrying a dramatic claim (`dramatic_scare_quote`) | 8/3,544 (0.2%) | 0/2,499 | 16/317 (5.0%) |
| Fear word on an event (`feeling_word_event`) | 5/3,544 (0.1%) | 0/2,499 | 14/317 (4.4%) |
| Reassurance round a catastrophe (`pseudo_reassurance_alarm`, `temporal_suspension_threat`) | 0/3,544 | 0/2,499 | 0/317 |

## Principles

Every position follows from these.

1. **A headline's job is to tell you what happened.** A technique is wording that works on the reader instead: it withholds, alarms, provokes, or borrows authority it does not name.
2. **Judge the words, and what the article says about itself, never who published it.** An article's own label or section (opinion, review, a match report) can change how a word reads. The outlet's name, reputation or politics never changes a verdict. A genre explains words; it never excuses a technique for being common in it.
3. **Dangling what is held back is a tease**, whether what is held back is the subject ("This one trick"), the answer ("Here's why…") or the statement ("X breaks silence").
4. **Quoted words belong to a named speaker.** Quotation marks with nobody behind them are the outlet speaking.
5. **Telling the reader how to feel is not description.** "Deadly" describes an event; "terrifying" describes the reader.
6. **Staging people's words as a fight is conflict framing.** Reporting that they disagreed is not.
7. **Honesty never counts against a headline.** A stated limit ("in mice"), a scoped hedge, a signed column or a plainly posed question earns no flag.
8. **Strength follows the technique.** One technique is subtle. It is prominent when it is the whole headline or a formula at full force ("Horror as…", "You won't believe…"). Two or more techniques are prominent. A draft of what "full force" means for each technique is in position 11, still a draft (open question 5).

## Positions

### 1. An announced answer is a tease

**The position.** When a headline announces that an answer exists and holds it back, it is withheld detail (`curiosity_gap`), even if it names the subject. Examples that count:
- "Here's why the pound is falling"
- "This is why your phone gets hot"
- "Here's what we know"
- "Scientists explain why cats purr"
- "Pete Davidson Shares Why Becoming a Dad Made His Career Matter Less"
- "Hackers reveal how Flock cameras really track cars and people"
- "Revealed: how…"

**What doesn't count:**
- A plain question or topic is an explainer: "Why the pound is falling", "What is El Niño and what causes it?"
- Service phrasing promises instructions, not a payoff: "Here's how to watch the eclipse tonight", "Here's what to know about the new tax rules", "Here's what you need to do", "Martin Lewis explains how to avoid the price cap hike".
- A headline that gives its answer: "Glen Powell Explains Why He Doesn't Preach Politics: It Divides People".

**Why.** "Why the pound is falling" and "Here's why the pound is falling" name the same topic. The second adds a pointer. It tells the reader the answer is right there, and that the only way to get it is to click. A disclosure verb does the same with a person ("reveals", "shares", "explains"): the headline sells the answer as something someone has and won't give away in the headline. A plain question makes no such promise; it says what the article is about and lets the reader decide.

**Research.** Forward reference, where the headline points at content it does not give, is the classic lure (Blom & Hansen, 2015; Scott, 2021). Webis-Clickbait-22 counts such posts as clickbait because the article can spoil them (Hagen et al., 2022; Fröbe et al., 2023). MLE goes further than the research in two ways:
- **Where it draws the line.** It separates the announced answer from the plain question, a distinction Webis does not make: Webis counts both.
- **What it exempts.** Service phrasing and answer-given headlines are exempt.

**Evidence.**
- *Webis:* the "here's / this is" rule fires on 5.1% of Webis tuning posts.
- *Clean headlines:* it fires on one 2026 headline labeled `NONE`. That headline ("WATCH: Kim Kardashian, Tay Keith, Millie Bobby Brown: Here's what's trending") gives its answer before the colon, which the engine cannot see yet.
- *The rule change:* codebook version 3 changed 33 labels to follow this line, and two how-to headlines moved the other way. Moving from rule pack 25 to 26, the engine's Webis held-out catch rate rose from 23.9% to 26.9%.

**Status.** Decided 2026-10-11 (codebook v2 for "reveals/shares why", v3 for the rest). Signals: `tell_all_why_hook` and `withheld_answer_here`.

### 2. A held-back statement is a tease

**The position.** "Breaks silence", "speaks out" and "reacts to" are withheld detail when the headline does not say what was said: "Lindsay Clancy's Husband and Holdout Juror Break Their Silence", "Mauricio Umansky Reacts to Stepdaughter's Pregnancy".

**What doesn't count:**
- A headline that gives the statement after a colon: "Pep Guardiola breaks his silence: … tells fans 'I am here for you'".
- A speech verb with what was said: "…and says 'he scammed my heart'".
- A stance: "speaks out against the bill".
- A quotation that leads the headline.
- Speaking out in general: "afraid to speak out", "women who speak out".
- Markets, which react by moving: "Stocks react to jobs report".

**Why.** The news value of "X breaks silence" is entirely in what X said, and the headline withholds exactly that.

**Research.** This is an instance of withheld information the article supplies (Hagen et al., 2022). No study cited here tests this construction specifically, so this is an MLE position.

**Evidence.**
- *Webis:* 5 tuning posts match; the construction is rarer in social posts than in celebrity news.
- *Labeled data:* 9 flagged 2026 tuning headlines match, and no `NONE` ones.
- *Coverage gap:* the evidence is thin outside entertainment.

**Status.** Decided 2026-10-11 (codebook v2). Signal: `withheld_statement`.

### 3. Somebody's words staged as a fight are conflict framing

**The position.** A person responding to another person's words is cast as a fight (`manufactured_rage`):
- "Lily Allen responds to David Harbour's comments about her break-up album"
- "Arteta hits back at Guardiola's jibe"
- "claps back at trolls"

**What doesn't count:**
- An institution responding to an event: "Police respond to shooting", "Council responds to petition".
- Generic criticism: "Government responds to criticism over waiting lists".
- A real armed clash: "Russia launches fresh attack on Kyiv". This is why rule pack 26 removed the "launches attack" phrases, which had been flagging military strikes.

**Why.** "Responds to Y's comments" makes an exchange of words the story, a feud built from two quotes. The judgment behind this rule, which the data supports anecdotally, is that outlets produce dozens of these.

**Research.** Framing selects and emphasises one reading of events (Entman, 1993). Argument understood as war is among the most familiar conceptual metaphors (Lakoff & Johnson, 1980). Loaded language is a recognized persuasion technique (Piskorski et al., 2023). None of these tests this construction, so this is an MLE position.

**Evidence.**
- *Labeled data:* one labeled 2026 tuning headline matches.
- *Webis:* none.
- *CheckThat:* one news sentence is now flagged ("Hitting back at the critics, the Hollywood actress said…"). CheckThat labels it objective, because that dataset measures subjectivity, not framing.
- *Next step:* this needs evidence from beats outside entertainment.

**Status.** Decided 2026-10-11 (codebook v3). Signal: `staged_exchange`.

### 4. "This" or "these" pointing at something never named

**The position.** A demonstrative with nothing to point back to is withheld detail: "This semiconductor stock is in the dumps", "Mount Vesuvius Buried These Scrolls for 2,000 Years".

**What doesn't count:**
- Time: "this week".
- Titles: "Fight For This Love".
- Pointing back: "this latest crisis".
- Service lists: "these stylist-approved tips".
- Sales copy.
- Quotations.
- A signed view, where "this" points at the writer's own argument.

**Research.** Scott (2021) found definite referring expressions significantly over-represented in clickbait; ambiguous reference is one of the tactics in Nofar et al. (2025).

**Evidence.** This is the clearest separation in MLE's data:
- 15.2% of Webis tuning posts;
- 0.1% of 2026 tuning headlines labeled `NONE`.

**Status.** Rule pack 24, codebook v1. Signal: `unnamed_demonstrative`.

### 5. Explainers and service pieces that name their subject are not teases

**The position.** "How to make cornbread", "Capital gains tax: how it works", "What is El Niño and what causes it?" and "Why the pound is falling" are `NONE`. A how-to is a curiosity gap only when it hides the subject ("This one trick…").

**Why.** Service journalism promises instructions and delivers them. Flagging it would mark much of what careful outlets publish and teach readers that every explanatory headline is a trick. The announced answer (position 1) is the line that separates a tease from an explainer.

**Research.** Webis-22 counts many explainers as clickbait, because their answers can be spoiled. MLE deliberately does not follow it here, and position 1 says exactly where it agrees.

**Status.** Codebook v1 rules 4 and 5, refined in v3.

### 6. Quoted words, and quotes with nobody behind them

**The position.** Words in quotation marks belong to whoever said them, so they never count as the outlet's alarm, conflict or opinion. A short quoted fragment (one to four words) with nobody named or identified in the headline is anonymous sourcing (`vague_sourcing`) when it carries a dramatic claim. A dramatic claim is an allegation of wrongdoing or violence, a charge, a loaded label, or an alarm. Examples that count:
- "Pilot 'tries to crash' Israel flight"
- "Suspect foiled RAF Fairford 'bomb plot'"
- "Antisemitism 'endemic' in British universities"
- "'Very sexual' prison guard sacked"

**What doesn't count:**
- A named or role-identified speaker: "Andy Burnham says there are 'strong indications'", "construction boss blames 'family tragedies'". An anonymous speaker ("allies warn", "amid claims") does not count as identified.
- A name, title or coined term: "'alcohol awareness' training", "the 'crypto winter'".
- A word being discussed: "the word 'gangs'".
- A quotation that opens the headline.
- A press review: "The Papers: 'Giddy King'…".
- Words that make no dramatic claim: "'unseasonable'", "'devoted'", "'gobsmacking' amount". An evaluation without a target of blame or danger is not a claim.

**Why.** Quotation marks let a headline say "tries to crash" without the outlet saying it. The reader remembers the claim, not the punctuation. Most quoted fragments are innocent, though, so counting them all would flag a large share of careful reporting. The test is whether the quotes carry something the outlet would have to stand behind if it wrote the words plainly.

**Research.**
- *Scott (2023):* deceptive headlines lead the reader to infer more than they state.
- *Piskorski et al. (2023):* treat appeals to unnamed authority as a persuasion technique.
- *What is MLE's own:* the dramatic-claim test and the speaker test.

**Evidence.**
- *Plain fragments:* fire on 11.5% of 2026 tuning headlines labeled `NONE`, and on 1.9% of Webis posts.
- *Dramatic fragments:* fire on none of those `NONE` headlines.
- *The relabel:* codebook version 3 re-judged every row with a short quoted fragment (558). It changed 39 labels: 36 where a dramatic claim had nobody behind it, and 3 where the quotes turned out to have a named or identified speaker.
- *A known cost:* UK court reporting puts allegations in quotes for legal reasons, so some careful reporting is flagged. That cost is accepted.

**Status.** Decided 2026-10-11 (codebook v3 rule 1). Signals: `scare_quotes` (soft), `dramatic_scare_quote`.

### 7. A signed or labeled view is never opinion as fact

**The position.** An opinion piece, editorial, review or letter has told the reader whose judgment it is. The reader knows this from a byline, a label, or the article's own type from its feed or link, so `opinion_as_fact` never applies. Its other techniques still count: a column can tease or alarm. A thesis headline in a news section is the outlet speaking. Examples, both now `SUBTLE`:
- "The Kennedy Center Is Just Trump's Latest Hostage" (Mother Jones, /politics/)
- "Greenland Deal Vindicates Trump Yet Again" (Breitbart, /politics/)

**Why.** The technique is passing judgment off as fact. A label removes the pretence.

**Research.** Otis (2024) found that labeling opinion pieces raised the source's perceived credibility, which supports treating the label as information the reader has.

**Evidence.**
- *Web lookups:* twelve harvested thesis headlines were looked up; six news-section theses were relabeled.
- *Even-handedness:* the same standard was applied to left- and right-leaning outlets.
- *An engine gap:* the engine misses all six, because these verdicts use ordinary words ("is just… latest hostage").

**Status.** Codebook rule 2; the article type has been an engine input since 2026-10-11. Signals: `labelled_opinion`, plus `ArticleTypeClassifier`.

### 8. Real events, fear words, and "Moment…"

**The position.** Plain reports of real harm are not alarmism: "Deadly crash closes M6", "Bridge collapse kills three". A fear word on the event or its footage is `manufactured_fear` even when the event is real and filmed:
- "Terrifying footage shows…"
- "Horrifying moment girl, 14, hit by car"
- "harrowing ordeal"
- "horror crash"

**What doesn't count:**
- Plain "Footage shows jet nosediving".
- A caption that states what happens: "Moment police tackle knifeman".
- Sad words: "heartbreaking death".
- Genres: "horror film", "horror game".

**Why.** The footage proves the event; it does not prove the feeling. "Deadly" describes what happened; "terrifying" tells the reader how to feel about it.

**Research.** Appeal to fear and exaggeration are recognized persuasion techniques (Piskorski et al., 2023). Where MLE goes further:
- **Where the line falls:** fear words count, sad words do not.
- **The captions:** "Moment…" is a caption, not a tease.

**Evidence.**
- *Labeled data:* the rule fires on 14 flagged 2026 tuning headlines and on no `NONE` ones.
- *The relabel:* codebook version 3 changed 8 labels to follow it. It also reversed an earlier relabel that had treated "horror crash" on a real crash as `NONE`.

**Status.** Decided 2026-10-11 (codebook v3 rule 3). Signal: `feeling_word_event`, plus the fear word list.

### 9. Reassurance wrapped round a catastrophe

**The position.** "No, you won't die from flesh-eating bacteria", "Relax, an asteroid probably won't wipe out Earth" and "Your savings aren't gone just yet" are fear appeals. The reassurance is the disguise; what the headline does is put the catastrophe in the reader's head.

**Research.** No study cited here tests this construction. It is an MLE position.

**Evidence.** None yet: these rules match no headline in any dataset. They were kept as a position on 2026-10-11 rather than deleted for lack of a citation. They need labeled examples, which the harvest will collect.

**Status.** Project position. Signals: `pseudo_reassurance_alarm`, `temporal_suspension_threat`, `survival_challenge_frame`.

### 10. Genre, source and lean

**The position.** Three different things get called "genre rules":
1. **Phrasings that cluster in a beat**, such as "drops a massive hint" in gaming or "breaks silence" in entertainment. The standard is the same everywhere, and the rule fires wherever the phrase appears.
2. **Conventions that change what a word means**: a patch note's "nerf", a match report's "City crush United", a labeled column's thesis. These are legitimate because the reader can see what kind of text it is, and they come from the article's own label or section.
3. **Different thresholds for the same words depending on who published them** (publisher priors).

MLE uses the first two and not the third.

**Why not priors.**
- **The receipt would stop being true.** A score raised because of the outlet is not something the headline did.
- **Priors computed from an engine's own hit rates are circular.** Until rule pack 24, MLE read the Daily Mail's capitalized bylines as shouting. A prior computed then would have punished the Mail for an engine bug, and kept punishing it.
- **They teach source heuristics instead of reading.**
- **They are politically loaded.** Identical headlines from left- and right-leaning outlets would get different verdicts.

**What MLE does instead with outlets and lean:**
- **Even-handedness.** Its developer harvest panel pairs left- and right-leaning outlets, so the word lists can be checked for symmetry: a rule that knows one side's vocabulary better flags that side more. BABE records each sentence's outlet as left, center or right, so the same check can run on expert labels.
- **Research.** Per-outlet technique rates are measured and published as findings, never as scoring inputs.
- **Measuring priors.** Priors were an open question, so they were measured rather than argued: fitted on tuning labels, tested on held-out, with misses fixed, false flags added and the effect on the left-right gap reported.

**Evidence** (outlet study, 2026-10-11).
- **Priors do not help.** No prior strength improves balanced accuracy even on the tuning rows it was fitted to, whether its offsets come from labels or from the engine's own flags.
- **Forced anyway, they cost far more than they fix.** A moderate prior fixes 18 held-out misses and adds 140 false flags; clean headlines left alone fall from 98.6% to 87.1%.
- **They distort lean balance.** A forced prior moves the left-right gap 20 to 40 points away from what the labels show, in whichever direction its data leans.
- **They are circular.** Offsets fitted from the engine's own flags correlate with label-fitted ones at r = 0.94.
- **The rules show no political dialect.** Within each register, left- and right-leaning outlets are treated alike, except where the engine cannot see a technique. It misses verdicts stated in a news voice, which in the data fall mostly on Mother Jones. BABE's own lean labels show no meaningful difference.

**Research.**
- *Scott (2023):* deceptive headlines often wear conventional dress, so the words, not the outlet, are what to judge.
- *Spinde et al. (2021):* BABE's lean metadata makes the symmetry test possible.

**Status.** Decided 2026-10-11. Lean tags stay in developer tools; nothing in the app knows an outlet's politics.

### 11. Strength

**The position (draft).** Principle 8 holds: one technique is SUBTLE and two or more are PROMINENT. One technique is also PROMINENT at full force, when it is pushed as far as it goes. Each technique escalates in its own way, so "full force" is defined per technique:

| Technique | Full force: PROMINENT on its own | One ordinary use: SUBTLE |
| :--- | :--- | :--- |
| `curiosity_gap` | The headline hides what it is about as well as the payoff, or is nothing but the tease. "You won't believe…", "This one trick…", "The extra cash Isa allowance you probably won't have heard of…", "…reveals the everyday store cupboard staple that kickstarted her weight-loss journey". | One withheld detail, subject named: "Scientists Reveal Who Really Benefits From a Heart Calcium Scan", "Here's why the pound is falling". |
| `manufactured_fear` | Catastrophe with nothing behind it ("ticking timebomb", "Horror as…"), or alarm aimed at the reader with an instruction: "Deadly jab warning to university parents - urged to act before it's too late". | One fear word on a real event, or a metaphor: "horror crash", "Inside the squishy nightmare: Explosions, burns, and thousands of calls to poison control". |
| `manufactured_rage` | Humiliation or a personal attack as the story: "Sadiq Khan brutally humiliates Zack Polanski over baffling foreign language video", "…left humiliated in new blow". | One conflict word for a real disagreement: "slams", "hits out at", "unloads on", "war of words". |
| `vague_sourcing` | The story rests entirely on people nobody can check, especially about someone's private life: "Family insiders reveal life-long secret struggle…". | One unnamed source, or one quoted claim with nobody behind it. |
| `speculative_hedge` | A catastrophe that exists only through the hedge: "Putin plot could trigger 'mass casualties'…". | A hedged outcome of ordinary size: "set to shake up the AI race". |
| `opinion_as_fact` | An insult or a loaded label stated as fact about a named person or group ("a shameless bribe"). | A mild verdict: "a work in progress". |
| `urgency_pressure` | Several pressure devices together: capitals, a deadline, "act now". | One capitalized word or one deadline. |
| `science_overhype` | A cure, reversal or breakthrough claimed from mice, cells or a simulation. | A big claim with the stage missing, without the cure language. |

**Why.** Strength tells the reader how hard a headline is working on them. A headline can work harder in two ways: by stacking techniques, or by pushing one technique to its extreme. Counting covers the first; this table covers the second. It has to be per technique because the techniques escalate differently:
- a tease escalates by hiding more, until even the subject is gone;
- alarm escalates by turning from the event to the reader;
- conflict escalates by making someone's humiliation the story.

**Research.** None of the research MLE draws on defines strength per technique. This is MLE's own position.

**Evidence.** The 27 harvested rows whose labels differ only in strength (audit 2026-10-10, Appendix B):
- 21 are tuning rows. Applying the table to them would change 12 labels: 4 to PROMINENT and 8 to SUBTLE. One row depends on whether "the truth about…" is a tease.
- Most of the eight moves down are a single announced answer with its subject named. Those rows kept their PROMINENT label from the detector's early guess, which treats many tease phrases as full strength.
- The engine already scores two techniques found at notice level or above as PROMINENT. A full-force formula reaches PROMINENT only through its hard list, so adopting the table means moving phrases between the hard and notice lists.

**Status.** Draft, 2026-10-11. The leaning was "prominent, but it depends on the technique"; this table makes that idea concrete.

### 12. Context settles facts

**The position.** Context (the article's declared type and section, the feed summary, and the opening of the story) settles facts: who is quoted, what the piece says it is, what stage the work is at, what the headline held back. It can clear or confirm a headline-level flag, and it can show the reader the fact a tease withheld (`resolvedContext`). It never adds a technique the headline's words do not carry (codebook §1).

**What context can do:**
- Clear `opinion_as_fact` when the page declares itself opinion, review or editorial (rule 2, position 7).
- Show the answer a tease held back, when the summary or opening gives it.
- Confirm `science_overhype` when the summary or opening shows the work is in mice, cells or simulations.

**What context never does:** flag an honest headline because the story beneath it is loaded, or excuse a device in the headline because the story is calm. A headline is judged on its own words.

**Why.** The headline is what the reader sees first and what MLE underlines. Judging it by the story's style would break the promise that every flag points at words in the headline. Context earns its place by settling the facts those words rest on.

**Research.** Webis-Clickbait-22 judges a post by whether the linked article supplies what the post withholds (Hagen et al., 2022; Fröbe et al., 2023).

**Evidence.** On `context-2026` (375 rows with cached pages), the three benchmark modes, `--context none`, `summary` and `story`, give identical verdicts: 93/96 held-out honest headlines left alone and 8/24 held-out techniques caught in every mode. So on this set context changed no verdict; what it adds is the answer shown to the reader, which the benchmark does not score.

**Status.** Decided 2026-10-12 with codebook version 4 (§1). Implemented in `HeadlineFlagger.flag(headline, context)` and `ArticleContext`.

### 13. A deficit hook on a news event, and a reader's dilemma closed by a verdict question

**The position.** Two constructions announce an answer and hold it back, and both are `curiosity_gap` (codebook version 5, rule 5):

1. **A deficit hook tacked onto a news event:** "Cardinal football is back — here's what you need to know". The event is named, but the story's substance is sold as something the reader lacks. The hook must close the headline. One that opens with the words, or goes on to name its subject, is service and stays `NONE` ("Here's what you need to know if you haven't filed your return", "Social anxiety is more common than many think. Here's what you need to know about the condition", rule 4).
2. **A reader's dilemma closed by a verdict question:** "My sister moved in with our mother, 78, and says she deserves the family home. Is she right?", "My financial adviser wants me to invest $350,000 in a UAE data center. Is this risky?". The headline stages the dilemma in the reader's own words and holds back the columnist's verdict, which is the payoff. The dilemma must be told in the first person, so an explainer question ("Covid jab for under-5s: is it safe?") is not caught.

**Why.** Both forms have been spreading in recent coverage, across sports, personal finance and advice columns, often from outlets that otherwise write plain headlines. Each sells the article's answer while looking like service or a reader question.

**Evidence, and what it cannot show.**
- The engine rules (`prescriptive_deficit_hook`, `advice_dilemma_cliffhanger`) were written from three `context-2026` held-out rows: the Cardinal football row and the two advice-column rows. Those rows account for the whole rise in held-out catches, from 5/21 to 8/24, so the held-out figure is not independent evidence for these rules. The memorization lint flags it, and the three grandfathered keys in `fixtures/benchmark-baseline.json` record it.
- Elsewhere the rules match one Webis-Clickbait-22 post ("STOCKS DO NOTHING: Here's what you need to know") and one `harvested` headline labeled `SUBTLE` before the rule existed ("Earl Spencer's bombshell new Princess Diana book could tear the Royal Family apart. Here's what you need to know"). They match no headline labeled `NONE` in `broadsheet-2026`, `beats-2026` or `harvested`.
- An honest test needs fresh headlines harvested after 2026-10-13.

**Not adopted.** Two other constructions from the same session were left out. A verdict like "Zack Polanski has disappointed his party" is already `opinion_as_fact` under version 4 and needs no new rule (the term "has disappointed" matches that one headline). A named speaker's paradox ("Steven Pinker: 'Something that's arbitrary can be highly rational'") is a cryptic line that promises nothing, which is `NONE`.

**Status.** Decided 2026-10-13 (codebook version 5). Implemented in `Signals.kt` and `rulepack-v1.json`.

### 14. The obvious ones, as rule families (rule pack 28)

**Retired targets (2026-10-22, position 17).** The catch-rate targets that went with this position no longer apply; catch rate is reported as information. The left-alone targets (97.5% pooled, 96% per set) stand.

**The position.** The engine should be good at the obvious ones, with rules alone on the device. Rhetorical and cynical styles count when they are obvious, as named patterns inside the existing techniques (codebook version 7):
- leading questions, sweeping verdicts, insinuation and loaded labels under `opinion_as_fact` (leading questions are `loaded_question` since rule pack 39, position 20);
- sarcasm and mockery under `manufactured_rage`.

Each pattern is its own signal, so its name rides on the hit (`signal:leading_question`) and an app needs no new category.

**How the rules were made.** Language models did the reading, and only rules ship. A model labeled 1,600 unseen harvested headlines (`mined-2026-10`, tuning only) against the codebook, naming the pattern and copying the exact trigger words. The triggers were grouped into families, with one pattern per family. A pattern stayed only if it matched at most 0.5% of tuning rows labeled `NONE` and added catches on flagged ones. The memorization lint caught six alternatives that matched only held-out rows, and they were removed.

**What it showed.** The new families caught obvious formula clickbait much better on tuning material, but held-out catches barely moved. The misses that remain are unsigned thesis headlines, evaluative verdicts in plain words, and withheld subjects described in ordinary nouns. A word pattern cannot separate these from honest headlines without flagging honest ones too, so this is the measured ceiling of phrase rules, and part of why catch rate stopped being a target (position 17). Rule pack 30 then added guards against false flags: a scare quote whose speaker the headline names, "this chart" or a name given before a colon, and genre phrases such as "horror at its heart".

### 15. The blind spot-check (codebook version 8, rule pack 31)

**The position.** A person acts as the project's reference reader. Where that reader's blind judgment and the codebook disagree systematically, the codebook moves; where they disagree on single calls, it does not.

**Evidence.** A person labeled 200 tuning rows blind ([report](benchmark/spot-check-2026-10/report.md)). Agreement with the version 7 labels was 148/200 (74%), κ 0.48, and the reviewer flagged more (33 against 19). Most disagreements sat on four rules, and reworking them became codebook version 8:
- a fear word next to a harm the headline states is description;
- quotes around alleged conduct are shorthand for "alleged";
- labeled opinion stays exempt, and an unsigned verdict counts;
- practical guides stay clean.

From the reviewer's own labels: an unnamed report is not anonymous sourcing, and "disgraced" and "yobs" are not loaded labels.

**What followed.** A blind re-label of the 787 rows those rules could touch changed 131 labels, 117 of them to `NONE`. Rule pack 31 retired 21 phrase rules with no tuning support under the new labels. Agreement with the reviewer on the same 200 rows rose to 154/200 (77%), κ 0.55. That figure is not independent, because the codebook was adjusted on these rows.

**Read it honestly.** Tuning left-alone rates were 97.1–99.0% in every set, but held-out rates were lower (harvested: 99.0% tuning against 96.3% held-out). The rules had partly fitted the rows they were written from. Closing that gap needs new, unseen data, not more rule edits against the same rows.

### 16. Clickbait unless proven otherwise, and where 70% stops (codebook version 9)

**Reversed (2026-10-22, position 17).** Codebook version 10 replaced rule 7 with the teaching bar (a tie goes to `NONE`) and restored the version 8 labels. The Strict setting and its word table were removed, and the 70% decision rule was retired. What follows is the short record of what was tried.

The request was to examine headlines "with strict prejudice", assuming clickbait unless proven otherwise, and for the engine to catch at least 70% of clickbait. Codebook version 9 applied that standard and moved 1,035 of 4,226 `NONE` rows to flagged. A decision rule was fixed before measuring: ship whatever reaches at least 70% caught on held-out rows with at most 15 honest headlines in 100 wrongly flagged.

Two experiments added learned word weights to the rules. On version 8 labels the weights reached 61% caught with 15.7 honest headlines in 100 wrongly flagged; on version 9 labels, 64% caught with 28.0 in 100. Rules alone caught 41% at 3.4 in 100 (version 8 labels). Neither experiment met the rule. What blocked 70% was not the headline text alone. Read from the headline and the outlet's summary, a language model reached the reviewer's own standard, catching 82% of the reviewer's clickbait while flagging 14% of the reviewer's honest headlines. What phrase rules and word weights cannot do is read: a thesis stated as fact, an unnamed subject or a held-back payoff carries no fixed words. Rules alone stay precise but catch about a quarter of clickbait under the reviewer's standard, and buying 70% with word weights costs about 28 wrong flags in 100. A Strict setting using the word table shipped as an opt-in on 2026-10-20, and was removed again by position 17.

### 17. MLE is a teaching tool, not a filter

**The position.** MLE exists to show a reader, on the headline in front of them, the words doing the work, name the device and ask a question, so they can reflect on what they read. Most readers already know what clickbait and ragebait are; few have had one pointed out clearly at the moment of reading. So MLE speaks only when the technique is plain and the flag would teach something true. When a headline is borderline it stays silent. A missed headline costs a reader little: they read as they would have anyway. A wrong or muddled flag teaches the wrong lesson and costs the tool its reader's trust.

**Why this is written down.** Between 2026-10-11 and 2026-10-20 the project came to be run like a filter. It had drifted toward a familiar model: Pi-hole, an ad blocker. The codebook went through eight versions in nine days and the rules through eight packs; catch-rate targets were set, missed and reset three times (§14, §16); version 9 presumed every headline clickbait; and a Strict mode was added to raise the catch rate. Held-out catch rate stayed between about a quarter and a half throughout. A filter's measures were being applied to a teacher.

| | A filter (Pi-hole) | MLE, a teacher |
| :--- | :--- | :--- |
| Job | Stop the thing reaching you | Show you the thing and why it works on you |
| A miss | A bug: the ad got through | Normal: the reader reads as they would have anyway |
| A wrong flag | Minor: a page loads oddly | Serious: the reader learns the wrong lesson and stops trusting the tool |
| How it grows | Bigger lists, stricter modes | A small, stable set of clear, explainable cases |
| Success | Share blocked | When it speaks, it is right and it teaches |

**What counts as working.**
- **The monthly flag review**, the main measure: a person reads up to 50 headlines MLE flagged that month, as a reader would see them, and answers two questions. Is this a clear case? Would the highlight and question help a reader stop and think? The target is at least 90% yes on each.
- **Honest headlines left alone:** at least 97.5% of held-out rows pooled and 96% in every set.
- **Precision per technique:** when MLE names a technique, how often it is right; it leads each set in the benchmark report.
- **Catch rate** is published with its interval for honesty. No decision is taken to raise it.

**Guardrails.** Each answers a move the filter period made.
1. A reported miss is not a bug. It is a candidate teaching example, and becomes a rule only if it is a clear case under the codebook, the rule fires on at most 0.5% of tuning rows labeled `NONE`, and the gate passes.
2. No stricter modes or tiers. MLE speaks or stays silent.
3. Catch rate is never a goal.

The codebook only changes in numbered versions; rule packs ship monthly with the flag review; and the decisions made in each review are kept as tests (`core/flagging/src/test/resources/edge-cases.tsv`), so a later rule pack cannot quietly undo them.

**Evidence so far.** The first flag review (October 2026: 366 headlines harvested from 2026-10-19, 33 flagged, all reviewed) scored clear case 28/33 (84.8%) [69–93%] and helps a reader 23/33 (69.7%) [53–83%], both short of 90%. MLE mostly flagged the right headlines but often named the wrong technique or underlined the wrong words. Reading the stories behind the reviewer's notes led to codebook versions 11 and 12 and rule packs 32 and 33. Re-run on the same headlines, the rules now meet 32 of the 35 recorded decisions; three stay open. That re-run is not independent, because the rules were written from those headlines: November's review is the honest test. Held-out left alone moved from 96.6% to 96.7%; it is still short of 97.5%, and those wrong flags are the first precision work.

**Status.** Adopted 2026-10-22 (`docs/teaching-reset-2026-10.md`): codebook version 10, Strict setting removed, catch-rate targets retired, report reordered, flag review built. Codebook version 12 and rule pack 33, 2026-10-23.

### 18. A flag needs a construction, and one clear technique

**The position.** MLE flags a headline for what its words are doing together, not for a word being present, and a flag names one technique unless two are each strong enough to flag the headline alone. "Good enough to help educate people" is the bar (2026-11-09): a reader should be able to see why the marked words were marked.

**Why.** In November 2026 both kinds of error showed up in the Know News app: plain teases left alone, and flags on words used literally ("Nightmare difficulty", "survival horror", "Urgent Care", "[SPOILER]"). An audit found one cause behind both.

- **Misses share a shape, not a phrase.** Most missed teases put a general noun where the fact should be: "one controversial detail", "reveals her daughter's reaction", "popular supplement", "and it's not the one you think". The earlier rules aimed at this shape, but each carried its own list of adjectives and nouns, so "makes big change" was caught and "makes controversial change" was not. Rule pack 35 builds these rules from shared word classes (adjectives that sell a secret, nouns that name a slot without filling it), with checks for a headline that goes on to fill the slot.
- **Wrong flags came from words alone.** A listed word flagged on sight unless an exception list caught it, which is the reverse of the codebook's "a tie goes to `NONE`". Ten single words with an everyday literal sense (nightmare, shocking, terrifying, horrifying, horrified, urgent, standoff, snub, branded, bombshell) no longer flag alone. Their constructions still do: "terrifying footage", "worst nightmare", "nightmare winter ahead", "urgent recall". "Shuts down" counts only when a person is shut down.
- **Weak second labels were mostly wrong.** A hit too weak to flag a headline alone used to be shown as a second technique beside a real one. On tuning rows whose labels name techniques, those labels matched 5 times in 43; a full construction hit matched 176 in 188. They are no longer shown.

**What does not change.** The codebook (version 12) and its teaching bar. Every new rule detects something §4 already calls withheld detail; no technique was added or widened.

**Evidence (2026-11-09, rule pack 35 against 34).**

| Measure | Rule pack 34 | Rule pack 35 |
| :--- | :--- | :--- |
| Held-out honest headlines left alone, pooled | 1,233/1,275 (96.7%) | 1,236/1,275 (96.9%) |
| Held-out caught, pooled (information) | 111/274 (40.5%) | 113/274 (41.2%) |
| `savedyouaclick-2026` caught (tuning only) | 355/928 (38.3%) | 461/928 (49.7%) |
| Fresh November headlines flagged (365) | 30 | 32 |

Read it honestly. The new rules were written after reading `savedyouaclick-2026` tuning rows, so that figure shows the rules do what they were written to do and nothing more. The held-out figures are independent and move little: seven catches gained from the new rules, five lost with the demoted words, three wrong flags removed. On the November headlines six flags are new and four are gone; all ten changes read as right to the session that made them, and the November review is the test. The memorization lint warns that two alternatives (one in `negated_guess`, one in `reaction_what`) match a single headline each.

**Not solved.** Verdicts stated in plain words ("is a cruel joke"), a withheld subject in ordinary nouns ("The world's largest telecoms firm is on trial"), closings such as "Here's the breakdown", and an open question read as a leading one ("Can they lower your grocery bill?").

**Topic.** Should rules differ by topic? Not for now: most of the wrong flags reported were not topic-specific, and this position removes them without per-topic lists. What remains possible, within position 10, is passing the topic as a hint so a few words read literally in gaming, sport and entertainment. Revisit with the November review in hand.

**The miss check.** The monthly review used to read only what MLE flagged, so a plain miss was invisible until someone noticed one. It now also shows up to 50 headlines MLE left alone and asks one question: is this an obvious miss? The count is reported as information, like the catch rate; it is not a target, and a yes is a candidate teaching example under guardrail 1 of position 17.

**Status.** Decided 2026-11-09: the bar was set ("good enough to help educate people") and the calls above follow from it. Rule pack 35; `ConstructionsTest`; edge cases f12, f24 and f33 now require their main technique only.

### 19. A study offered as proof is its own technique

**The position.** A headline that leans on an unnamed study, survey, poll or data set as the proof of its point ("…, study finds", "Poll:", "Data shows…") is `study_as_proof`, "Study as proof", not `vague_sourcing`. The flag asks what the study measured and whether the story says what else it found.

**Why.** In November 2026 it became clear that the study behind a "study shows" or "research suggests" headline is usually quoted in the story, but with the data picked to fit the writer's line. "Who ran it?", the old question, often has an easy answer a few paragraphs down, so it taught the wrong check. The trouble is selection: one result, chosen to fit the story, standing in for the whole study. That is a different move from an anonymous insider, and it needs a different question.

**What MLE does not claim.** The headline cannot show whether a finding was cherry-picked, so the flag never says it was. It names the move, offering a study as proof, and hands the reader the check. Whether a named study is sound is not judged: that would be rating a source.

**What does not change.** The same headlines are flagged, at the same level. The signals, weights and cancels moved from `vague_sourcing` unchanged, and the flag still stands down when the headline, the summary or the story's opening names who did the work (codebook version 12). Scored on every labeled headline before and after (40,038), no shown verdict moved. 35 labels changed technique.

**Not decided.** A named study can be cherry-picked too ("A Harvard study finds…"). Flagging those would be a new rule, not a rename, and would be measured before it is proposed.

**Status.** Decided 2026-11-17: a new technique, not a reworded question. Codebook version 13, rule pack 38.

### 20. A loaded question is its own technique

**The position.** A question that carries a claim, verdict or worry the headline does not state outright is `loaded_question`, "Loaded question", not `opinion_as_fact`: "Was Neville right to…?", "Is it time for Ed Davey to quit…?", "Has the next financial crisis begun?", "Is X really…?", "… Why now?", "… Should he?", ". Is your backup ready?". The flag asks the reader to turn the question into a statement and ask whether the outlet would print that, and whether the story backs it.

**Why.** Question headlines often read as opinion, speculation or provocation, and "is this something that happened, or somebody's view of it?" does not fit them: a question states nothing. The move is to carry the claim inside the question, where the reader takes it in and the outlet can say it only asked. Implying a claim without asserting it is the same mechanism as misleading implicature (Scott, 2023), and *Doubt* covers the planted-doubt form (Piskorski et al., 2023).

**Where the line falls.** It was drawn from a reading of 42 question headlines on 2026-11-18, each marked trick, gentle note or fair. That reading marked nearly every question a trick, including plain explainers, so the line is narrower than the reading: a question counts when its statement form is a verdict, a scare, an insinuation or a hook the outlet would not print as it stands. Left alone: a plain question the piece answers (rule 5), an open question about how something will turn out (rule 7), a question that doubts a claim ("…: Does it?"), service, a reader's dilemma (`curiosity_gap`), and signed opinion, reviews and letters (rule 2, as for `opinion_as_fact`).

**What the rules can and cannot do.** The leading-question and closing-nudge signals move over unchanged. One new signal adds shapes read off tuning headlines: sarcasm ("What could possibly go wrong?"), a verdict on a person ("Was X right to…?", "Is it time for X to quit?"), a scare at the reader ("killing your battery?", ". Is your backup ready?"), a closing "- but are … ?", a "why … so bad" premise, "Why now?" after the news, "Seriously, …?" and "the question no one dares ask". Two broader shapes were measured and dropped because they caught fair questions: a scare word in any yes/no question ("Can Labour end rough sleeping?", "Could I survive the apocalypse?") and "So why …?". On held-out rows the new signal caught none of the loaded questions and added one wrong flag (pooled held-out left alone 97.1% to 97.0%). Most loaded questions carry their load in ordinary words ("Why Does Trump Think He Can Act Like a King?"), which rules cannot read reliably. The technique earns its place through the reader question, not the catch rate.

**Not decided.** A plain question that holds back a short answer the headline could have given ("Why are two college football teams playing at Wembley on Saturday?") was raised at the same time. It is a withheld-detail question, not a loaded one, and changing it would reopen position 1's line between a plain question and an announced answer. One idea is to use the feed summary: when it gives the answer in one sentence, the headline held back something easy. It would be measured before it is proposed.

**Status.** Decided 2026-11-18: a new technique. Codebook version 14, rule pack 39.

## Open questions

Sorted 2026-10-23, after the teaching reset ([position 17](#17-mle-is-a-teaching-tool-not-a-filter)). The numbers are kept because other documents refer to them. Two are still open: question 5, and the app half of question 6. The rest are settled and are listed after them with their rulings. An open question is not a to-do: under position 17 it becomes work only when a clear miss turns up in the monthly flag review.

5. **Strength per technique** (position 11). *Open.* Two or more distinct techniques are always `PROMINENT` (codebook version 4); whether one technique at full force is, and how that differs per technique, is still the position 11 draft. One question is new since the reset: neither the flag review nor the gate ever measures `SUBTLE` against `PROMINENT`, so whether MLE should keep two levels at all belongs with this decision.
6. **Outlet rates in the app.** *Answered for MLE; open for apps built on it.* MLE never ranks, blocks or rates outlets (README; principle 2). METHODOLOGY §7 says the app never keeps league tables, and publishing research in `docs/` does not change that. Showing rates in an app built on MLE would, and apps are out of scope here, so that decision belongs to whoever builds the app.

### Settled

1. **Witness feelings.** *Answered.* "Horrified children look on as lollipop man struck by car" is `NONE`: a witness's feeling reports how people at the scene reacted (codebook §3 rule 3).
2. **Emotions and private lives sourced to nobody.** *Answered.* For example "William is 'furious'": a bare quoted emotion with no speaker is `NONE` (codebook version 4; §4, anonymous sourcing). Other short quoted fragments are judged by rule 1: they count only when they frame the story with a loaded label or a dramatic claim.
3. **An unnamed subject.** *Answered.* For example "one of the scariest games I have ever played will finally come out…": a subject described but not named is `curiosity_gap` when the description is written to make you want the name, and plain labels stay `NONE` (codebook version 4). A pronoun standing in for the subject is a tease too (version 6).
4. **The outside design review's beat candidates:** crowd reaction ("Fans are furious over…"), "drops a massive hint", "what they don't want you to know", "the real problem with", "there's a catch", local panic with the place hidden, weather or space alarm without numbers, and ellipsis cliffhangers. *Closed.* Tested in October: none needs its own rule. Ellipses in particular are mostly `NONE`: 39 of 52 tuning rows.
7. **Publisher priors.** *Closed.* Verdicts stay source-blind (principle 2). The experiment has run (position 10): priors add nothing on tuning rows, cost many false flags when forced, and distort lean balance. It could still be reopened.
8. **Thesis verdicts in a news voice.** *Answered.* Argument pieces filed as news stay `opinion_as_fact` (codebook version 4), but since version 10 a thesis in a news headline counts only when it is blatant: a sweeping verdict or a loaded label (codebook §3 rule 7). That the engine cannot detect the subtle ones (position 7) is now by design, not a gap.
9. **Rulings added in codebook version 4.** *Answered.*
   - "Report:" and "reportedly" with no named outlet: `vague_sourcing`, narrowed in version 12 to claims that cannot be checked, not public acts such as a court order;
   - commerce headlines that withhold the product: `curiosity_gap`;
   - cryptic titles that promise nothing: `NONE`;
   - numbered lists: judged by their words, not the number ("These 5…" is a tease; a how-to that names its subject is not);
   - fact-checks that withhold the verdict, "the truth behind", "the true cost of": `curiosity_gap`.
10. **A language model in the engine.** *Closed 2026-10-19: no.* Only rules run on the device; language models are development tools that label headlines and point at trigger words, which become rules (§14). Tested on 2026-10-19 ([model bake-off](benchmark/model-bakeoff-2026-10.md)). Small fine-tuned models rank headlines better than the rules, most of all on unseen headlines (AUC 0.80 against 0.62). But at 97.5% left alone they catch about a third, and the hybrid failed its pre-set pass criteria. The nearest thing later shipped, a learned word table behind a Strict setting, was removed on 2026-10-22 (position 17).

## Outside proposals considered

**An outside design review ("Offline LLMs for RSS Apps", 2026), considered on 2026-10-11.**

*Adopted:*
- The beat-by-beat search for edge cases. MLE's labeled data had no gaming outlet and one regional paper.
- Reading the article's own type from its feed (`<category>Opinion</category>`).
- A match receipt naming the rule that fired (`PatternHit.ruleId`).

*Treated as hypotheses to test, not rules to adopt:* its beat-specific patterns (open question 4). The beat audit tested them; none needed its own rule.

*Rejected:*
- **Publisher priors** (position 10).
- **Numbers and dates lowering the score.** Michaluk et al. (2026) found numerals among the strongest clickbait cues, so this would excuse "5 Sunscreen Mistakes You're Probably Making".
- **Points that stack per word.** MLE counts a technique once.
- **Zeroing emotional words for sports and gaming, and whitelisting "breakthrough".** A gaming headline can still be ragebait, and "breakthrough could cure…" is the hype the science rule exists for. MLE mutes specific literal uses instead.
- **Rule sets chosen by outlet.** Genre comes from the article, not the publisher.
- **Telemetry.** Feedback stays on the device.

## Changes to this document

The version line at the top says which codebook version and rule pack this document describes. Earlier changes are in the repository history and in [CHANGELOG.md](../CHANGELOG.md).
