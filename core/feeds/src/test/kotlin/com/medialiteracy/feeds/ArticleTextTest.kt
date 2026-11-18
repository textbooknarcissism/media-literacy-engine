package com.medialiteracy.feeds

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleTextTest {

    @Test
    fun `pulls prose out of a normal article page`() {
        val html = """
            <html><head><title>Ignore me</title>
            <script>var tracking = 'ignore me too';</script>
            <style>.ignore { color: red }</style></head>
            <body>
              <nav><p>Home News Sport Business</p></nav>
              <article>
                <p>Researchers at the university reported that the treatment slowed
                   tumour growth in mice, according to a paper published on Tuesday.</p>
                <p>The work is at an early stage and has not yet been tested in
                   humans, the authors cautioned in the paper.</p>
              </article>
              <footer><p>Copyright notice and a pile of links</p></footer>
            </body></html>
        """.trimIndent()

        val text = ArticleText.extract(html)

        assertContains(text, "slowed tumour growth in mice")
        // The caveat is the whole reason we fetch the body at all.
        assertContains(text, "early stage")
        assertFalse(text.contains("tracking"), "script contents must not survive")
        assertFalse(text.contains("color: red"), "style contents must not survive")
    }

    @Test
    fun `drops navigation and boilerplate fragments`() {
        val html = """
            <body>
              <p>Home</p>
              <p>Sign up for our newsletter</p>
              <p>This is a genuine paragraph of reporting that runs on long enough to
                 be recognisable as actual prose rather than a navigation link.</p>
            </body>
        """.trimIndent()

        val text = ArticleText.extract(html)

        assertContains(text, "genuine paragraph of reporting")
        assertFalse(text.trim().startsWith("Home"), "short nav fragments should be dropped")
    }

    @Test
    fun `decodes the entities that show up in body copy`() {
        val html = """
            <body><p>The company&rsquo;s results &amp; outlook were described as
            &quot;disappointing&quot; by analysts who follow the sector closely.</p></body>
        """.trimIndent()

        val text = ArticleText.extract(html)

        assertContains(text, "company’s")
        assertContains(text, "&")
        assertFalse(text.contains("&amp;"), "entities should be decoded, not left raw")
    }

    @Test
    fun `respects the character cap`() {
        val paragraph = "<p>${"word ".repeat(400)}</p>"
        val text = ArticleText.extract("<body>$paragraph</body>", maxChars = 500)
        assertTrue(text.length <= 500, "was ${text.length}")
    }

    @Test
    fun `survives pages with no paragraph tags at all`() {
        val html = "<body><div>Some sites still lay out their copy in bare divs " +
            "without a single paragraph tag anywhere on the page.</div></body>"
        val text = ArticleText.extract(html)
        assertContains(text, "bare divs")
    }

    @Test
    fun `empty input does not blow up`() {
        assertTrue(ArticleText.extract("").isEmpty())
    }
}
