# Changelog

Every release of the Media Literacy Engine is listed here, newest first. The build takes its version number from the newest numbered heading below, so a packaged copy of the engine always matches an entry in this file.

## How version numbers work

A version has three numbers, `MAJOR.MINOR.PATCH`, and each release raises exactly one of them.

- **MAJOR** (1.0.0 to 2.0.0): an app built on the engine may need to change its code. A technique is added, removed or renamed (apps often handle each technique by name), or a public class or function is removed or changes its shape. Until the engine is public, a new technique is a MINOR release instead, with the change spelled out in its **For apps** note; once it is public, it is a MAJOR one.
- **MINOR** (1.0.0 to 1.1.0): what the engine flags changes, but an app needs no code change. A new rule pack, a new codebook version, new or retired rules, or reworded explanations and reader questions.
- **PATCH** (1.0.0 to 1.0.1): a fix that changes no verdict. A crash, a feed the parser could not read, a typo in documentation.

Every entry names the **rule pack** and **codebook** it ships, and has a **For apps** note saying what an app has to do when it upgrades. Each release is also tagged in git as `vX.Y.Z`.

Work that is finished but not yet released sits under **Unreleased** at the top. Apps should only ever use a numbered release.

## Unreleased

Nothing yet.

## 1.2.0 (2026-11-21)

Rule pack 40, codebook 15. This adds a technique; the engine is not public yet, so it is a MINOR release (see How version numbers work).

New in rule pack 40: **Social scraping** (`social_scraping`) is a new technique. It covers a crowd of unknown size and its reaction reported as the news: "Fans are furious over…", "People are just realizing…", "The internet reacts to…", "Strictly viewers say same thing as…", "GTA 6 players are already ditching…". Its reader question: how many people is this, and where? Stories like this are often built from a few social media posts. A real count ("35 million people"), a named person making the claim, people in the story stating a fact ("Florida parents say their baby…"), a study or poll, and a bug report are left alone. The crowd phrases Unnamed sources used to flag ("people are saying", "shoppers say", "fans brand") move here. See [position 21](docs/positions.md#21-a-crowd-reported-as-the-news-is-its-own-technique).

**For apps.** Handle the eleventh technique, `Category.CROWD` (id `social_scraping`, family `speculation`), anywhere techniques are listed or switched on, including any `when` over `Category` without an `else`. Give it an icon, label and on/off setting if techniques have those, on by default. Headlines that showed Unnamed sources for "people are saying" or "shoppers say" now show Social scraping.

## 1.1.0 (2026-11-18)

Rule pack 39, codebook 14. This adds a technique; the engine is not public yet, so it is a MINOR release (see How version numbers work).

New in rule pack 39: **Loaded question** (`loaded_question`) is its own technique, split from Opinion as fact. It covers a question that carries a claim, verdict or worry the headline does not state outright: "Was X right to…?", "Has the next financial crisis begun?", "Is X really…?", "… Should he?", "Ransomware has a new target. Is your backup ready?". Its reader question: turn the question into a statement: would the outlet print that, and does the story back it up? The leading questions and closing nudges that Opinion as fact used to flag move here, and a few new shapes are caught. Like Opinion as fact, it never applies to signed opinion, reviews or letters. See [position 20](docs/positions.md#20-a-loaded-question-is-its-own-technique).

- The Opinion family's description now says "judgment" (American spelling) instead of "judgement".

**For apps.** Handle the tenth technique, `Category.QUESTION` (id `loaded_question`, family `editorializing`), anywhere techniques are listed or switched on, including any `when` over `Category` without an `else`. Give it an icon, label and on/off setting if techniques have those, on by default. Headlines that showed Opinion as fact for a question now show Loaded question.

## 1.0.0 (2026-11-17)

Rule pack 38, codebook 13. The first release.

The engine flags nine headline techniques, each with a plain explanation and a question for the reader: Alarmist framing, Conflict framing, Speculation, Unnamed sources, Study as proof, Withheld detail, Manufactured urgency, Early science and Opinion as fact. It runs on rules alone, on the device, with no network calls. It ships as four modules: `model`, `flagging`, `feeds`, and `engine`, which brings the other three.

New in rule pack 38: **Study as proof** (`study_as_proof`) is its own technique, split from Unnamed sources. It covers headlines that offer an unnamed study, survey, poll or data set as the proof of their point ("…, study finds", "Poll: …"). The same headlines are flagged as before; only the technique on them changes. See [position 19](docs/positions.md#19-a-study-offered-as-proof-is-its-own-technique).

**For apps.** Builds made before this release also said 1.0.0, because the number was never set; they are not this release. An app moving from one of those builds should:

- handle the ninth technique, `Category.STUDY` (id `study_as_proof`, family `speculation_sourcing`), anywhere it lists or switches on techniques, including any `when` over `Category` without an `else`;
- give it an icon, label and on/off setting if techniques have those, on by default;
- expect different flags if its old build carried an earlier rule pack. Since rule pack 35 a flag needs a construction (how the words work together), not just a listed word, and names one technique unless two are each strong enough to flag the headline alone.
