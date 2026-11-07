package io.github.mdeberly.rsseink

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

/**
 * Pulls the main text out of a web page, without menus, ads and other clutter.
 * A small "readability" heuristic: paragraphs vote for their parent containers,
 * the best scored container wins.
 */
object Extractor {

    private val NEGATIVE = Regex(
        "(?i)(comment|share|sharing|social|related|promo|sidebar|footer|masthead|nav|menu|breadcrumb|" +
            "\\bads?\\b|advert|sponsor|newsletter|cookie|consent|subscribe|abonn|paywall|popup|modal|" +
            "banner|outbrain|taboola|teaser|recommend|lire-aussi|read-more|readmore|tags|byline|author-bio|toolbar)",
    )
    private val KEEP_TAGS = setOf("html", "body", "article", "main")

    val safelist: Safelist = Safelist.relaxed()
        .removeTags("table", "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup", "col", "caption")
        .addTags("figure", "figcaption", "hr")

    /** Returns clean HTML of the article body, or null when nothing readable was found. */
    fun extract(doc: Document): String? {
        val baseUri = doc.location()

        // Lazy-loaded pictures
        for (img in doc.select("img")) {
            for (attr in listOf("data-src", "data-lazy-src", "data-original", "data-url")) {
                val v = img.attr(attr)
                if (v.isNotBlank()) { img.attr("src", v); break }
            }
            if (img.attr("src").startsWith("data:") || img.attr("src").isBlank()) {
                val srcset = img.attr("data-srcset").ifBlank { img.attr("srcset") }
                srcset.split(',').firstOrNull()?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
                    ?.let { img.attr("src", it) }
            }
        }

        doc.select(
            "script, style, noscript, iframe, form, nav, footer, aside, button, svg, header, input, " +
                "select, textarea, object, embed, [hidden], [aria-hidden=true], [role=navigation], [role=dialog]",
        ).remove()

        val body = doc.body() ?: return null
        for (el in body.select("*")) {
            if (el.parent() == null || el.tagName() in KEEP_TAGS) continue
            val hint = el.className() + " " + el.id()
            if (hint.isBlank() || !NEGATIVE.containsMatchIn(hint)) continue
            // A wrapper such as "page has-sidebar" may still hold the article
            val longText = el.select("p").count { it.text().length > 80 } >= 4
            if (!longText) el.remove()
        }

        val scores = HashMap<Element, Double>()
        for (p in body.select("p, pre, blockquote")) {
            val text = p.text()
            if (text.length < 25) continue
            val score = 1.0 + text.count { it == ',' } + minOf(text.length / 100, 3)
            p.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score }
            p.parent()?.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score / 2 }
        }
        var best = scores.entries
            .maxByOrNull { (el, s) -> s * (1 - linkDensity(el)) }
            ?.key ?: return null
        // Climb to the enclosing container (e.g. <article>) when it adds little
        // text, so the lead picture and subheadings next to the text are kept.
        while (true) {
            val parent = best.parent() ?: break
            if (parent.tagName() == "body" || parent.tagName() == "html") break
            if (parent.text().length <= best.text().length * 1.3 && linkDensity(parent) < 0.3) best = parent else break
        }

        // Clean inside the chosen container
        best.select("ul, ol, div, section").filter { linkDensity(it) > 0.5 }.forEach { it.remove() }
        best.select("h1").remove() // the title is shown separately

        val html = Jsoup.clean(best.html(), baseUri, safelist)
        val text = Jsoup.parseBodyFragment(html).text()
        return if (text.length < 200) null else html
    }

    private fun linkDensity(el: Element): Double {
        val total = el.text().length
        if (total == 0) return 0.0
        val links = el.select("a").sumOf { it.text().length }
        return links.toDouble() / total
    }
}
