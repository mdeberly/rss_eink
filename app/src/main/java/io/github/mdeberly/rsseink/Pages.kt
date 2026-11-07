package io.github.mdeberly.rsseink

import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

/** Full article pages, downloaded and cleaned once, then kept in the cache. */
object Pages {

    fun cached(link: String): String? =
        runCatching { Store.pageFile(link).takeIf { it.exists() }?.readText() }.getOrNull()

    /** Downloads and extracts the article. Throws IOException when offline; returns null if no text was found. */
    fun fetch(link: String): String? {
        cached(link)?.let { return it }
        val r = Http.get(link)
        val doc = Jsoup.parse(ByteArrayInputStream(r.body), Http.charset(r.contentType), r.url)
        val html = Extractor.extract(doc) ?: return null
        runCatching { Store.pageFile(link).writeText(html) }
        return html
    }
}
