package com.medialiteracy.feeds

import com.medialiteracy.model.FeedItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the grouping over every real headline in `fixtures/headlines/`.
 *
 * The unit tests next door prove single rules in isolation. This one is the
 * check that matters: the rules loosened or tightened by hand always look fine
 * on four hand-picked headlines and can still be wrong across a day of ten
 * feeds. Same reasoning as running the rule scorer before and after a detection
 * change.
 *
 * The fixture has no publication dates, so the time window is not exercised
 * here. That rule only gets proven against live feeds.
 */
class StoryGrouperFixtureTest {

    private fun fixture(): List<Pair<String, String>> {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "fixtures/headlines/harvested.tsv").exists()) {
            dir = dir.parentFile
        }
        val file = dir?.let { File(it, "fixtures/headlines/harvested.tsv") }
        assertTrue(file != null && file.exists(), "could not find fixtures/headlines/harvested.tsv")

        return file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val columns = line.split('\t')
                if (columns.size < 3) return@mapNotNull null
                val source = columns[2].removePrefix("#").trim()
                val headline = columns[1].trim()
                if (source.isEmpty() || headline.isEmpty()) null else source to headline
            }
    }

    private fun groups(): List<StoryGrouper.StoryGroup> {
        val bySource = fixture()
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, titles) ->
                titles.distinct().map {
                    FeedItem(title = it, link = null, publishedAt = null, summary = null, id = null)
                }
            }
        return StoryGrouper.group(bySource)
    }

    private fun StoryGrouper.StoryGroup.titles() = items.map { it.title }

    private fun List<StoryGrouper.StoryGroup>.holding(fragment: String) =
        singleOrNull { group -> group.titles().any { it.contains(fragment, ignoreCase = true) } }

    @Test
    fun `finds the stories several outlets covered`() {
        val found = groups()

        // A day of ten feeds. If this collapses to a handful the rules have
        // been tightened past usefulness; if it triples, something is gluing
        // unrelated stories together.
        assertTrue(
            found.size in 8..300,
            "expected roughly a dozen multi-outlet stories per day, got ${found.size}:\n" +
                found.joinToString("\n\n") { it.titles().joinToString("\n  ") },
        )

        val eu = found.holding("Canada to become")
        assertTrue(eu != null && eu.sourceCount >= 3, "the EU/Canada story should span several outlets: $eu")

        val putney = found.holding("Putney pusher")
        assertTrue(putney != null && putney.sourceCount >= 3, "the Putney story should span several outlets: $putney")
    }

    @Test
    fun `does not glue unrelated stories together`() {
        val found = groups()

        val missingPerson = found.holding("Noah Woods")
        assertTrue(
            missingPerson == null || missingPerson.titles().none { it.contains("Nottingham") },
            "two different missing-person stories were merged: ${missingPerson?.titles()}",
        )

        val var_ = found.holding("derby VAR mistake")
        assertTrue(
            var_ == null || var_.titles().none { it.contains("Sterling") },
            "a VAR story was merged with a player profile: ${var_?.titles()}",
        )
    }

    @Test
    fun `every group really does span more than one outlet`() {
        groups().forEach { group ->
            assertTrue(
                group.sourceCount >= 2,
                "a single-outlet group escaped: ${group.titles()}",
            )
        }
    }

    @Test
    fun `every group has something to show as its subject`() {
        groups().forEach { group ->
            assertTrue(
                group.subjectWords.isNotEmpty(),
                "nothing to put in the header for: ${group.titles()}",
            )
        }
    }
}
