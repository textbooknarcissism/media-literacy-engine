package com.medialiteracy.benchmark

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import java.io.File
import java.time.LocalDate
import java.util.Locale

data class Label(val id: String, val level: FlagLevel, val techniques: Set<Category>)

data class MergeResult(
    val agreed: List<Label>,
    val disagreements: List<Pair<Label, Label>>,
    val levelAgreement: Double,
    val techniqueAgreement: Double,
    val levelKappa: Double,
)

/** Helpers for the blind model-labeling protocol in docs/benchmark/codebook.md. */
object Labels {

    /** `id<TAB>LEVEL<TAB>techniques` lines. Every problem is reported with file and line, and nothing is returned until all lines are valid. */
    fun parse(file: File): Map<String, Label> {
        val errors = mutableListOf<String>()
        val out = linkedMapOf<String, Label>()
        file.readLines(Charsets.UTF_8).forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val where = "${file.name}:${index + 1}:"
            val p = line.split('\t')
            val id = p.getOrNull(0)?.trim().orEmpty()
            val level = p.getOrNull(1)?.trim()?.let { runCatching { FlagLevel.valueOf(it) }.getOrNull() }
            val techniques = p.getOrNull(2)?.let { runCatching { HeadlineFiles.parseTechniques(it) }.getOrNull() }
            when {
                p.size < 3 || id.isEmpty() || level == null || techniques == null ->
                    errors += "$where expected id<TAB>LEVEL<TAB>techniques, got '$line'"
                level == FlagLevel.NONE && techniques.isNotEmpty() -> errors += "$where NONE must have techniques '-'"
                level != FlagLevel.NONE && techniques.isEmpty() -> errors += "$where $level needs at least one technique"
                id in out -> errors += "$where duplicate id $id"
                else -> out[id] = Label(id, level, techniques)
            }
        }
        require(errors.isEmpty()) { errors.joinToString("\n") }
        return out
    }

    /** All `*.tsv` label files in a labeler's output directory, as one map. */
    fun readDir(dir: File): Map<String, Label> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".tsv") }.orEmpty().sortedBy { it.name }
        require(files.isNotEmpty()) { "No .tsv label files in $dir" }
        val all = linkedMapOf<String, Label>()
        for (f in files) for ((id, label) in parse(f)) {
            require(id !in all) { "${f.name}: id $id also appears in another file of $dir" }
            all[id] = label
        }
        return all
    }

    /** Batch files for annotation: every intake row not yet labeled, in chunks. */
    fun writeBatches(intake: List<IntakeRow>, labelledIds: Set<String>, outDir: File, chunkSize: Int): List<File> {
        outDir.mkdirs()
        return intake.filter { it.id !in labelledIds }.chunked(chunkSize).mapIndexed { i, rows ->
            File(outDir, "batch-${i + 1}.tsv").apply {
                writeText(
                    "# id\toutlet\tarticleType\theadline\tsummary\n" +
                        rows.joinToString("") { r -> listOf(r.id, r.outlet, r.articleType, r.headline, r.summary).joinToString("\t") + "\n" },
                    Charsets.UTF_8,
                )
            }
        }
    }

    fun merge(a: Map<String, Label>, b: Map<String, Label>): MergeResult {
        val ids = a.keys intersect b.keys
        require(ids.isNotEmpty() && ids.size == a.size && ids.size == b.size) {
            "Labeller files cover different ids: ${(a.keys - ids).size} only in A, ${(b.keys - ids).size} only in B"
        }
        val agreed = mutableListOf<Label>()
        val disagreements = mutableListOf<Pair<Label, Label>>()
        var levelSame = 0
        var techniquesSame = 0
        for (id in ids.sorted()) {
            val x = a.getValue(id)
            val y = b.getValue(id)
            if (x.level == y.level) levelSame++
            if (x.techniques == y.techniques) techniquesSame++
            if (x.level == y.level && x.techniques == y.techniques) agreed += x else disagreements += x to y
        }
        val n = ids.size.toDouble()
        val observed = levelSame / n
        val expected = FlagLevel.entries.sumOf { lvl ->
            (a.values.count { it.level == lvl } / n) * (b.values.count { it.level == lvl } / n)
        }
        val kappa = if (expected >= 1.0) 1.0 else (observed - expected) / (1 - expected)
        return MergeResult(agreed, disagreements, observed, techniquesSame / n, kappa)
    }

    fun writeDisagreements(file: File, disagreements: List<Pair<Label, Label>>, intake: Map<String, IntakeRow>) {
        file.parentFile?.mkdirs()
        file.writeText(
            "# id\toutlet\tarticleType\theadline\tsummary\tlevelA\ttechniquesA\tlevelB\ttechniquesB\n" +
                disagreements.joinToString("") { (x, y) ->
                    val r = intake.getValue(x.id)
                    listOf(r.id, r.outlet, r.articleType, r.headline, r.summary,
                        x.level.name, HeadlineFiles.formatTechniques(x.techniques),
                        y.level.name, HeadlineFiles.formatTechniques(y.techniques)).joinToString("\t") + "\n"
                },
            Charsets.UTF_8,
        )
    }

    fun writeFrozen(out: File, labels: List<Label>, intake: Map<String, IntakeRow>, result: MergeResult, adjudicated: Int, codebookVersion: String) {
        val missing = labels.map { it.id }.filter { it !in intake }
        require(missing.isEmpty()) { "Labelled ids not in the intake: ${missing.take(5)}" }
        fun pct(v: Double) = String.format(Locale.ROOT, "%.1f%%", 100 * v)
        val header = buildString {
            appendLine("# broadsheet-2026: Independent double-annotated evaluation set.")
            appendLine("# Labelled by two independent annotators per docs/benchmark/codebook.md (codebook version $codebookVersion) on ${LocalDate.now()}; disagreements settled by adjudication.")
            appendLine("# No annotator or adjudicator saw the rules or the detector's output.")
            appendLine("# Items: ${labels.size}. Level agreement: ${pct(result.levelAgreement)}. Technique-set agreement: ${pct(result.techniqueAgreement)}. Cohen's kappa (level): ${String.format(Locale.ROOT, "%.3f", result.levelKappa)}. Adjudicated: $adjudicated.")
            appendLine("# Labels change only by relabelling the whole set under a new codebook version.")
            appendLine("# ${HeadlineFiles.BROADSHEET_COLUMNS.joinToString("\t")}")
        }
        val rows = labels.sortedBy { it.id }.joinToString("") { label ->
            val r = intake.getValue(label.id)
            listOf(label.level.name, HeadlineFiles.formatTechniques(label.techniques), r.id, r.outlet, r.articleType,
                r.typeSource, r.link, r.headline, r.summary).joinToString("\t") { tsvSafe(it) } + "\n"
        }
        out.parentFile?.mkdirs()
        out.writeText(header + rows, Charsets.UTF_8)
    }
}

/** `--key value` pairs; a flag with no value becomes "true". */
fun parseFlags(args: List<String>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        require(key.startsWith("--")) { "Expected an option, got '$key'" }
        val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") }
        out[key] = value ?: "true"
        i += if (value == null) 1 else 2
    }
    return out
}

private const val DEFAULT_INTAKE = "fixtures/headlines/broadsheet-intake.tsv"
private const val DEFAULT_LABELLED = "fixtures/headlines/broadsheet-2026.tsv"

fun labelBatch(args: List<String>): Int {
    val f = parseFlags(args)
    val intake = IntakeRow.readAll(File(f["--intake"] ?: DEFAULT_INTAKE))
    val labelled = File(f["--labelled"] ?: DEFAULT_LABELLED)
    val labelledIds = if (labelled.exists()) HeadlineFiles.readBroadsheet(labelled).map { it.id }.toSet() else emptySet()
    val files = Labels.writeBatches(intake, labelledIds, File(f.getValue("--out")), (f["--chunk"] ?: "150").toInt())
    println("Wrote ${files.size} batch file(s) to ${f.getValue("--out")}")
    return 0
}

fun labelMerge(args: List<String>): Int {
    val f = parseFlags(args)
    val result = Labels.merge(Labels.readDir(File(f.getValue("--a"))), Labels.readDir(File(f.getValue("--b"))))
    val adjudicated = f["--adjudicated"]?.let { Labels.parse(File(it)) }.orEmpty()
    val intake = IntakeRow.readAll(File(f["--intake"] ?: DEFAULT_INTAKE)).associateBy { it.id }
    println(String.format(Locale.ROOT, "Level agreement %.1f%%, technique agreement %.1f%%, kappa %.3f, %d disagreements",
        100 * result.levelAgreement, 100 * result.techniqueAgreement, result.levelKappa, result.disagreements.size))

    val unresolved = result.disagreements.filter { (x, _) -> x.id !in adjudicated }
    if (unresolved.isNotEmpty()) {
        val file = File(f.getValue("--disagreements"))
        Labels.writeDisagreements(file, unresolved, intake)
        println("${unresolved.size} disagreement(s) need adjudication; written to $file")
        return 3
    }
    val out = File(f["--out"] ?: DEFAULT_LABELLED)
    require(!out.exists() || f.containsKey("--replace")) {
        "$out exists and its labels are frozen. Pass --replace only when relabelling the whole set under a new codebook version."
    }
    val labels = result.agreed + result.disagreements.map { (x, _) -> adjudicated.getValue(x.id) }
    Labels.writeFrozen(out, labels, intake, result, adjudicated = result.disagreements.size, codebookVersion = f.getValue("--codebook-version"))
    println("Wrote ${labels.size} labels to $out")
    return 0
}
