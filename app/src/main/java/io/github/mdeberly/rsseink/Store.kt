package io.github.mdeberly.rsseink

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Everything the app keeps on the device, as small JSON files. */
object Store {
    private lateinit var dir: File
    private lateinit var pagesDir: File

    private val feeds = ArrayList<Feed>()
    private var articles: List<Article> = emptyList()
    private val saved = ArrayList<SavedArticle>()
    private val estimates = HashMap<String, Estimate>()
    private val read = LinkedHashSet<String>()

    @Volatile var lastUpdated = 0L; private set
    @Volatile var lastRefreshFailed = false
    @Volatile var feedsVersion = 0; private set

    private var estimatesDirty = false
    private val saveEstimates = Runnable {
        val json = synchronized(this) {
            estimatesDirty = false
            JSONObject().also { o ->
                estimates.forEach { (k, e) ->
                    o.put(k, JSONArray().put(e.words).put(e.images).put(e.full))
                }
            }.toString()
        }
        Bg.io.execute { write("estimates.json", json) }
    }

    fun init(context: Context) {
        dir = context.filesDir
        pagesDir = File(context.cacheDir, "pages").apply { mkdirs() }
        synchronized(this) {
            read("feeds.json")?.let { JSONArray(it) }?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { feeds += Feed(it.getString("url"), it.optString("title")) }
            }
            read("articles.json")?.let { JSONObject(it) }?.let { o ->
                lastUpdated = o.optLong("updated")
                val a = o.getJSONArray("items")
                articles = (0 until a.length()).map { articleFrom(a.getJSONObject(it)) }
            }
            read("saved.json")?.let { JSONArray(it) }?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let {
                    saved += SavedArticle(articleFrom(it.getJSONObject("article")), it.getString("html"), it.optLong("savedAt"))
                }
            }
            read("estimates.json")?.let { JSONObject(it) }?.let { o ->
                for (k in o.keys()) o.getJSONArray(k).let { estimates[k] = Estimate(it.getInt(0), it.getInt(1), it.getBoolean(2)) }
            }
            read("read.json")?.let { JSONArray(it) }?.let { a -> for (i in 0 until a.length()) read += a.getString(i) }
        }
    }

    // ---- Feeds

    @Synchronized fun feeds(): List<Feed> = feeds.toList()

    @Synchronized fun hasFeed(url: String) = feeds.any { it.url == url }

    @Synchronized fun addFeed(feed: Feed) {
        if (hasFeed(feed.url)) return
        feeds += feed
        feedsVersion++
        saveFeeds()
    }

    @Synchronized fun removeFeed(url: String) {
        feeds.removeAll { it.url == url }
        articles = articles.filter { it.feedUrl != url }
        feedsVersion++
        saveFeeds()
        saveArticles()
    }

    private fun saveFeeds() {
        val a = JSONArray()
        feeds.forEach { a.put(JSONObject().put("url", it.url).put("title", it.title)) }
        write("feeds.json", a.toString())
    }

    // ---- Articles

    @Synchronized fun articles(): List<Article> = articles

    @Synchronized fun setArticles(list: List<Article>, updated: Long) {
        articles = list
        lastUpdated = updated
        saveArticles()
        prune()
    }

    private fun saveArticles() {
        val a = JSONArray()
        articles.forEach { a.put(articleJson(it)) }
        write("articles.json", JSONObject().put("updated", lastUpdated).put("items", a).toString())
    }

    @Synchronized fun findArticle(link: String): Article? =
        articles.firstOrNull { it.link == link } ?: saved.firstOrNull { it.article.link == link }?.article

    // ---- Saved

    @Synchronized fun saved(): List<SavedArticle> = saved.toList()

    @Synchronized fun savedArticle(link: String): SavedArticle? = saved.firstOrNull { it.article.link == link }

    @Synchronized fun isSaved(link: String) = saved.any { it.article.link == link }

    @Synchronized fun save(article: Article, html: String) {
        saved.removeAll { it.article.link == article.link }
        saved.add(0, SavedArticle(article, html, System.currentTimeMillis()))
        writeSaved()
    }

    @Synchronized fun unsave(link: String) {
        saved.removeAll { it.article.link == link }
        writeSaved()
    }

    private fun writeSaved() {
        val a = JSONArray()
        saved.forEach {
            a.put(JSONObject().put("article", articleJson(it.article)).put("html", it.html).put("savedAt", it.savedAt))
        }
        write("saved.json", a.toString())
    }

    // ---- Reading-time estimates

    @Synchronized fun estimate(link: String): Estimate? = estimates[link]

    @Synchronized fun putEstimate(link: String, e: Estimate) {
        estimates[link] = e
        if (!estimatesDirty) {
            estimatesDirty = true
            Bg.main.postDelayed(saveEstimates, 3000)
        }
    }

    // ---- Read marks

    @Synchronized fun isRead(link: String) = link in read

    @Synchronized fun markRead(link: String) {
        if (read.add(link)) write("read.json", JSONArray(read.toList()).toString())
    }

    // ---- Extracted pages cache

    fun pageFile(link: String) = File(pagesDir, hash(link) + ".html")

    /** Forgets data about articles that are no longer listed nor saved. */
    private fun prune() {
        val keep = HashSet<String>()
        articles.forEach { keep += it.link }
        saved.forEach { keep += it.article.link }
        if (estimates.keys.retainAll(keep)) {
            estimatesDirty = true
            Bg.main.postDelayed(saveEstimates, 3000)
        }
        if (read.retainAll(keep)) write("read.json", JSONArray(read.toList()).toString())
        val files = keep.map { hash(it) + ".html" }.toSet()
        pagesDir.listFiles()?.forEach { if (it.name !in files) it.delete() }
    }

    // ---- JSON helpers

    private fun articleJson(a: Article) = JSONObject()
        .put("link", a.link).put("title", a.title).put("feedTitle", a.feedTitle).put("feedUrl", a.feedUrl)
        .put("date", a.date).put("content", a.content).put("words", a.feedWords).put("images", a.feedImages)

    private fun articleFrom(o: JSONObject) = Article(
        link = o.getString("link"),
        title = o.optString("title"),
        feedTitle = o.optString("feedTitle"),
        feedUrl = o.optString("feedUrl"),
        date = o.optLong("date"),
        content = o.optString("content"),
        feedWords = o.optInt("words"),
        feedImages = o.optInt("images"),
    )

    private fun read(name: String): String? =
        runCatching { File(dir, name).takeIf { it.exists() }?.readText() }.getOrNull()

    private fun write(name: String, text: String) {
        synchronized(dir) {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, name))
        }
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
