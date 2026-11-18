# Blind spot-check, 2026-10-19

> The scoring script this report was made with (`tools/spotcheck`) was removed on 2026-10-23. It is not in this repository.

## What was done

A person labeled 200 tuning rows blind on a private page: 20 labeled `NONE` and 20 labeled flagged, from each of `broadsheet-2026`, `beats-2026`, `context-2026`, `week-2026-10-19` and `harvested` (seed 20261019; `sample.json`). They never saw the model label. The answers are in `owner-labels.json`. They were scored with:

    node tools/spotcheck/score.mjs build/spotcheck/key.json docs/benchmark/spot-check-2026-10/owner-labels.json build/spotcheck/items.json

The model labels compared are the version 7 labels after the full re-label (fixtures at commit `68d9cfd`, rule pack 30).

## Agreement (flagged or not)

| Set | Agree | κ | Model flagged, reviewer NONE | Model NONE, reviewer flagged |
|---|---|---|---|---|
| all | 148/200 (74%) [68%–80%] | 0.48 [0.35–0.63] | 19 | 33 |
| beats-2026 | 30/40 (75%) [60%–86%] | 0.50 [0.22–0.75] | 2 | 8 |
| context-2026 | 27/40 (68%) [52%–80%] | 0.35 [0.05–0.62] | 9 | 4 |
| harvested | 32/40 (80%) [65%–90%] | 0.60 [0.34–0.80] | 3 | 5 |
| week-2026-10-19 | 30/40 (75%) [60%–86%] | 0.50 [0.22–0.75] | 3 | 7 |
| broadsheet-2026 | 29/40 (73%) [57%–84%] | 0.45 [0.23–0.71] | 2 | 9 |

Intervals are 95% (Wilson for agreement, bootstrap for κ). Stratum-weighted agreement: 73.9%. Both flagged: 81; share a technique: 44. Reviewer unsure: 3.

**Reading it.** Agreement of 74% and κ 0.48 is moderate. On a 40-row set the interval is too wide to rank sets. The reviewer flags more often than the labels: 33 rows the labels call `NONE` against 19 the other way. Where both flag, they name the same technique only about half the time. The reviewer noted that some rows are tricky.

## What the disagreements say about the codebook

Most disagreements sit on a few rules. Recommendations for them were drawn up on 2026-10-19. They become codebook version 8.

| Rule | What the reviewer did | Ruling |
|---|---|---|
| 3, a fear word on a real event | "Two dead after horror crash", "Epping fire horror as locals urged to keep windows shut", "Horror at train station as youths wield machetes": all clean (s047, s046, s026) | A fear word counts only when it stands in for the facts or the threat is vague or future. A real, stated harm described with "horror" is clean. |
| 1, quoted words with no speaker | 'biting and punching crew', 'stolen' goods, 'Hamas tribute', 'Elon Is a Nuke': all clean (s095, s161, s121, s132) | Reported conduct or an allegation in quotes is shorthand for "alleged" and is clean. A loaded label or sneer the outlet uses to frame the story stays `vague_sourcing`. |
| 2, opinion | Flagged a Guardian editorial, a signed column and other opinion pieces (s078, s041, s017, s117, s166); left "Badenoch must go bigger" clean | Confirmed. A labeled editorial or signed column is never `opinion_as_fact`; an unsigned verdict stated as news ("Congress has never passed a real budget", s151) is. |
| 4, service | Flagged "Here's what you need to do", "everything you need to know" and "Here's how to maximise the benefits" (s019, s181, s051) | Confirmed: a practical guide that names its topic stays clean. |
| Unnamed report | "…, report finds" and "…, report says" clean (s094, s176) | The rule pack 29 extension from unnamed studies to unnamed reports is reverted. |
| Loaded labels | "disgraced ex-Labour MP" and "yobs" clean (s154, s173) | Both words come out of the loaded-label list. |

The rest are scattered single calls: teases (11), hedges (6), science claims (3), rage (2) and fear on threats (4). They show where human readers diverge, but no single rule explains them, so they don't change the codebook on their own.

## After codebook version 8

After the version 8 re-label (12 of these 200 labels changed), agreement with the reviewer on the same rows is 154/200 (77%), κ 0.55. This is not independent: the codebook was adjusted on these rows. The remaining gap runs one way: the reviewer flags 36 rows the labels call `NONE` and leaves 10 flagged rows clean.

## All disagreements

| Key | Set | Model | Reviewer | Outlet | Headline |
|---|---|---|---|---|---|
| s003 | harvested | NONE  | SUBTLE opinion_as_fact | New Atlas | Jensen Interceptor GTX is Britain's emphatic reply to the Ford Mustang |
| s009 | harvested | NONE  | PROMINENT opinion_as_fact, vague_sourcing | Deutsche Welle | US-China rivalry is reshaping global trade — and Europe is caught in the middle. |
| s010 | harvested | NONE  | SUBTLE opinion_as_fact | Business Insider | The 20 best airlines in the world, ranked by passengers |
| s017 | broadsheet-2026 | NONE  | PROMINENT opinion_as_fact, speculative_hedge | The Guardian | Burnham’s electricity grid idea is interesting – but it’s not ‘public control’ |
| s019 | broadsheet-2026 | NONE  | SUBTLE curiosity_gap | The Independent | Ofgem energy price cap hike to hit UK from Thursday. Here’s what you need to do |
| s020 | week-2026-10-19 | NONE  | SUBTLE curiosity_gap | Wired | The Tesla Roadster 2 Is Here—and It Flies |
| s021 | broadsheet-2026 | NONE  | SUBTLE vague_sourcing | The Washington Post | Reports that Netanyahu ignored Oct. 7 warnings dominate Israeli election |
| s023 | harvested | NONE  | SUBTLE curiosity_gap | BBC Sport | How is Scotland squad shaping up for Pocognoli debut? |
| s024 | context-2026 | SUBTLE curiosity_gap | NONE  | Inside Climate News | Greece’s Seafood Problem Starts Elsewhere |
| s025 | week-2026-10-19 | NONE  | PROMINENT curiosity_gap, manufactured_rage | Politico | ‘Is this like a hit piece or what?': Dan Sullivan's MAGA balancing act |
| s026 | context-2026 | SUBTLE manufactured_fear | NONE  | Daily Mirror | Horror at West Sussex train station as youths 'wield machetes' and brawl on platform |
| s027 | week-2026-10-19 | SUBTLE curiosity_gap | NONE  | Daily Star | Davina McCall opens up on 'challenges' as she faces new health battle |
| s041 | broadsheet-2026 | NONE  | SUBTLE speculative_hedge | The Guardian | Inflation is rising, interest rates are going up – and Labor’s economic woes could be One Nation’s gain \| Tom McIlroy |
| s046 | harvested | PROMINENT  | NONE  | Daily Mirror | Epping fire horror as locals urged to 'keep windows and doors shut' over huge smoke clouds |
| s047 | broadsheet-2026 | SUBTLE manufactured_fear | NONE  | Daily Mirror | Two dead after horror crash between camper van and lorry in Somerset village |
| s051 | week-2026-10-19 | NONE  | PROMINENT curiosity_gap, science_overhype | Sydney Morning Herald | Weightlifting can help your heart. Here’s how to maximise the benefits |
| s070 | week-2026-10-19 | NONE  | SUBTLE curiosity_gap | Business Insider | This chart shows how junior-level hiring is down at Anthropic due to AI |
| s077 | week-2026-10-19 | NONE  | PROMINENT speculative_hedge, vague_sourcing | Financial Times | Australian MP struck by car in alleged attack |
| s078 | broadsheet-2026 | NONE  | SUBTLE opinion_as_fact | The Guardian | The Guardian view on Friedrich Merz: Germany’s chancellor must change course to halt the rise of the AfD \| Editorial |
| s082 | beats-2026 | NONE  | PROMINENT manufactured_fear, opinion_as_fact, speculative_hedge, vague_sourcing | Axios | Why France is a warning sign for the markets |
| s084 | beats-2026 | NONE  | PROMINENT curiosity_gap, manufactured_fear, speculative_hedge, vague_sourcing | New Atlas | Wait, AI is hacking government networks now? |
| s086 | beats-2026 | SUBTLE opinion_as_fact | NONE  | The Critic | Badenoch must go bigger |
| s088 | harvested | SUBTLE  | NONE  | Manchester Evening News | Floyd Samba lifts lid on Enzo Maresca chat ahead of 'dream' Man City debut |
| s094 | week-2026-10-19 | SUBTLE vague_sourcing | NONE  | Guardian Environment | US corporate lobbyists mounted ‘blitz’ to dilute landmark EU deforestation law, report finds |
| s095 | context-2026 | SUBTLE vague_sourcing | NONE  | Daily Star | Actress Selma Blair arrested after 'biting and punching crew' during flight |
| s100 | context-2026 | SUBTLE opinion_as_fact | NONE  | Mother Jones | Trump Blames Ukraine—Not His Iran War—for Our Skyrocketing Fuel Prices |
| s108 | broadsheet-2026 | NONE  | SUBTLE manufactured_fear | BBC News | We fear for our lives after being told our abusive exes will be freed from jail early |
| s117 | broadsheet-2026 | NONE  | SUBTLE opinion_as_fact | The New York Times | The Trump Brand Has Tanked. The G.O.P. May Pay the Price. |
| s121 | broadsheet-2026 | SUBTLE vague_sourcing | NONE  | Daily Mail | REV GILES FRASER: Church's 'Hamas tribute' is yet another howler from our posturing progressives |
| s124 | broadsheet-2026 | NONE  | SUBTLE manufactured_fear | Daily Mail | Grooming gangs inquiry to investigate child sexual abuse in Birmingham and Wales as scope of £65million probe widens |
| s126 | harvested | NONE  | PROMINENT manufactured_fear, opinion_as_fact | The Guardian | Unease in ‘newly independent’ Piddington as world media and Danny Tommo descend |
| s129 | context-2026 | SUBTLE science_overhype | NONE  | New Atlas | MIT’s drone brain neutralizes hazards in disaster zones |
| s131 | context-2026 | NONE  | SUBTLE science_overhype | New Atlas | Spiral solar tower promises near-continuous power for Moon bases |
| s132 | context-2026 | SUBTLE vague_sourcing | NONE  | The Atlantic | ‘Elon Is a Nuke’ |
| s144 | broadsheet-2026 | NONE  | SUBTLE curiosity_gap | Daily Mail | Little person internet star and his shaven headed girlfriend are charged with torching church |
| s147 | context-2026 | SUBTLE manufactured_rage | NONE  | CBC News Canada | 'More men need to be standing up': Dan Levy calls out Shane Gillis SNL joke about women's clothing |
| s148 | context-2026 | SUBTLE curiosity_gap | NONE  | Business Insider | Photos show why George and Amal Clooney are still one of the best-dressed couples in Hollywood |
| s151 | beats-2026 | NONE  | SUBTLE opinion_as_fact | The Hill | Congress has never passed a real budget |
| s154 | harvested | SUBTLE  | NONE  | Daily Express | Green leader Zack Polanski backed by disgraced ex-Labour MP Chris Williamson |
| s159 | beats-2026 | NONE  | PROMINENT curiosity_gap, opinion_as_fact | Daily Star | 'Jeffrey Archer showed me his true colours with one very obvious thing from his books' |
| s161 | week-2026-10-19 | SUBTLE vague_sourcing | NONE  | Business Insider | Resale website including Vinted are flooded with 'stolen' goods in the wake of French looting |
| s166 | beats-2026 | NONE  | SUBTLE opinion_as_fact | The Atlantic | Barack Obama Looks Left |
| s167 | beats-2026 | NONE  | SUBTLE curiosity_gap | CBC News Canada | Former NHL player happy that horse-drawn vehicles set to be legal in Saskatoon |
| s168 | beats-2026 | NONE  | SUBTLE curiosity_gap | PC Gamer | Critical Role's latest D&D episode was so stressful, I discovered a new noise my mouth can make |
| s173 | beats-2026 | SUBTLE opinion_as_fact | NONE  | Manchester Evening News | Salford mayor calls for city to unite following launch of murder investigation, HMO attack, removal of anti-migrant banners and reports of yobs terrorising suburb |
| s174 | context-2026 | NONE  | PROMINENT curiosity_gap, speculative_hedge | ABC News Australia | How NRL's Super League bid could revive a fallen giant |
| s176 | context-2026 | SUBTLE vague_sourcing | NONE  | PBS NewsHour | Sudan's famine risk worsened by conflict, drought and funding shortages, report says |
| s178 | context-2026 | NONE  | SUBTLE curiosity_gap | Business Insider | Elon Musk takes a dig at Waymo's pet collision history and says Tesla Robotaxi has a solution |
| s179 | week-2026-10-19 | NONE  | SUBTLE manufactured_fear | Sky News | Police investigating threatening 'hoax' emails sent to schools |
| s181 | week-2026-10-19 | NONE  | SUBTLE curiosity_gap | Guardian Environment | Cop17 at a glance: everything you need to know about the UN’s nature summit |
| s184 | context-2026 | NONE  | SUBTLE science_overhype | ScienceDaily | Just 7 days of strength training started shrinking fat cells in mice |
| s193 | beats-2026 | NONE  | PROMINENT opinion_as_fact, speculative_hedge | Kiplinger | 5 Retirement Savings Strategies Beyond Your 401(k) Match |

