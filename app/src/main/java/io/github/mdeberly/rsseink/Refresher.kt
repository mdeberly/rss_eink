package io.github.mdeberly.rsseink

import org.jsoup.Jsoup
import java.text.Normalizer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Refresher {

    class Result(val failed: List<String>)

    fun refreshAll(): Result {
        val feeds = Store.feeds()
        if (feeds.isEmpty()) {
            Store.setArticles(emptyList(), System.currentTimeMillis())
            return Result(emptyList())
        }
        val pool = Executors.newFixedThreadPool(6)
        val futures = feeds.map { f -> pool.submit(Callable { runCatching { fetch(f) } }) }
        val fresh = ArrayList<Article>()
        val failed = ArrayList<Feed>()
        futures.forEachIndexed { i, future ->
            future.get().onSuccess { fresh += it }.onFailure { failed += feeds[i] }
        }
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.SECONDS)

        if (failed.size == feeds.size) {
            Store.lastRefreshFailed = true
            return Result(failed.map { it.title })
        }
        Store.lastRefreshFailed = false

        // Keep what we had for sources that failed this time
        val failedUrls = failed.map { it.url }.toSet()
        val all = fresh + Store.articles().filter { it.feedUrl in failedUrls }

        val now = System.currentTimeMillis()
        val cutoff = now - Prefs.maxAgeDays * 24L * 3600 * 1000
        val seenLinks = HashSet<String>()
        val seenTitles = HashSet<String>()
        val merged = all
            .sortedByDescending { it.date }
            .filter { it.date >= cutoff || it.date == 0L }
            // Same story in two feeds of the same site: keep it once
            .filter { seenLinks.add(normalizeLink(it.link)) && (it.title.length < 20 || seenTitles.add(normalizeTitle(it.title))) }
            .take(500)

        Store.setArticles(merged, now)
        for (a in merged) {
            if (Store.estimate(a.link) == null && a.feedWords >= FULL_FEED_WORDS) {
                Store.putEstimate(a.link, Estimate(a.feedWords, a.feedImages, true))
            }
        }
        return Result(failed.map { it.title })
    }

    private fun fetch(feed: Feed): List<Article> {
        val r = Http.get(feed.url)
        val parsed = FeedParser.parse(r.body, r.url)
        val now = System.currentTimeMillis()
        return parsed.items.mapIndexed { i, item ->
            val count = ReadingTime.analyze(item.content)
            Article(
                link = item.link,
                title = item.title.ifBlank { Jsoup.parse(item.content).text().take(90) },
                feedTitle = feed.title,
                feedUrl = feed.url,
                date = item.date ?: (now - i * 60_000L),
                content = item.content,
                feedWords = count.words,
                feedImages = count.images,
            )
        }
    }

    private fun normalizeLink(link: String) =
        link.substringBefore('#').substringBefore("?utm_").removeSuffix("/").replaceFirst("http://", "https://")

    private fun normalizeTitle(title: String) =
        Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }
}
