package io.github.mdeberly.rsseink

import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reads RSS 2.0, RSS 1.0 (RDF) and Atom feeds. */
object FeedParser {

    data class Item(val title: String, val link: String, val date: Long?, val content: String)
    data class Parsed(val title: String, val siteUrl: String?, val items: List<Item>)

    fun looksLikeFeed(body: ByteArray): Boolean {
        val head = String(body, 0, minOf(body.size, 2048), Charsets.ISO_8859_1).lowercase()
        return "<rss" in head || "<feed" in head || "<rdf:rdf" in head
    }

    fun parse(body: ByteArray, baseUrl: String): Parsed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val p = factory.newPullParser()
        // Tolerate HTML entities such as &nbsp; that many feeds contain
        runCatching { p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        p.setInput(ByteArrayInputStream(stripBom(body)), null)

        var feedTitle = ""
        var siteUrl: String? = null
        val items = ArrayList<Item>()

        var inItem = false
        var title = ""
        var link: String? = null
        var guid: String? = null
        var published: Long? = null
        var updated: Long? = null
        var summary = ""
        var content = ""

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = p.name.lowercase()
                if (name == "item" || name == "entry") {
                    inItem = true
                    title = ""; link = null; guid = null; published = null; updated = null
                    summary = ""; content = ""
                } else if (inItem) {
                    when (name) {
                        "title" -> title = readInner(p)
                        "link" -> {
                            val href = p.getAttributeValue(null, "href")
                            if (href != null) {
                                val rel = p.getAttributeValue(null, "rel")
                                if (rel == null || rel == "alternate") link = href
                            } else {
                                val text = readInner(p)
                                if (text.isNotBlank()) link = text
                            }
                        }
                        "guid", "id" -> guid = readInner(p)
                        "pubdate", "published", "issued", "dc:date" -> published = parseDate(readInner(p))
                        "updated", "modified", "dc:modified" -> updated = parseDate(readInner(p))
                        "description", "summary", "media:description" ->
                            readInner(p).let { if (it.length > summary.length) summary = it }
                        "content:encoded", "content" -> readInner(p).let { if (it.length > content.length) content = it }
                    }
                } else when (name) {
                    "title" -> if (feedTitle.isEmpty()) feedTitle = Jsoup.parse(readInner(p)).text()
                    "link" -> if (siteUrl == null) {
                        val href = p.getAttributeValue(null, "href")
                        val rel = p.getAttributeValue(null, "rel")
                        siteUrl = if (href != null) href.takeIf { rel == null || rel == "alternate" } else readInner(p)
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                val name = p.name.lowercase()
                if (inItem && (name == "item" || name == "entry")) {
                    inItem = false
                    val target = (link ?: guid?.takeIf { it.startsWith("http") })?.trim()
                    if (!target.isNullOrEmpty()) {
                        items += Item(
                            title = Jsoup.parse(title).text().trim(),
                            link = resolve(baseUrl, target),
                            date = published ?: updated,
                            content = if (content.length >= summary.length) content else summary,
                        )
                    }
                }
            }
            event = p.next()
        }
        return Parsed(feedTitle.trim(), siteUrl?.trim()?.takeIf { it.isNotEmpty() }, items)
    }

    /** Text of the current element, keeping any nested markup (Atom xhtml content). */
    private fun readInner(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    sb.append('<').append(p.name)
                    for (i in 0 until p.attributeCount) {
                        sb.append(' ').append(p.getAttributeName(i)).append("=\"")
                            .append(p.getAttributeValue(i).replace("\"", "&quot;")).append('"')
                    }
                    sb.append('>')
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth > 0) sb.append("</").append(p.name).append('>')
                }
                XmlPullParser.TEXT -> sb.append(p.text)
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString().trim()
    }

    private fun stripBom(b: ByteArray): ByteArray =
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) b.copyOfRange(3, b.size) else b

    fun resolve(base: String, link: String): String = runCatching { URL(URL(base), link).toString() }.getOrDefault(link)

    private val legacyFormats = listOf(
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm zzz",
        "EEE, d MMM yyyy HH:mm Z",
        "d MMM yyyy HH:mm:ss zzz",
        "d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
    )

    fun parseDate(raw: String): Long? {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return null
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }
        runCatching { return LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
        for (pattern in legacyFormats) {
            runCatching {
                val f = SimpleDateFormat(pattern, Locale.ENGLISH)
                f.isLenient = true
                return f.parse(s)!!.time
            }
        }
        return null
    }
}
