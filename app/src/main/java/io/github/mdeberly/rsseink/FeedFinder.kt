package io.github.mdeberly.rsseink

import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URL

/** Finds the RSS/Atom feed of a site from any address typed or shared by the user. */
object FeedFinder {

    class NotFound : IOException("No feed found")

    fun find(input: String): Pair<String, FeedParser.Parsed> {
        var address = input.trim()
        if (!address.contains("://")) address = "https://$address"

        val first = Http.get(address)
        if (FeedParser.looksLikeFeed(first.body)) {
            return first.url to FeedParser.parse(first.body, first.url)
        }

        val candidates = LinkedHashSet<String>()
        val doc = Jsoup.parse(ByteArrayInputStream(first.body), Http.charset(first.contentType), first.url)
        doc.select("link[rel~=(?i)alternate][type~=(?i)(rss|atom)]").forEach { candidates += it.absUrl("href") }
        doc.select("a[href~=(?i)(rss|feed|atom)]").take(6).forEach { candidates += it.absUrl("href") }
        val origin = URL(first.url).let { "${it.protocol}://${it.host}" }
        listOf("/feed", "/rss", "/rss.xml", "/feed.xml", "/atom.xml", "/index.xml", "/feed/", "/rss/").forEach {
            candidates += origin + it
        }

        for (c in candidates) {
            if (c.isBlank()) continue
            val r = runCatching { Http.get(c) }.getOrNull() ?: continue
            if (FeedParser.looksLikeFeed(r.body)) {
                val parsed = runCatching { FeedParser.parse(r.body, r.url) }.getOrNull() ?: continue
                return r.url to parsed
            }
        }
        throw NotFound()
    }
}
