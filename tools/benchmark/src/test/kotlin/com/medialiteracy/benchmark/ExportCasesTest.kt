package com.medialiteracy.benchmark

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportCasesTest {

    private val root = File("../..")

    @Test
    fun `every row is exported once with the benchmark's own split`() {
        val ds = HEADLINE_DATASETS.first { it.id == "starter" }.let { it.copy(path = File(root, it.path).path) }
        val owner = File.createTempFile("owner", ".tsv").apply {
            writeText(
                "# header\nid\toutlet\theadline\tapp_verdict\tproposed_level\tproposed_techniques\tcall\tnote\towner_level\towner_techniques\n" +
                    "abc\tPaper\tYou won't believe this\tNONE\tSUBTLE\tcuriosity_gap\tmiss\t\tPROMINENT\tcuriosity_gap\n",
            )
            deleteOnExit()
        }
        val out = File.createTempFile("cases", ".jsonl").apply { deleteOnExit() }
        val n = ExportCases.run(out, owner, listOf(ds))
        val rows = out.readLines().map { Json.parseToJsonElement(it).jsonObject }
        val cases = ds.read(File(ds.path))
        assertEquals(cases.size + 1, n)
        assertEquals(n, rows.size)
        val byId = rows.associateBy { it["id"]!!.jsonPrimitive.content }
        for (c in cases) assertEquals(Split.of(c.id).name, byId.getValue(c.id)["split"]!!.jsonPrimitive.content)
        val ownerRow = byId.getValue("abc")
        assertEquals("OWNER", ownerRow["split"]!!.jsonPrimitive.content)
        assertEquals("PROMINENT", ownerRow["expectedLevel"]!!.jsonPrimitive.content, "the maintainer's label wins over the proposed one")
        assertTrue(rows.all { "inspectHits" in it })
    }
}
