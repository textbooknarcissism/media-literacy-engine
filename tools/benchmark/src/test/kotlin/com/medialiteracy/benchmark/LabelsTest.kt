package com.medialiteracy.benchmark

import com.medialiteracy.model.Category
import com.medialiteracy.model.FlagLevel
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LabelsTest {
    private fun temp(text: String) = File.createTempFile("labels", ".tsv").apply { deleteOnExit(); writeText(text) }

    @Test
    fun `parses valid label lines`() {
        val labels = Labels.parse(temp("a\tNONE\t-\nb\tSUBTLE\topinion_as_fact,manufactured_fear\n"))
        assertEquals(Label("b", FlagLevel.SUBTLE, setOf(Category.OPINION, Category.FEAR)), labels["b"])
    }

    @Test
    fun `refuses malformed, inconsistent and duplicated lines, naming the line`() {
        val bad = assertFailsWith<IllegalArgumentException> {
            Labels.parse(temp("a\tNONE\topinion_as_fact\nb\tSUBTLE\t-\nc\tLOUD\t-\nd\tNONE\t-\nd\tNONE\t-\n"))
        }
        val message = bad.message.orEmpty()
        assertTrue(":1:" in message && ":2:" in message && ":3:" in message && ":5:" in message, message)
    }

    @Test
    fun `merge reports agreement and Cohen's kappa`() {
        fun set(vararg levels: FlagLevel) = levels.mapIndexed { i, l ->
            "id$i" to Label("id$i", l, if (l == FlagLevel.NONE) emptySet() else setOf(Category.FEAR))
        }.toMap()
        val a = set(FlagLevel.NONE, FlagLevel.NONE, FlagLevel.SUBTLE, FlagLevel.PROMINENT)
        val b = set(FlagLevel.NONE, FlagLevel.SUBTLE, FlagLevel.SUBTLE, FlagLevel.PROMINENT)
        val result = Labels.merge(a, b)
        assertEquals(3, result.agreed.size)
        assertEquals(1, result.disagreements.size)
        assertEquals(0.75, result.levelAgreement, 1e-9)
        assertEquals(0.6364, result.levelKappa, 0.001)
    }

    @Test
    fun `merge refuses label sets that cover different ids`() {
        val a = mapOf("x" to Label("x", FlagLevel.NONE, emptySet()))
        val b = mapOf("y" to Label("y", FlagLevel.NONE, emptySet()))
        assertFailsWith<IllegalArgumentException> { Labels.merge(a, b) }
    }

    @Test
    fun `batches skip labelled rows and split by chunk size`() {
        val intake = (1..5).map { IntakeRow("id$it", "BBC News", "news", "section-feed", "f", "l$it", "2026-09-27", "Headline $it", "") }
        val dir = createTempDirectory("batches").toFile().apply { deleteOnExit() }
        val files = Labels.writeBatches(intake, labelledIds = setOf("id1"), outDir = dir, chunkSize = 3)
        assertEquals(2, files.size)
        assertEquals(3, files[0].readLines().count { !it.startsWith("#") })
        assertEquals(1, files[1].readLines().count { !it.startsWith("#") })
    }
}
