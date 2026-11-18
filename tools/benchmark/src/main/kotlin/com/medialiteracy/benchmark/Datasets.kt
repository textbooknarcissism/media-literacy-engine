package com.medialiteracy.benchmark

import java.io.File
import java.time.LocalDate

enum class HeadlineFormat { LEGACY, BROADSHEET, BEATS, CONTEXT, OWNER }

data class HeadlineDataset(
    val id: String,
    val path: String,
    val format: HeadlineFormat,
    val year: Int,
    val licence: String,
    val source: String,
    /** Labeled after the rules were frozen; its held-out part is the honest test. */
    val fresh: Boolean = false,
    /** Older than 2021: reported as a regression check, never as evidence, and not gated. */
    val legacyEvidence: Boolean = false,
    /**
     * Rule material, never evidence: every row was read while writing rules, so
     * the whole set counts as tuning. It has no held-out part and is not gated.
     */
    val tuningOnly: Boolean = false,
    /**
     * A sealed test set: labeled, committed, and then left unread until this
     * day. Before it, the set is not scored, listed, exported or gated, so no
     * rule pack can be chosen by its result. From this day on it is scored as
     * held-out totals only, the whole set; its first score is the honest one
     * (docs/benchmark/review-process.md, "Sealed sets").
     */
    val sealedUntil: LocalDate? = null,
) {
    init {
        require(sealedUntil == null || (fresh && !tuningOnly && !legacyEvidence)) {
            "$id: a sealed set is fresh evidence by definition"
        }
    }

    fun isSealed(today: LocalDate = LocalDate.now()): Boolean = sealedUntil != null && today < sealedUntil

    /** Which part of the benchmark a case belongs to: always tuning for a [tuningOnly] set, always held-out for a sealed one. */
    fun partOf(caseId: String): SplitPart = when {
        tuningOnly -> SplitPart.TUNE
        sealedUntil != null -> SplitPart.HELDOUT
        else -> Split.of(caseId)
    }

    fun read(file: File): List<HeadlineCase> = when (format) {
        HeadlineFormat.LEGACY -> HeadlineFiles.readLegacy(file)
        HeadlineFormat.BROADSHEET -> HeadlineFiles.readBroadsheet(file)
        HeadlineFormat.BEATS -> HeadlineFiles.readBeats(file)
        HeadlineFormat.CONTEXT -> HeadlineFiles.readContext(file)
        HeadlineFormat.OWNER -> HeadlineFiles.readOwner(file)
    }
}

/** Headline datasets committed to the repo. A missing file is reported as skipped. */
val HEADLINE_DATASETS = listOf(
    HeadlineDataset(
        "broadsheet-2026", "fixtures/headlines/broadsheet-2026.tsv", HeadlineFormat.BROADSHEET, 2026, "project",
        "Model-labeled broadsheet and tabloid headlines, per docs/benchmark/codebook.md", fresh = true,
    ),
    HeadlineDataset(
        "beats-2026", "fixtures/headlines/beats-2026.tsv", HeadlineFormat.BEATS, 2026, "project",
        "Beat-stratified headlines (gaming, science, politics, entertainment, local, opinion and long-form, business), labeled blind by a single model reviewer per docs/benchmark/codebook.md version 3; maintainer spot-check pending. " +
            "The headlines come from the live intake, which was in the repo when rule pack 1 was written, so the rules may have seen them and the held-out numbers may be optimistic",
    ),
    HeadlineDataset(
        "context-2026", "fixtures/headlines/context-2026.tsv", HeadlineFormat.CONTEXT, 2026, "project",
        "Context-labeled headlines with summaries, page metadata and cached article pages, labeled by a single model reviewer under docs/benchmark/codebook.md version 4, plus version 5 (rule 5); maintainer spot-check in progress. " +
            "Two engine signals were written from three held-out rows (docs/positions.md section 13), so three held-out catches are not independent", fresh = true,
    ),
    HeadlineDataset(
        "week-2026-10-19", "fixtures/headlines/week-2026-10-19.tsv", HeadlineFormat.CONTEXT, 2026, "project",
        "Headlines published 2026-10-12 to 2026-10-19, after rule pack 27, labeled under codebook version 6: a language model's blind first pass, with a second pass adjudicating the unsure rows and every row where the first pass and the rules disagreed; maintainer spot-check pending. " +
            "Unseen by the rules and by the bake-off models (docs/benchmark/model-bakeoff-2026-10.md)", fresh = true,
    ),
    HeadlineDataset(
        "harvested", "fixtures/headlines/harvested.tsv", HeadlineFormat.LEGACY, 2026, "project",
        "Harvested newsroom corpus (the rules were tuned on all of it before this benchmark existed)",
    ),
    HeadlineDataset(
        "starter", "fixtures/headlines/starter.tsv", HeadlineFormat.LEGACY, 2026, "project",
        "Hand-written starter cases",
    ),
    HeadlineDataset(
        "owner-reported", "fixtures/headlines/owner-reported-2026-10.tsv", HeadlineFormat.OWNER, 2026, "project",
        "Headlines the maintainer saw in the reader app and reported as missed or wrongly flagged (2026-10-19); the maintainer's labels where given, otherwise proposed ones. Rule material, never evidence", tuningOnly = true,
    ),
    HeadlineDataset(
        "mined-2026-10", "fixtures/headlines/mined-2026-10.tsv", HeadlineFormat.CONTEXT, 2026, "project",
        "1,598 unlabeled intake headlines labeled by a language model under codebook v7 with trigger words, spot-checked; read in full while writing rules. Rule material, never evidence", tuningOnly = true,
    ),
    HeadlineDataset(
        "savedyouaclick-2026", "fixtures/headlines/savedyouaclick-2026.tsv", HeadlineFormat.CONTEXT, 2026, "project",
        "1,165 headlines readers spoiled on r/savedyouaclick (2025-01 to 2026-10), labeled under codebook v7. Clickbait only, so it measures catching, never false flags. Rule material, never evidence", tuningOnly = true,
    ),
    HeadlineDataset(
        "chakraborty-legacy", "fixtures/headlines/external-clickbait-chakraborty.tsv", HeadlineFormat.LEGACY, 2016,
        "see file header", "Chakraborty et al., Stop Clickbait, ASONAM 2016", legacyEvidence = true,
    ),
)
