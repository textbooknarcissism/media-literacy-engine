# The nine techniques

Plain-language reference for what the Media Literacy Engine detects. The working version (the
actual words and numbers) is in
`core/flagging/src/main/resources/rulepack-v1.json`. If the two disagree, the
JSON is what the engine actually does.
Why each line falls where it does, and the evidence for it, is in
[positions.md](positions.md).

**Version 2.** Version 1 was written from imagination and flagged 1% of real
headlines, with three categories matching nothing at all. Every word below was
then observed in real copy from UPI, PBS, BBC, Guardian, Sky, Daily Mail, Daily
Express, SciTechDaily, New Atlas and Ars Technica.

Tone rule for all user-facing text: describe the **technique**, never the
publication or the writer. "This headline uses manufactured urgency" is fine.
"This outlet is dishonest" is not, and is also a legal problem.

---

## Two things that took real headlines to learn

**Outlets use different words for the same trick.** Tabloids write "sources
say"; broadsheets write "analysts say", "study suggests", "sources close to".
Identical mechanism, different register. Version 1 only knew the downmarket
vocabulary, which is why it found nothing in serious copy.

**Honesty must never be what gets a headline marked.** A headline that states
its own limits ("may slow tumour growth *in mice*") has been straight with
you. Those phrases now switch off both speculative framing and science overhype.

---

## Three codebook rules that apply everywhere

The benchmark is labeled by [`docs/benchmark/codebook.md`](benchmark/codebook.md),
and since rule pack 24 the engine follows the same rules.

**Quoted words belong to the speaker** (rule 1). "Mayor defends budget: 'This
is a disaster for the city'" reports what the mayor said; the outlet has called
nothing a disaster. Words inside quotation marks never count toward alarm,
conflict or opinion as fact. A short scare-quoted fragment ("man seen with
'gun'") is different: it still counts toward vague sourcing, as a soft signal.

**Signed or labeled opinion is never opinion as fact** (rule 2). A byline
("SARAH VINE: ...", "... | Jonathan Wilson") or a label ("Opinion:", "Column:",
"Letters", "Elden Ring review:", "The Guardian view on ...") has already told
the reader whose judgment it is. "Analysis" is not such a label: an analysis
piece speaks in the outlet's voice. A signed column can still use alarm or a
tease, and those are judged as normal.

**An explainer that names its subject is not a curiosity gap** (rules 4 and 5).
"Here's why Apple's Mac Studio has become so expensive" and "What the rate cut
means for your mortgage" tell you what the piece is about. "I asked 5 experts
whether coffee is healthy. Here's what they said" and "This semiconductor stock
is in the dumps" keep the point back.

---

## 1. Manufactured fear
Implies danger without saying who is at risk or how likely it is.

Full strength: alarming, terrifying, horrifying, nightmare, chilling, sickening,
ticking timebomb, amid fears, sparks fears, horror as, before it's too late.

Soft: warns, deadly, dangerous, at risk, outbreak, collapse, crisis, threat,
brutal, disturbing, heartbreaking, chaos, crackdown.

Structural signals. These are project heuristics: none of the studies MLE cites
tests these constructions, and on the benchmark they have matched one headline
between them (see METHODOLOGY §F).
- **Pseudo-reassurance alarm**: Tells the reader not to worry about an extreme hazard or pathogen (*"You Probably Aren't Going to Get the Plague"*, *"Why you shouldn't panic about bird flu"*, *"Relax: an asteroid won't hit Earth"*), putting the catastrophe in the headline under cover of reassurance.
- **Temporal suspension threat**: Defers a disaster with a time qualifier (*"The grid won't collapse this winter (for now)"*, *"Your savings aren't gone just yet"*), implying it is only a matter of time.
- **Survival challenge frame**: Frames an ordinary seasonal, economic, or lifestyle topic as a survival question (*"Can your body survive the summer heatwave?"*, *"Will your savings survive the autumn budget?"*).

The split is essential. "Deadly crash closes M6" and "Bridge collapse kills
three" are plain factual reporting. A fear word on the event or its footage is
not (codebook v3 rule 3): "Terrifying footage shows…", "Horrifying moment…",
"harrowing ordeal" and "horror crash" count at half strength or more, because
the footage proves the event, not the feeling. A sad word ("heartbreaking
death") is not alarm, "horror film" is a genre, and "Moment police tackle
knifeman" is a caption that says what happens.

## 2. Manufactured rage
Reframes ordinary disagreement as combat.

Full strength: slams, blasts, rips into, destroys, eviscerates, lashes out,
fury as, sparks fury, furious backlash, war of words, takes aim at, warning
shot, breaks ranks.

**Somebody's words staged as a fight** (codebook v3): a person responding to
another person's comments, remarks or criticism ("Lily Allen responds to David
Harbour's comments…", "hits back at Guardiola's jibe"), or to critics, haters or
trolls. An institution responding to an event never counts ("Police respond to
shooting"), and nor does a military strike ("Russia launches fresh attack on
Kyiv"), which is why rule pack 26 dropped the "launches attack" phrases.

Soft: fury, furious, backlash, swipe, jibe, branded, defiant, row over, clash.

The British press barely uses "slams". It uses "fury", "backlash" and "swipe".
Version 1 had only the American vocabulary and scored zero in 482 headlines.

## 3. Speculative framing
Presents something unconfirmed as though it were news.

could, may, might, set to, poised to, expected to, on the brink of, tipped to.

Weighted very low (0.35) and **switched off entirely** when the headline states
its own scope. Honest reporting hedges constantly and is right to.

## 4. Vague sourcing
Borrows authority from people nobody can check. Split by how checkable the
source is:

- **Anonymous (0.6):** sources say, insiders reveal, friends fear, pals say,
  it is understood, according to reports, rumours swirl, experts warn.
- **Institutional (0.3):** researchers say, analysts say, officials say,
  reportedly, claims, insists. Real, but citable in a way an "insider" is not.

Studies, surveys and polls are no longer here: since rule pack 38 they are
their own technique, below.

Also detects **scare quotes**: a short quoted fragment inside a longer headline
("man seen with 'gun'", "'Trojan horse'"). These let a paper print a claim
without asserting it: the quotes carry the meaning and none of the
responsibility. On its own a fragment is a soft signal, because ordinary direct
quotation looks similar. It counts at half strength when nobody in the headline
is behind it and the words carry an allegation, a charge, a loaded label or an
alarm (codebook v3 rule 1): "Pilot 'tries to crash' flight", "Suspect foiled
'bomb plot'", "Antisemitism 'endemic' in universities". A named or role-identified
speaker ("Burnham says…", "construction boss blames…"), a mentioned word ("the
word 'gangs'"), a press review and a quotation that opens the headline are left
alone.

## 5. Study as proof
Offers an unnamed study, survey, poll or data set as the proof of the
headline's point: "…, study finds", "Study Finds…", "New Research Reveals…",
"Poll:", "…: Survey", "Data shows…". Split from vague sourcing in rule pack 38
(codebook 13) at the maintainer's request, with the same words, weights and
cancels, so the same headlines are flagged.

Why it is its own technique: the study is often named further down the story,
so "who ran it?" is the wrong question. The trouble is the finding the writer
picked, one result chosen to fit the story, standing in for the whole study.
The headline cannot show whether that happened, so the flag never says it did.
It asks instead: **what did the study actually measure, and does the story tell
you what else it found, or only the part that fits the headline?** For a poll:
whose poll is it, who was asked, and does the story show the whole result or
just the number that fits?

Stands down when the headline, the summary or the story's opening names who
did the work (a university, institute, journal or pollster). Unnamed scientists
reporting a finding are judged as early science, not here (codebook 11).

## 6. Curiosity gap
Withholds the actual point so you have to click.

Full strength: you won't believe, the truth about, the real reason, what really
happened, what happens next, little-known, finally explained, nobody is talking
about.

Half strength (0.5): the secret of, bombshell, mysterious, plus the announced
answers below.

Soft: hidden, strange new, surprising, uncovered, secret, stunning.

Rule pack 24 removed the blanket "here's why/how" phrases as explainers. Codebook
v3 drew the line again: a plain question or topic ("Why the pound is falling")
is an explainer, but an answer the headline announces and holds back is a tease,
so rule pack 26 brings "here's/this is why, how, what" back as a signal that
leaves service phrasing ("here's how to", "what to know", "what you need to do")
alone.

Pattern detectors & structural signals (forward reference after Blom & Hansen
2015 and Scott 2021; tactic names after Nofar et al. 2025):
- **Unnamed "this" or "these"**: a demonstrative pointing at something the
  headline never names ("This semiconductor stock is in the dumps", "Mount
  Vesuvius Buried These Scrolls for 2,000 Years"). Time ("this week"), titles
  ("belts out Fight For This Love"), service lists ("these stylist-approved
  tips"), sales copy and quotations are left alone.
- **An announced answer** (codebook v3 rule 5): "Here's why…", "This is why…",
  "Here's what we know", and somebody who reveals, shares, explains, exposes,
  details, tells, recalls or recounts why, how or what ("Pete Davidson Shares
  Why Becoming a Dad Made His Career Matter Less", "Scientists explain why cats
  purr", "Revealed: how…"). It counts even when the subject is named, because
  the headline sells the answer as a payoff. Not counted: a how-to ("explains
  how to avoid the price cap hike"), service phrasing, or a headline that gives
  the answer after a colon ("…Explains Why He Doesn't Preach Politics: It
  Divides People").
- **A statement the headline holds back**: "breaks silence", "speaks out",
  "reacts to" (codebook v2). Not counted when the headline gives the statement
  (a colon, a speech verb with what was said, a stance such as "speaks out
  against", a leading quotation), for speaking out in general ("afraid to speak
  out"), or for markets ("Stocks react to jobs report").
- A **teaser opening** ("Unmasked: ...", "Unmasking...") at half strength.
  "Revealed:" with a colon labels what follows, so it is not counted, except
  "Revealed: why/how/what…" (above).
- A **question headline** as a soft signal.
- **Cataphoric second-person gap** (*a direct appeal in Nofar et al. 2025*): Addresses the reader directly ("you") about something they supposedly missed or don't know (*"41 Things You Probably Didn't Know About..."*, *"The One Thing You Probably Didn't Notice..."*, *"What you probably missed"*), without catching plain statements (*"You probably aren't going to get..."*).
- **Prescriptive behavioral imperative** (*a direct appeal in Nofar et al. 2025*): Gives the reader a flat order or warning without stating the reason (*"Why you should never charge your phone overnight"*, *"The 1 food you should never eat"*, *"Stop doing this immediately"*).

"Watch:" is deliberately excluded: broadcasters use it to label a video, which
is a format note, not a tease. So is "Moment ...": "Moment thug stabs man in
high street" is a video caption that says what happens.

The soft list is SciTechDaily's entire house style: "Scientists Discover a
Hidden Trigger", "Finally Explained", "What Really Disappears When...". It is
curiosity-gap clickbait wearing a lab coat.

## 7. Manufactured urgency
Adds time pressure the story does not have.

breaking, urgent, just in, act now, last chance, happening now, plus two or
more exclamation marks.

**ALL-CAPS detection was removed.** Tabloids lead with the writer's name in caps
("SARAH VINE:") and broadcasters label format ("WATCH LIVE:"), so it turned
bylines into warnings. The words that genuinely signal urgency are in the list
above and match regardless of case, so nothing real was lost.

## 8. Science overhype
A big claim, a hedge, and no mention of what stage the work is at. No single
word triggers it: all three must hold:

1. A hedge: could, may, might, promises, set to, on track to, one day
2. A big claim: revolutionize, breakthrough, cure, eliminate, transform,
   world's first, nearly all, or a number with a unit ("1,000 miles", "300%")
3. **No stage qualifier**: in lab tests, early-stage, in simulations, in mice,
   prototype, years away, preliminary

Point 3 is the important one. "New battery could double range in early-stage lab
tests" is honest and must never be flagged. "New battery could give EVs 1,000
miles per charge" is the same science with the caveat removed.

For this category, `SummaryContextResolver` checks the article's RSS summary for an early-stage or animal-model caveat and shows it alongside the flag.

## 9. Opinion as fact
States a value judgment or directive as plain fact.

Full strength: makes no sense, is a disaster, should be ashamed, has no idea, everyone knows, let's be honest.

Half strength (0.5): out of touch, tone-deaf, pathetic, damning, more worried about, weird and wonderful, yet another awkward.

Separates factual reporting of events ("Minister resigns after report") from editorial evaluation ("Ministers are completely out of touch"). Never applied to signed or labeled opinion, or to words inside quotation marks (codebook rules 1 and 2, above). An analysis piece is judged as normal.
