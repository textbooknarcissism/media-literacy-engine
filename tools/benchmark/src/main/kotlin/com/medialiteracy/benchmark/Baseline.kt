package com.medialiteracy.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DatasetBaseline(
    val leftAloneTune: Rate,
    val leftAloneHeldout: Rate,
    val caughtHeldout: Rate,
)

/** The committed numbers CI compares every change against. Change it only in the PR whose trade-off it records. */
@Serializable
data class Baseline(
    val datasets: Map<String, DatasetBaseline>,
    /** Rule phrases already in the pack when this baseline was written; the memorization lint only errors on newer ones. */
    val knownTerms: List<String> = emptyList(),
    /** The same for signals: signal ids and "id: alternative" patterns. */
    val knownSignals: List<String> = emptyList(),
) {
    companion object {
        private val json = Json { prettyPrint = true }

        fun read(file: File): Baseline = json.decodeFromString(serializer(), file.readText(Charsets.UTF_8))

        fun readOrNull(file: File): Baseline? = if (file.exists()) read(file) else null

        fun write(file: File, baseline: Baseline) {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(serializer(), baseline) + "\n", Charsets.UTF_8)
        }
    }
}

object Gate {
    const val MAX_RECALL_DROP = 0.01
    private const val EPSILON = 1e-9

    /** One message per regression; empty means the change may merge. */
    fun check(baseline: Baseline, current: Map<String, DatasetBaseline>): List<String> = buildList {
        for ((id, base) in baseline.datasets) {
            val now = current[id]
            if (now == null) {
                add("$id: in the baseline but not scored in this run")
                continue
            }
            if (now.leftAloneTune.value < base.leftAloneTune.value - EPSILON) {
                add("$id: left alone correctly (tuning) fell from ${base.leftAloneTune.format()} to ${now.leftAloneTune.format()}")
            }
            if (now.leftAloneHeldout.value < base.leftAloneHeldout.value - EPSILON) {
                add("$id: left alone correctly (held-out) fell from ${base.leftAloneHeldout.format()} to ${now.leftAloneHeldout.format()}")
            }
            if (now.caughtHeldout.value < base.caughtHeldout.value - MAX_RECALL_DROP - EPSILON) {
                add("$id: caught when it should (held-out) fell from ${base.caughtHeldout.format()} to ${now.caughtHeldout.format()}, more than 1 point")
            }
        }
    }
}
