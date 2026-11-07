package io.github.mdeberly.rsseink

data class Feed(val url: String, val title: String)

data class Article(
    val link: String,
    val title: String,
    val feedTitle: String,
    val feedUrl: String,
    val date: Long,
    /** HTML from the feed itself (summary or full text) */
    val content: String,
    val feedWords: Int,
    val feedImages: Int,
)

/** Word and picture count used for the reading time. [full] is false when only the feed summary was available. */
data class Estimate(val words: Int, val images: Int, val full: Boolean)

data class SavedArticle(val article: Article, val html: String, val savedAt: Long)

/** Feeds with at least this many words are considered to carry the full article. */
const val FULL_FEED_WORDS = 300
