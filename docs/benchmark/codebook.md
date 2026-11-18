# Headline labeling codebook

**Version 14**, 2026-11-18. This is the current rulebook. The teaching standard (rule 7) is frozen: changing it needs a new version and a §7 entry saying why. What changed in each version, and why, is in §7. The reasoning and evidence behind each rule are in [`docs/positions.md`](../positions.md).

Read the whole document before labeling. Apply the decision rules in §3 before the technique definitions in §4. Rule 7, the teaching bar, decides every row the others leave open: a tie goes to `NONE`.

The codebook is the project's own position. It draws on the research in §6, but where that research is silent or draws the line elsewhere, the codebook decides, and the labeled data tests the decision.

## 1. What you are labeling

Each row is one headline, with the outlet that published it, the article type from the outlet's feed (news, opinion, editorial, analysis, feature, review, letters), and the outlet's own short summary.

You label the **headline**. A technique counts only if it is in the headline's own words. Read the summary before you mark a headline unsure, and the story if the summary does not settle it. Context settles facts: who is quoted, what the piece says it is, what stage the work is at, what the headline held back. Where a fact differs from what the headline alone suggested, judge on the fact and say so in `story_facts`. Context never adds a technique the headline's words do not carry.

## 2. The output

For each row, one line: `id<TAB>LEVEL<TAB>techniques`.

| Level | Meaning |
| :--- | :--- |
| `NONE` | No technique in the outlet's own words, or only the hedging and attribution careful reporting uses (a lone "could", "may", "says", or "warns" from a named source). When you are torn between `NONE` and a technique, choose `NONE` (rule 7). |
| `SUBTLE` | One clear technique. |
| `PROMINENT` | Two or more distinct techniques in the same headline (always, whatever each one scores alone), or one used at full force ("You won't believe…", "Horror as…", a headline that is nothing but a tease). |

`techniques` is a comma-separated list of these ids, or `-`: `manufactured_fear`, `manufactured_rage`, `speculative_hedge`, `vague_sourcing`, `study_as_proof`, `curiosity_gap`, `urgency_pressure`, `science_overhype`, `opinion_as_fact`, `loaded_question`. `NONE` always has `-`; `SUBTLE` and `PROMINENT` always list at least one id.

## 3. Decision rules (apply these first)

1. **Only the outlet's own words count.** Words inside quotation marks belong to whoever is quoted. The exception is a short quoted fragment (one to four words) inside a longer headline when the headline names nobody who said it and the words are a loaded label, sneer or characterization that frames the story ("Church's 'Hamas tribute'", "Antisemitism 'endemic' in universities", "'Very sexual' prison guard sacked"). The quotes let the outlet print the framing without standing behind it: that is `vague_sourcing`. It is not `vague_sourcing` when:
   - the quotes report what someone is accused of doing, which is ordinary shorthand for "alleged" (version 8): "Selma Blair arrested after 'biting and punching crew'", "Vinted flooded with 'stolen' goods", "Pilot 'tries to crash' flight", "Suspect foiled 'bomb plot'";
   - the headline names or identifies the speaker ("Andy Burnham says there are 'strong indications'", "construction boss blames 'family tragedies'"); an anonymous speaker ("allies warn", "amid claims") does not count as identified;
   - the quotes mark a name, a title, a coined term or a word being discussed ("'alcohol awareness' training", the "'crypto winter'", "the word 'gangs'");
   - the quoted words make no dramatic claim ("'unseasonable'", "'devoted'", "'serious concerns'", "'gobsmacking'");
   - the quotation opens the headline before a colon or dash ("'We want justice': series of rapes trigger protests"), or the headline reviews other papers' front pages ("The Papers: 'Giddy King'…").
2. **Signed or labeled opinion is never `opinion_as_fact` or `loaded_question`.** If the article type is opinion, editorial, review or letters, or the headline carries a byline ("| Name") or a label ("The X view on…", "– review"), the reader has been told it is opinion. Judge its other techniques normally: a column can still be alarmist or a tease.
3. **Real events described plainly are not alarmism or conflict.** Crowd anger ("Fans Outraged…") is judged by its words like any other; mild displeasure ("viewers issue same complaint") is `NONE`. A witness's feelings ("Horrified children look on as…") report how people at the scene reacted and are `NONE`. Fear and conflict words about a fiction count like any others. "Deadly crash closes M6", "Bridge collapse kills three" and "Tense standoff at loyalist march" (when there is a standoff) describe what happened. Since version 8, a fear word next to a harm the headline states is description too: "Two dead after horror crash between camper van and lorry", "Epping fire horror as locals urged to keep windows shut", "Horror at train station as youths wield machetes" and "Terrifying footage shows jet nosediving" are `NONE`. A fear word counts as `manufactured_fear` when it stands in for the facts ("Horror as…", "Terrifying moment…" with nothing said about what happened) or when the threat is vague, hypothetical or still to come ("a ticking timebomb", "nightmare winter ahead"). Plain "Footage shows jet nosediving" is `NONE`, and so is a video caption that states what happens ("Moment police tackle knifeman"). A sad word ("heartbreaking death") is not alarm, and "horror film" is a genre.
4. **Service and how-to headlines that name their subject are not curiosity gaps.** "How to make cornbread", "Capital gains tax: how it works", "Here's what to know about the new tax rules", "Here's what you need to do", "Here's what you need to know if you haven't filed your return" and "Martin Lewis explains how to avoid the price cap hike" are `NONE`. A how-to is a curiosity gap only when it hides the subject ("This one trick…"), or when the service words are tacked onto a news event (rule 5).
5. **A plain question or topic is not a tease; an announced answer is.** A headline that poses the question the article answers, or names its topic, is an explainer: "What is El Niño and what causes it?", "Why the pound is falling". Reader call-outs ("Tell us: …") are `NONE`. But when the headline announces that an answer exists and holds it back, it is `curiosity_gap` even if it names the subject, because it sells the answer as a payoff: "Here's why the pound is falling", "This is why your phone gets hot", "Here's what we know", "Scientists explain why cats purr", "Pete Davidson shares why…", "X reveals how…", "Revealed: why…". Two more forms announce an answer and hold it back: "here's what you need to know" closing a news headline after a dash, colon or full stop ("Cardinal football is back — here's what you need to know"; a headline that opens with it, or goes on to name its subject, "…Here's what you need to know about the condition", is service, rule 4), and a reader's dilemma told in the first person and closed by a verdict question the column answers ("My sister … says she deserves the family home. Is she right?", "My financial adviser wants me to invest $350,000 … Is this risky?"). The second is a tease, not `opinion_as_fact`: the question is the reader's, and the verdict is the payoff. It is not a tease when the headline gives the answer ("…Explains Why He Doesn't Preach Politics: It Divides People"). A plain question is also not a *loaded* question: one that has already chosen its answer, carries a verdict, scare or insinuation, or plants doubt about a named person, group or thing, is `loaded_question` (§4; `opinion_as_fact` before version 14). The test: could the article answer it either way without the headline looking wrong? "Can Labour end rough sleeping?" can go either way; "Is smart farming really smart business?" has already chosen.
6. **Label the words, not the outlet.** The same headline gets the same label whichever outlet published it. What the article says about itself, its type (rule 2) or its section, can change how a word reads ("nerf" in a patch note, a result in a match report). Who published it never does. A genre never excuses a technique for being common in it: "Fans are begging devs to fix this one feature" is a tease on any gaming site.

7. **The teaching bar (version 10).** MLE flags a headline to show a reader a technique clearly, not to catch every one. Label a technique only when it is plain on a first reading and a reasonable reader could not argue the headline is fair. When you are torn between `NONE` and a technique, choose `NONE`. Four readings tried in version 9 are not grounds for a flag on their own:
   - a thesis stated in a news, analysis or feature headline ("Europe is caught in the middle");
   - the outlet's own "could", "may" or "set to" carrying the news ("Rail strike could cost the economy billions");
   - a question about how something stands or will turn out ("Can Labour end rough sleeping?");
   - an unease word over something still to come ("Unease as factory closure looms").

   Each counts only where it is blatant under §4: a sweeping verdict or loaded label, a hedge stacked on a sensational claim, a cliffhanger built to make you click, a threat word standing in for the facts ("nightmare winter ahead").

## 4. Techniques

### `manufactured_fear`: Alarmist framing
Frightening or catastrophic wording beyond what the reported facts support: threat words without who is at risk or how likely it is ("ticking timebomb", "nightmare", "chilling", "doom spiral"), catastrophic metaphors ("a recipe for disaster"), "horror as…" with nothing said about what happened, and fear words on a threat that is vague, hypothetical or still to come (rule 3).
Closest research categories: *Appeal to Fear-Prejudice* and *Exaggeration-Minimisation* (Piskorski et al., 2023).
Not: plain reports of real harm, including a fear word next to a harm the headline states ("Two dead after horror crash", version 8, rule 3); a named expert's quoted warning (rule 1); a sad word or a genre (rule 3).

### `manufactured_rage`: Conflict framing
Disagreement cast as combat or personal attack in the outlet's own words: "slams", "blasts", "rips into", "war of words", "fury as", "hits out at", mocking characterizations of what a person did ("swoons over", "meltdown"), and somebody's words staged as a fight: a person responding to another person's comments, remarks or criticism ("Lily Allen responds to David Harbour's comments…", "hits back at Guardiola's jibe", "claps back at trolls"), including a person characterizing a rival's remark ("Susan Collins Says Rival's 'Oh, Shut Up' Comment Reflects How He Treats Women").
Closest research categories: *Loaded Language* and *Name Calling-Labeling* (Piskorski et al., 2023); biased word choice (Spinde et al., 2021).
Not: sport contests, real armed clashes ("Russia launches fresh attack on Kyiv"), a quoted insult, an institution responding to an event ("Police respond to shooting", "Council responds to petition").

One named pattern sits inside this technique (version 7):
- **Sarcasm and mockery.** Snark at someone's expense in the outlet's own voice: a punchline sentence that mocks the subject ("Artificial Goes After Our Silicon Valley Overlords. Just Not Very Well."), mock sympathy ("$9000+ price tag brings a tear to my eye too"), ridicule words ("left red-faced", "meltdown", "humiliating climbdown"). Not: the wit of a signed column or review (rule 2); a joke at nobody's expense ("Two entire pallets of RTX 5090s have 'gone missing'").

### `opinion_as_fact`: Opinion as fact
In a news, analysis or feature headline (rule 2), an evaluative judgment stated in the outlet's voice as if it were fact: "X's plan is a disaster", "a shameless bribe", labels pinned on named people ("strongman", "profane"), or a verdict ("Pomp over progress … is a win for China", "Congress has never passed a real budget"). Version 8 confirms the line: a labeled editorial or signed column is never `opinion_as_fact` ("The Guardian view on Friedrich Merz…", "… | Tom McIlroy"), however strong its verdict.
Research sources: sentence subjectivity, meaning the author's own opinion rather than a reported one (Antici et al., 2024); biased language (Spinde et al., 2021); *Loaded Language*, *Name Calling-Labeling* and *Doubt* (Piskorski et al., 2023).
Only a label a reader would recognize tells them it is opinion (opinion, comment, column, review, editorial, letters): not a house section name ("Artillery Row"), and not a "must" or "should" in the headline. A first-person or argument piece filed as news, a review or preview with no review label, and a loaded label on a whole group still count.
Not: signed opinion (rule 2); a quoted person's judgment (rule 1); since version 14, a judgment carried by a question, which is `loaded_question`.

Three named patterns sit inside this technique (version 7; the fourth, the leading question, became `loaded_question` in version 14). Each carries its own rule id, so a reader can be told which one fired:
- **Sweeping verdict.** An absolute verdict or hyperbole in the outlet's voice: "has changed everything", "is turning the music gear world upside down", "Everything you've been told about X is a lie", "X will never be the same", "the biggest risk". Not: a superlative a source supplies and the headline names ("the hottest year on record, Met Office says").
- **Insinuation.** A word that hints at wrongdoing or bad faith without stating it: "suspiciously", "conveniently", "quietly" when the act is a reversal or a cut ("EPA quietly drops…"), "some wonder if", "raises questions about", "look the other way". Not: "quietly" as plain manner ("quietly released a patch"), and doubt a named source voices.
- **Loaded label.** An insulting or belittling label pinned on a person or group: "worthless vibe coded slop", "Radical Leftist Group", "useless singles", "scapegoats". (This was already in version 4; it is named here so the rules can carry it.)

### `loaded_question`: Loaded question
Since version 14, split from `opinion_as_fact`. In a news, analysis or feature headline (rule 2), a question that carries a claim, verdict or worry the headline does not state outright, so the reader takes the claim in while the outlet has only asked. The test: turn the question into a statement. If the outlet would not print that statement as it stands, and the question is doing the job of saying it anyway, it is a loaded question. It covers:
- **A verdict in the question:** "Is it time for Ed Davey to quit…?", "Was Neville right to call out Man Utd…?", "Did Jeffrey Archer ever write a good novel?", "Is pumpkin spice over?", "X: genius or fraud?".
- **A scare in the question:** "Has the next financial crisis begun?", "Is smartphone fast charging killing your battery?", "How Long Until AI Hacks Everything?", "Is this the end of X?".
- **Insinuation or sarcasm:** "Is X really…?", "…but at what cost?", "Why now?" closing a news statement, "What could possibly go wrong?", "Seriously, is anyone good enough…?", "The question no one dares ask: …".
- **A nudge after a statement:** "Another bad run of results has Michael Carrick feeling the heat. Should he?", "Republicans are spending millions to oust a Democrat who won by 187 votes. Should he worry?", "…– but are centrists listening?".
- **A hook aimed at the reader after a threat or tease:** "Ransomware has a new target. Is your backup ready?", "UK's weirdest hobbies revealed – but could you find your tribe?".

Closest research categories: *Doubt* and *Loaded Language* (Piskorski et al., 2023); implying a claim without asserting it (Scott, 2023).
**Why it is its own technique** (2026-11-18). A question states nothing, so "is this something that happened, or somebody's view of it?" does not fit it. The move is to carry the claim inside the question, where the outlet can say it only asked. The check hands the claim back to the reader as a statement to weigh.
Not: a plain question the piece answers, or a topic (rule 5: "Why have China's oil prices reached a record high?"); an open question about how something stands or will turn out (rule 7: "Can Labour end rough sleeping?", "Will Pedro Sánchez fall?"); a question that doubts a claim ("Trump says US has 'permanent control' of Greenland security: Does it?"); service ("Fixed Annuity Payouts Are Spiking: Is it Time to Lock In?"); a reader's dilemma question, which is `curiosity_gap` (rule 5); a signed column, review or letter (rule 2); a question in quotation marks with a named speaker (rule 1).

### `curiosity_gap`: Withheld detail
The headline deliberately holds back the key fact so you have to click: "You won't believe…", "This one thing…", "What happened next", "this" or "these" pointing at something the headline never names, an announced answer ("Here's why…", "X reveals why…", "X explains how…", rule 5), and a statement without its content ("X breaks silence on…", "X speaks out about…", "X reacts to…") when the headline does not say what was said. Also a tease: a subject described but not named, when the description is written to make you want the name ("one of the scariest games I have ever played", "a molecule that raises Alzheimer's risk", "Barcelona star facing brutal treatment"; plain labels like "AI giant" are compression, not a tease); a shopping headline that withholds the product ("…Another Reason to Shop Prime Day"); a numbered list that sells its items as an announced answer ("These 5 chip stocks…", "Here Are 5 Things He Learned"; "Ask Your Spouse These 3 Questions First" is a how-to that names its subject, rule 4); "the truth behind", "the story behind" or "the true cost of", which announce an answer, even after a plain question ("Why is the Plaza Hotel Closing? The Story Behind the Façade"; a fact-check should carry its verdict); and a pronoun standing in for the person the story is about ("He Wants AI Benchmarking To Be a Science, Not a Dark Art"). A cryptic title that promises nothing ("The pile-on paradox") is a label, not a lure.
Research source: clickbait that withholds information the article supplies, whether a phrase, a passage or several pieces (Hagen et al., 2022; Fröbe et al., 2023).
Not: a plain question or topic (rule 5); service and how-to phrasing (rule 4); a statement or answer the headline gives ("X breaks silence: 'We are still friends'", "X speaks out against the bill").

### `vague_sourcing`: Anonymous sourcing
Authority borrowed from people nobody can check: "sources say", "insiders reveal", unnamed "experts warn", "it is understood", "according to reports", "Report:" or "reportedly" when the headline names no outlet (since version 12, not when the headline reports a public act anyone can check, such as a court order, a ruling or a vote: "Judge orders X to sit for deposition: Reports"), unnamed researchers, scientists, analysts or officials giving a view or a warning ("…, researchers say", "Experts warn…", "U.S. officials say") when the headline names no institution or publication, a label put on something with nobody named as putting it there ("new rules are branded a 'nightmare'", version 12), and a quoted dramatic claim with nobody behind it (rule 1). A bare quoted emotion with no speaker ("'Angry' Christa Pike…") is not a dramatic claim.
Closest research category: *Appeal to Authority* where the authority is unnamed (Piskorski et al., 2023); implying a claim without asserting it (Scott, 2023).
Not: named people or institutions with a checkable publication ("ONS figures show"); quoted words with a named or identified speaker (rule 1); since version 11, unnamed scientists or researchers reporting a finding ("Scientists find…", "Researchers discover…", "…, researchers find"), which is judged under `science_overhype` instead; since version 13, an unnamed study, survey, poll or data, which is `study_as_proof`.

### `study_as_proof`: Study as proof
Since version 13, split from `vague_sourcing`. An unnamed study, survey, poll, trial or data set offered as the proof of the headline's point ("…, study finds", "Study Finds…", "New Research Reveals…", "Poll:", "…: Survey", "Data shows…", "…, according to data", "…, Clinical Trial Finds") when the headline names no institution, journal or pollster and (since version 12) neither the summary nor the story names who did it. An unnamed report ("…, report finds", "Report:") is not a study: "Report:" with no outlet stays `vague_sourcing`, and "…, report finds" is not counted (version 8).
Closest research category: *Appeal to Authority* where the authority is unnamed (Piskorski et al., 2023).
**Why it is its own technique** (2026-11-17). The study behind a "…, study finds" headline is often named further down the story, so asking who ran it misses the point. The trouble is the finding the writer picked: one result, chosen to fit the story, standing in for the whole study. The headline cannot show whether that happened, so the flag does not say it did; it hands the reader the check: what did the study measure, and does the story say what else it found?
**Why an unnamed poll, survey or study is a credibility concern** (2026-11-12; this explains the rule above and changes no label). A number borrows its weight from whoever produced it, and a headline that says only "poll shows", "survey finds" or "study says" gives the reader nobody to weigh. Polls and surveys differ a great deal in who paid for them, who was asked, how those people were chosen and how the question was worded, and some publish none of that; a headline can also pick the one finding from a study that makes the strongest line. None of this says the number is wrong. It says the reader cannot tell from the headline, which is what the flag teaches them to notice. The flag stands down when the headline, the summary or the story names who did it, because the reader then has someone to look up. MLE does not judge whether a named pollster's methods are sound: that would be rating a source, and verdicts stay source-blind.
Not: a named institution, journal or pollster ("ONS figures show", "a Harvard study finds"); unnamed scientists or researchers reporting a finding (`science_overhype`, version 11).

### `speculative_hedge`: Speculation
An unconfirmed dramatic outcome presented as the news, carried by a hedge: "could spark…", "set to…", "on the brink of…", where the hedge is what turns a non-event into a headline.
Research source: misleading implicature, where the headline lets the reader infer more than it states (Scott, 2023).
Not: an ordinary forecast from a named institution; a hedge the headline scopes honestly ("…in early trials").

### `urgency_pressure`: Manufactured urgency
Time pressure or shouting the story does not need: "BREAKING:" on a story that is not breaking, "act now", "last chance", several exclamation marks, words in capitals for emphasis ("MORE hikes").
No direct research category from 2021 onward; defined from the project taxonomy (`docs/taxonomy.md`).
Not: real deadlines and official alerts.

### `science_overhype`: Early science
A big health or technology claim with no mention of what stage the work is at ("could cure…", "reverses ageing") when the summary shows the work is in mice, cells or simulations. Since version 11 it also covers a finding credited to unnamed scientists when the headline overreaches: a breakthrough or cure claim, or a lab result stated as a general one ("Scientists find a way to fight dementia", "Scientists extend lifespan by 30%" with no organizm named). The same finding stated plainly is `NONE` ("Scientists detect a new moon around Saturn").
No research source from 2021 onward in this codebook; defined from the project taxonomy (`docs/taxonomy.md`).
Not: headlines that state the stage ("…in mice"), or (since version 12) that carry their own honest hedge ("…linked to slower ageing", "a potential early warning sign"), even when the article shows the work is early or observational.

## 5. Worked examples

Written for this codebook; none comes from the labeling set.

| Headline | Article type | Label |
| :--- | :--- | :--- |
| Council approves £40m plan for new homes in Leeds | news | `NONE` `-` |
| Minister's housing plan is a cynical giveaway to landlords | news | `SUBTLE` `opinion_as_fact` |
| Minister's housing plan is a cynical giveaway to landlords \| Jane Doe | opinion | `NONE` `-` |
| Horror as 'ticking timebomb' of damp homes leaves families terrified | news | `PROMINENT` `manufactured_fear` |
| You won't believe what this pensioner found in her loft | feature | `PROMINENT` `curiosity_gap` |
| Insiders say rebels plot revenge as war of words erupts | news | `PROMINENT` `manufactured_rage,vague_sourcing` |
| Energy bills could rise again next year, regulator says | news | `NONE` `-` |
| Drug reverses memory loss, scientists say (summary: tested in mice) | news | `SUBTLE` `science_overhype` |
| Mayor defends budget: 'This is a disaster for the city' | news | `NONE` `-` |
| How to fix a squeaky door | feature | `NONE` `-` |
| Singer shares why she quit social media | feature | `SUBTLE` `curiosity_gap` |
| Why the pound is falling | analysis | `NONE` `-` |
| Here's why the pound is falling | news | `SUBTLE` `curiosity_gap` |
| Actor breaks silence on divorce | news | `SUBTLE` `curiosity_gap` |
| Actor breaks silence on divorce: 'We are still friends' | news | `NONE` `-` |
| Singer responds to rival's comments about her new album | news | `SUBTLE` `manufactured_rage` |
| Police respond to shooting at shopping centre | news | `NONE` `-` |
| Shocking footage shows car plunging into river | news | `SUBTLE` `manufactured_fear` |
| Footage shows car plunging into river | news | `NONE` `-` |
| Man held over 'bomb plot' at airport | news | `SUBTLE` `vague_sourcing` |
| Minister says plan is 'not working' | news | `NONE` `-` |
| Is the four-day week really the future of work? | news | `SUBTLE` `loaded_question` |
| Can the four-day week work in Britain? | analysis | `NONE` `-` |
| The minister's U-turn has changed everything | news | `SUBTLE` `opinion_as_fact` (sweeping verdict) |
| Council suspiciously quiet on budget gap | news | `SUBTLE` `opinion_as_fact` (insinuation) |
| Chancellor unveils growth plan. It does not go well. | news | `SUBTLE` `manufactured_rage` (mockery) |
| Rail strike could cost the economy billions | news | `NONE` `-` (rule 7: a lone hedge, not blatant) |
| How are the unions shaping up for the next round of talks? | analysis | `NONE` `-` (rule 7: an open question, not a cliffhanger) |
| Union boss says he has a plan to end the strike | news | `NONE` `-` (rule 7: a reader could fairly say the plan is the story) |
| Unease in market town as factory closure looms | news | `NONE` `-` (rule 7: a mood described, not alarm) |
| You won't believe what the union boss said next | news | `PROMINENT` `curiosity_gap` (a tease at full force: plain on any reading) |
| Scientists detect a new moon around Saturn | news | `NONE` `-` (§4: a plain finding; unnamed scientists are not a technique on their own) |
| Scientists find a way to fight dementia | news | `SUBTLE` `science_overhype` (§4: a cure-style claim with no stage) |
| Scientists find a way to slow dementia in mice | news | `NONE` `-` (the stage is stated) |

## 6. Sources

- Antici, F., et al. (2024). A Corpus for Sentence-Level Subjectivity Detection on English News Articles. LREC-COLING 2024. https://aclanthology.org/2024.lrec-main.25/
- Fröbe, M., et al. (2023). SemEval-2023 Task 5: Clickbait Spoiling. https://aclanthology.org/2023.semeval-1.312/
- Hagen, M., et al. (2022). Clickbait Spoiling via Question Answering and Passage Retrieval. ACL 2022. https://arxiv.org/abs/2203.10282
- Piskorski, J., et al. (2023). SemEval-2023 Task 3: Detecting the Category, the Framing, and the Persuasion Techniques in Online News in a Multi-lingual Setup. https://aclanthology.org/2023.semeval-1.317/
- Scott, K. (2023). 'Deceptive' clickbait headlines: Relevance, intentions, and lies. Journal of Pragmatics, 218, 71–82. https://doi.org/10.1016/j.pragma.2023.10.004
- Spinde, T., et al. (2021). Neural Media Bias Detection Using Distant Supervision With BABE. Findings of EMNLP 2021. https://aclanthology.org/2021.findings-emnlp.101/

## 7. Changes

- **Version 14 (2026-11-18).** Question headlines that read as opinion, speculation or provocation are a different move from opinion stated as fact: the question states nothing, and the claim rides inside it. A new technique, `loaded_question` ("Loaded question"), takes the version 7 leading question out of `opinion_as_fact` and names what it covers: a verdict, a scare, insinuation or sarcasm, a nudge after a statement, and a hook aimed at the reader. The line was drawn from a reading of 42 question headlines on 2026-11-18, each marked trick, gentle note or fair. A plain or open question stays `NONE` (rules 5 and 7). A plain question that holds back a short answer the headline could have given ("Why are two college football teams playing at Wembley on Saturday?") was raised at the same time and is not decided: rule 5 is unchanged.
- **Version 13 (2026-11-17).** Headlines that say "study shows" or "research suggests" usually quote the study in the story, but pick the data that fits the writer's line, so "who ran it?" was the wrong question. A new technique, `study_as_proof` ("Study as proof"), takes the unnamed study, survey, poll and data cases out of `vague_sourcing`, with the same boundaries (version 12's stand-down when the article names who did it still holds). It changes which technique a row names, not whether it is flagged or at what level.
- **Clarification, 2026-11-12 (still version 12; no label changes by it).** §4 `vague_sourcing` now says why an unnamed poll, survey or study is a credibility concern. This followed the precision-gap pass, where a reviewer confirmed five such headlines as clear cases ("…, Study Finds", "…: Survey", "…, new study says", "…, survey finds").
- **Version 12 (2026-10-23).** From a reading of the articles behind the first flag review (`docs/benchmark/flag-review/2026-10/report.md`). Three changes: an unnamed study is not `vague_sourcing` when the summary or story names who did it (the ScienceAlert data-centre study names the Carnegie Institution and its journal), which narrows the version 7 "Yes, always"; a "Reports" credit counts only when the claim cannot be checked, not on a public act such as a court order (narrowing the version 4 ruling); and a science headline that carries its own honest hedge stays `NONE` even when the article shows the work is early. A passive label with nobody named ("branded a 'nightmare'") is named as `vague_sourcing`, as the Daily Mail story behind it names nobody.

- **Version 11 (2026-10-23).** From the first monthly flag review (`docs/benchmark/flag-review/2026-10/report.md`), where four of the headlines MLE flagged as anonymous sourcing for "Scientists find…" or "Scientists discover…" read to the reviewer as early-science hype, or as a plain discovery. So a finding credited to unnamed scientists or researchers is now judged as science (`science_overhype` when the headline overreaches, `NONE` when it states the finding plainly), not as `vague_sourcing`. Unnamed experts, analysts or officials giving a view or a warning stay `vague_sourcing`, as do unnamed studies, surveys and polls, which have not yet been revisited.

- **Version 10 (2026-10-22).** MLE is a teaching tool, not a filter: it should speak only when it is clearly right, and a missed headline costs little while a wrong flag costs a lot (`docs/teaching-reset-2026-10.md`, `docs/positions.md` §17). Version 10 restores the rules of version 8, reverses version 9's presumption of clickbait, and adds rule 7, the teaching bar: flag only what is plain on a first reading and could not fairly be argued, and a tie goes to `NONE`. Version 9's labels were reverted to version 8, with no new labeling run. The codebook stays at this version until a new one is issued with its reason.

- **Version 9 (2026-10-20).** The aim was to examine headlines "with strict prejudice: assume headlines are clickbait unless proven otherwise". Rule 7, the plain-reporting test, replaces the old tie-break: a headline that is not plainly reporting is flagged, and a tie now goes to the technique, not to `NONE`. It adds four readings the earlier versions left out: a thesis stated as fact in news or analysis is `opinion_as_fact`; the outlet's own unattributed "could" or "may" carrying the news is `speculative_hedge`; a question about how something stands or will turn out is `curiosity_gap`, as is a headline that says something exists and holds back what it is; an unease or threat word over something still to come is `manufactured_fear`. The carve-outs of versions 3–8 are kept (rules 1–5). The worked examples are written for this codebook; none comes from the 200-row spot-check, which is used to measure version 9. Calibrated on those 200 blind spot-check rows (κ 0.60 against the person who labeled them, from 0.52 under version 8), then applied to every `NONE` row the same day: 1,035 of 4,226 changed. **Reversed by version 10.**

- **Version 8 (2026-10-19).** From a blind spot-check of 200 tuning rows (agreement with the version 7 labels 74%, κ 0.48; `docs/benchmark/spot-check-2026-10/report.md`). Most of the disagreements sat on four rules, which were reworked:
  - rule 3: a fear word next to a harm the headline states is description ("Two dead after horror crash"); it counts when it stands in for the facts or the threat is vague or future;
  - rule 1: quotes around what someone is accused of doing are shorthand for "alleged" and are `NONE`; only a quoted loaded label, sneer or characterization counts;
  - rule 2 is confirmed (labeled opinion is never `opinion_as_fact`; an unsigned verdict stated as news is);
  - rule 4 is confirmed (a practical guide that names its topic is `NONE`).

  Two more calls follow from the reviewer's own labels: an unnamed report ("…, report finds") is not anonymous sourcing (the version 7 extension is reverted), and "disgraced" and "yobs" are not loaded labels. Rows these rules touch are re-judged in a targeted re-label.

- **Version 7 (2026-10-19).** Four rhetorical and cynical styles are flagged when obvious, as named patterns inside the existing techniques rather than new ones:
  - leading question, sweeping verdict and insinuation, all under `opinion_as_fact`;
  - sarcasm and mockery, under `manufactured_rage`.

  Rule 5 gains the leading-question carve-out. Loaded labels, already in version 4, are named as a pattern. A headline opening on unnamed scientists, researchers or experts as the authority ("Scientists Discover…") is also confirmed as `vague_sourcing`, as version 6 had it. Rows labeled under version 6 that these patterns change are re-judged in the label audit.

  The targeted label audit ran the same day: 79 labels changed from `NONE`. The same day, an unnamed study, survey or poll as the authority became `vague_sourcing`, "Yes, always", like unnamed scientists (§4). An unnamed report ("…, report says") follows by the same reasoning as "Report:" with no outlet. The rows this touches are re-judged in the full version 7 re-label.

  The full re-label ran the same day: 300 of 4,065 `NONE` rows changed. Its adjudication principles were: the bar is "obvious" and a tie goes to `NONE`; live-blog stubs ("Here's the latest.") and topic labels ("What to know") stay `NONE`; a named person "calls out" or "attacks" stays `NONE` unless the remark itself is characterized; and a quote with an identifiable speaker (court, police) is not anonymous.

- **Version 6 (2026-10-19).** Decisions on the open questions raised by reported headlines (`fixtures/headlines/owner-reported-2026-10.tsv`):
  - a person characterizing a rival's remark is conflict framing (§4 `manufactured_rage`);
  - a pronoun standing in for the person the story is about is `curiosity_gap`;
  - "the story behind" announces an answer, like "the truth behind", even after a plain question;
  - unnamed researchers, scientists, analysts or officials with no named institution or publication are `vague_sourcing`. This was decided for "researchers say"; "officials say" follows by the same reasoning.

  Rows in other sets that these change are left for the re-judging pass. They include four tuning rows labeled `NONE` that end "…, researchers say" or open "Scientists Find…", and one "…, U.S. officials say" row in `harvested`.

- **Version 5 (2026-10-13).** From a close reading of recent coverage:
  - "here's what you need to know" closing a news headline is `curiosity_gap`; a headline that opens with it, or goes on to name its subject, stays service (rules 4 and 5);
  - a first-person reader's dilemma closed by a verdict question ("Is she right?", "Is this risky?") is `curiosity_gap` (rule 5). Since version 7 (2026-10-19, from the full re-label) this covers any yes/no question put to the columnist about the reader's own case ("Should I leave him?", "Do we really need a will?").

  No row outside `context-2026` contains either construction with a different label: the one `harvested` row with a trailing "Here's what you need to know" (the Earl Spencer book) is already `SUBTLE`, though its v3 label may rest on "bombshell" alone, and two techniques would make it `PROMINENT`; it is left for the v4 re-judging pass. In `context-2026`, three held-out rows carry these constructions, and the engine rules for them were written from those rows (`docs/positions.md` §13).

- **Version 4 (2026-10-12).** Decisions from the beat-by-beat audit of the labels, confirmed 2026-10-12:
  - a subject described but not named is `curiosity_gap` when the description is written to make you want the name; plain labels stay `NONE` (Q1);
  - only a recognizable label (opinion, comment, column, review, editorial, letters) makes a headline signed opinion: not a house section name, not "must" or "should", and a link or section that says review counts as the type; argument pieces filed as news, unlabeled reviews and previews, and loaded labels on groups stay `opinion_as_fact` (Q2);
  - "Report:" or "reportedly" with no named outlet is `vague_sourcing` (Q3);
  - a shopping headline that withholds the product is `curiosity_gap` (Q4);
  - cryptic titles that promise nothing are `NONE` (Q5);
  - a numbered list is judged by its words, not its number: "These 5…" and "Here Are 5 Things…" are teases, a how-to that names its subject is not (Q6);
  - "the truth behind" and "the true cost of" announce an answer and are `curiosity_gap` (Q7);
  - no rule for crowd anger beyond its words; witness feelings are `NONE`; a bare quoted emotion with no speaker is `NONE`; fear and conflict words about fiction count (Q8);
  - two distinct techniques are always `PROMINENT`, and the engine's scoring should match (Q9);
  - read the summary, then the story, before marking a headline unsure. Context settles facts and never adds a technique (§1).

  The v3 labels in `harvested`, `broadsheet-2026` and `beats-2026` have not yet been re-judged under these rules; that is a separate pass, and the rows it changes are listed with it.

- **Version 3 (2026-10-11).** Decisions after the version 2 review:
  - an announced answer is `curiosity_gap` ("Here's why…", "This is why…", "X explains/reveals/shares/exposes/details why, how or what…"); a plain question or topic stays an explainer, and service phrasing stays `NONE` (rules 4 and 5);
  - somebody's words staged as a fight are `manufactured_rage`; an institution responding to an event is not (§4);
  - a quoted fragment with no named speaker counts as `vague_sourcing` only when it carries a dramatic claim (rule 1);
  - a fear word on a real event or its footage is `manufactured_fear`; "Moment…" captions stay `NONE` (rule 3);
  - what an article says about itself can change how a word reads, who published it never can, and a genre never excuses a technique (rule 6).

  Every row in `broadsheet-2026` and `harvested` containing these constructions was re-judged against this text by a single model reviewer. 23 `broadsheet-2026` labels and 60 `harvested` labels changed.
- **Version 2 (2026-10-11).** Answers to questions 2 and 3 of the audit of the `harvested` set:
  - a reason framed as a disclosure ("reveals why", "shares why") is `curiosity_gap` even when it names the subject (rule 5);
  - so is a statement without its content ("breaks silence", "speaks out", "reacts to") (§4, `curiosity_gap`).

  Every row in `broadsheet-2026` and `harvested` that these two changes touch was re-judged. One `broadsheet-2026` label and eight `harvested` labels changed.
