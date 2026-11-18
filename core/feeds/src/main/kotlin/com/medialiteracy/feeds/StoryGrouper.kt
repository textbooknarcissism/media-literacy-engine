package com.medialiteracy.feeds

import com.medialiteracy.model.FeedItem
import java.time.Duration
import java.time.Instant

/**
 * Finds the articles in a feed list that are all covering the same story.
 *
 * The point is the comparison screen: four versions of one event, side by side,
 * each showing whatever technique it used. Seeing that the facts are identical
 * and only the wording moved teaches more than any single flag does, because
 * the reader works it out themselves instead of being told.
 *
 * Nothing here ranks, scores or reorders. A group is a group; what the app does
 * with it is show it in the order the feeds arrived. See decision 9.
 *
 * Being too eager is the expensive mistake, not being too shy. A missed group
 * costs nothing: the reader never knew it was possible. A wrong one puts two
 * unrelated stories side by side and invites the reader to compare framings of
 * events that were never the same, which makes the app look like it isn't
 * reading the news at all. Same trade-off as the flagging rules.
 */
object StoryGrouper {

    /** How far apart two articles can be published and still be one story. */
    val DEFAULT_WINDOW: Duration = Duration.ofHours(48)

    /**
     * How much of the shorter headline has to be shared before two headlines
     * are considered the same story.
     */
    const val DEFAULT_OVERLAP = 0.40

    /**
     * How many words two headlines must share outright, whatever the
     * proportion.
     *
     * Short headlines are the problem this solves. "Inside catastrophic derby
     * VAR mistake" has only four words worth counting, so sharing any two of
     * them clears any sensible percentage, which is how a report of a
     * refereeing error got attached to a profile of Raheem Sterling on the
     * words "inside" and "mistake".
     */
    const val MINIMUM_SHARED_WORDS = 3

    /**
     * One story as covered by more than one source.
     *
     * [items] stays in the order it was given. That is deliberate and is the
     * whole of decision 9: sorting by flag would quietly turn into a ranking of
     * outlets, and it would also pull the plainly-worded headline away from the
     * manipulative one, which is the pairing that does the teaching.
     */
    data class StoryGroup(
        val items: List<FeedItem>,
        /** The shared distinctive words, most widely shared first. */
        val subjectWords: List<String>,
        /** How many different sources are in here, which is what the app counts. */
        val sourceCount: Int,
    )

    /**
     * @param itemsBySource every article currently in the feed, keyed by the
     *   source it came from. The key only has to be stable, not pretty.
     * @return one entry per story covered by at least two different sources,
     *   in the order the stories first appear in [itemsBySource].
     */
    fun group(
        itemsBySource: Map<String, List<FeedItem>>,
        window: Duration = DEFAULT_WINDOW,
        minimumOverlap: Double = DEFAULT_OVERLAP,
    ): List<StoryGroup> {
        val entries = itemsBySource.flatMap { (source, items) ->
            items
                // Feeds repeat themselves. The BBC served the same Hyrox
                // headline twice in one fetch, and the same headline printed
                // twice inside one group just looks broken.
                .distinctBy { it.title.trim().lowercase() }
                .map { Entry(it, source, contentWords(it.title), nameWords(it.title)) }
        }.filter { it.words.size >= MINIMUM_SHARED_WORDS }

        if (entries.size < 2) return emptyList()

        val union = DisjointSet(entries.size)

        // Only headlines that share a name word can ever be the same story:
        // that is the last and strictest test in [sameStory], and a name is
        // always one of the headline's own words. So rather than testing all
        // 376,000 pairs that 868 headlines make (which measured at 650ms on
        // the phone, on top of an already slow load), each name is indexed to
        // the headlines it appears in, and only headlines found under a shared
        // name are compared at all.
        //
        // This throws away no group. It skips only the pairs [sameStory] was
        // always going to reject on its final line.
        val headlinesByName = HashMap<String, MutableList<Int>>()
        entries.forEachIndexed { index, entry ->
            entry.names.forEach { name ->
                headlinesByName.getOrPut(name) { mutableListOf() }.add(index)
            }
        }

        val alreadyCompared = HashSet<Long>()
        entries.forEachIndexed { index, entry ->
            entry.words.forEach { word ->
                headlinesByName[word]?.forEach { other ->
                    if (other == index) return@forEach
                    val a = minOf(index, other)
                    val b = maxOf(index, other)
                    // One pair, one comparison: a pair can surface twice here,
                    // once under each headline's own words.
                    if (!alreadyCompared.add(a.toLong() * entries.size + b)) return@forEach
                    if (sameStory(entries[a], entries[b], window, minimumOverlap)) {
                        union.join(a, b)
                    }
                }
            }
        }

        return entries.indices
            .groupBy { union.rootOf(it) }
            .values
            .mapNotNull { indices -> toGroup(indices.map { entries[it] }) }
    }

    private fun toGroup(members: List<Entry>): StoryGroup? {
        val sources = members.map { it.source }.distinct()
        // One outlet running three pieces on the same story is still one
        // outlet. The header line says "4 outlets covered this", so counting
        // articles there would simply be untrue.
        if (sources.size < 2) return null

        val names = members.flatMap { it.names }.toHashSet()
        val shared = members
            .flatMap { it.words.distinct() }
            .groupingBy { it }
            .eachCount()
            .filter { (_, count) -> count > 1 }
            .entries
            // Names first: they are what the story is actually about, and the
            // screen shows these as its subject line.
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.key in names }
                    .thenByDescending { it.value }
                    .thenBy { it.key },
            )
            .map { it.key }

        return StoryGroup(
            items = members.map { it.item },
            subjectWords = shared,
            sourceCount = sources.size,
        )
    }

    private fun sameStory(a: Entry, b: Entry, window: Duration, minimumOverlap: Double): Boolean {
        if (!withinWindow(a.item.publishedAt, b.item.publishedAt, window)) return false

        val other = b.words.toHashSet()
        val shared = a.words.filter { it in other }
        if (shared.size < MINIMUM_SHARED_WORDS) return false

        val proportion = shared.size.toDouble() / minOf(a.words.size, b.words.size)
        if (proportion < minimumOverlap) return false

        // The rule that stops ordinary phrasing from gluing two stories
        // together. Two reports of one event share a name; two unrelated
        // reports share only the everyday words that any news story uses.
        //
        // "Body found in search for missing Noah Woods" and "Body found in
        // search for missing Nottingham woman" share four words and not one
        // name: a different child, a different city, and a comparison screen
        // that would have looked broken.
        return shared.any { it in a.names || it in b.names }
    }

    /**
     * Two articles published far apart are not the same story, however much
     * wording they share. A missing date is treated as "might be": dropping an
     * article because its feed left the date out would lose real groups, and a
     * wrong date is the feed's mistake to make, not ours to punish.
     */
    private fun withinWindow(a: Instant?, b: Instant?, window: Duration): Boolean {
        if (a == null || b == null) return true
        return Duration.between(a, b).abs() <= window
    }

    private fun contentWords(text: String): List<String> = tokenise(text)
        .map { it.lowercase() }
        .filter { it.length >= 4 && it !in STOP_WORDS }
        .distinct()

    /**
     * The words in a headline that look like names: capitalized, and not
     * merely because they started the sentence.
     *
     * This replaced an earlier rule that called a word distinctive when it was
     * rare across the feed. That rule quietly depended on how much news had
     * been loaded: with a handful of articles every word looks rare, so the
     * app would have been at its most reckless for the reader with the fewest
     * feeds, which is the wrong way round. Capitalization says the same thing
     * about a word without asking how big the feed is.
     *
     * A headline in Title Case has every word capitalized and so proves
     * nothing; those contribute no names at all rather than contributing
     * false ones. The cost is a missed group for outlets that headline that
     * way, which is the cheap failure.
     *
     * The first word counts too. Dropping it (on the grounds that a capital
     * at the start of a sentence means nothing) threw away "Kosovo", "Putney"
     * and "Brussels", which are the whole subject of their headlines.
     * [COMMON_NEWS_WORDS] does that job instead, and more precisely.
     */
    private fun nameWords(text: String): Set<String> {
        val tokens = tokenise(text)
        val longEnough = tokens.filter { it.length >= 4 }
        if (longEnough.isEmpty()) return emptySet()

        val capitalised = longEnough.count { it.first().isUpperCase() }
        if (capitalised > longEnough.size * TITLE_CASE_SHARE) {
            // In Title Case headlines, every major word starts with a capital.
            // Rather than returning emptySet() and blinding the clustering engine to Title Case headlines,
            // Filter out closed-class grammatical words, common news verbs, adjectives, and general vocabulary
            // to isolate proper nouns, company names, places, and distinctive subjects.
            return longEnough
                .map { it.lowercase() }
                .filterNot { it in STOP_WORDS || it in COMMON_NEWS_WORDS || it in TITLE_CASE_COMMON_WORDS }
                .toHashSet()
        }

        return longEnough
            .filter { it.first().isUpperCase() }
            .map { it.lowercase() }
            .filterNot { it in STOP_WORDS || it in COMMON_NEWS_WORDS }
            .toHashSet()
    }

    /**
     * Everyday news vocabulary that can start a headline with a capital letter
     * without being the name of anything.
     *
     * This list only stops a word counting as a *name*. It still counts
     * toward how much two headlines overlap, because "body found in search
     * for missing" genuinely is shared wording: it just isn't evidence of a
     * shared story.
     *
     * "Body found in search for missing Noah Woods" and "Body found in search
     * for missing Nottingham woman" are why it exists. Keep it short: every
     * word added here is a word that can no longer prove two reports are about
     * the same event, and names are the only evidence this code has.
     */
    private val COMMON_NEWS_WORDS = setOf(
        "body", "found", "search", "missing", "police", "dead", "death",
        "dies", "died", "killed", "jailed", "court", "case", "woman", "women",
        "child", "children", "people", "family", "video", "watch",
        "revealed", "breaking", "update", "live", "exclusive",
    )

    /**
     * Common English vocabulary (verbs, adjectives, common nouns, determiners)
     * capitalized in Chicago/AP Title Case headlines that do not constitute
     * proper nouns or named entities.
     */
    private val TITLE_CASE_COMMON_WORDS = setOf(
        // Common verbs & reporting actions
        "says", "said", "warns", "warned", "urges", "urged", "tells", "told",
        "calls", "called", "plans", "planned", "moves", "moved", "seeks", "sought",
        "finds", "found", "faces", "faced", "takes", "took", "makes", "made",
        "gives", "gave", "gets", "got", "shows", "showed", "holds", "held",
        "falls", "fell", "rises", "rose", "drops", "dropped", "beats", "cuts",
        "halts", "leads", "leaves", "left", "looks", "loses", "lost", "meets",
        "opens", "sets", "shuts", "starts", "stops", "turns", "votes", "wins",
        "fights", "strikes", "claims", "denies", "backs", "blasts", "slams",
        "fires", "hits", "comes", "came", "goes", "went", "knows", "knew",
        "sees", "seen", "thinks", "thought", "wants", "wanted", "feels", "felt",
        "seems", "seemed", "tries", "tried", "helps", "helped", "talks", "talked",
        "runs", "ran", "likes", "liked", "lives", "lived", "brings", "brought",
        "happens", "happened", "writes", "wrote", "stands", "stood", "pays", "paid",
        "learns", "changes", "speaks", "spoke", "reads", "adds", "added", "spends",
        "spent", "grows", "grew", "walks", "offers", "appears", "buys", "bought",
        "serves", "sends", "sent", "expects", "builds", "built", "stays", "reaches",
        "kills", "killed", "remains", "suggests", "raises", "passes", "sells", "sold",
        "reports", "decides", "pulls", "breaks", "broke", "pushes", "fears",
        // Common adjectives & adverbs
        "first", "last", "next", "best", "worst", "high", "higher", "highest",
        "large", "larger", "small", "smaller", "great", "good", "major", "minor",
        "former", "annual", "daily", "weekly", "monthly", "yearly", "global", "local",
        "national", "public", "private", "real", "true", "hard", "easy", "fast",
        "slow", "clear", "close", "deep", "free", "fresh", "long", "short", "open",
        "wide", "fine", "fair", "dark", "dead", "safe", "sure", "heavy", "ready",
        "right", "wrong", "final", "total", "huge", "historic", "massive", "severe",
        "sudden", "simple", "latest", "recent", "critical", "vital", "urgent",
        "ahead", "back", "away", "never", "ever", "always", "often", "still", "also",
        "just", "only", "even", "almost", "very", "quite", "really", "soon",
        // Common nouns & topics
        "news", "report", "reports", "story", "stories", "time", "times", "year",
        "years", "month", "months", "week", "weeks", "days", "hours", "deal",
        "deals", "plan", "plans", "bill", "bills", "laws", "rules", "rates",
        "prices", "costs", "jobs", "work", "workers", "home", "homes", "house",
        "houses", "school", "schools", "city", "cities", "state", "states", "country",
        "countries", "world", "life", "lives", "death", "deaths", "head", "heads",
        "side", "sides", "part", "parts", "line", "lines", "case", "cases", "point",
        "points", "group", "groups", "team", "teams", "power", "money", "order",
        "crisis", "crash", "fire", "storm", "flood", "record", "future", "history",
        "market", "markets", "growth", "impact", "system", "systems", "number",
        "numbers", "issue", "issues", "member", "members", "party", "parties",
        "service", "services", "leader", "leaders", "office", "action", "actions",
        "change", "changes", "result", "results", "reason", "reasons", "moment",
        "moments", "question", "questions", "answer", "answers", "truth", "facts",
        "guide", "photo", "photos", "image", "images", "video", "videos",
        // Common determiners, pronouns & prepositions
        "what", "when", "where", "which", "while", "whose", "more", "most",
        "many", "much", "some", "such", "both", "each", "every", "other", "another",
        "either", "neither", "here", "there", "this", "that", "these", "those",
        "thing", "things", "something", "anything", "nothing", "everything", "someone",
        "anyone", "everyone", "with", "without", "within", "between", "among", "under",
        "above", "over", "around", "through", "across", "behind", "beyond", "against",
        "before", "after", "during", "since", "until",
    )

    /** Above this share of capitalized words, a headline is just Title Case. */
    private const val TITLE_CASE_SHARE = 0.7

    private fun tokenise(text: String): List<String> = text
        // Curly quotes and apostrophes differ between outlets writing the same
        // name, so they are flattened before splitting.
        .replace('‘', '\'').replace('’', '\'')
        .replace('“', '"').replace('”', '"')
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }

    private class Entry(
        val item: FeedItem,
        val source: String,
        val words: List<String>,
        val names: Set<String>,
    )

    /**
     * Groups things that have been linked together, without needing to know in
     * advance how many groups there will be. A links to B and B links to C, so
     * all three end up in one story.
     */
    private class DisjointSet(size: Int) {
        private val parent = IntArray(size) { it }

        fun rootOf(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var walk = x
            while (parent[walk] != root) {
                val next = parent[walk]
                parent[walk] = root
                walk = next
            }
            return root
        }

        fun join(a: Int, b: Int) {
            val rootA = rootOf(a)
            val rootB = rootOf(b)
            if (rootA != rootB) parent[rootA] = rootB
        }
    }

    /**
     * Words long enough to survive the length filter but carrying no subject
     * information, so they would inflate the overlap between any two headlines.
     *
     * Deliberately the same list [ArticleRelevance] uses: the two are doing
     * the same job on different pairs of text, and letting them drift apart
     * would mean two different ideas of what a headline is about.
     */
    private val STOP_WORDS = ArticleRelevance.STOP_WORDS
}
