package io.github.mdeberly.rsseink

import java.io.IOException
import java.util.Collections
import java.util.concurrent.Executors

/** Computes reading times from the full article pages, in the background. */
object Estimator {

    /** What to show for an article in the list. */
    class Display(val seconds: Int, val approximate: Boolean, val pending: Boolean) {
        val label: String
            get() = when {
                pending -> "… min"
                approximate -> "~" + ReadingTime.format(seconds)
                else -> ReadingTime.format(seconds)
            }
    }

    private val pool = Executors.newFixedThreadPool(3)
    private val pending = Collections.synchronizedSet(HashSet<String>())
    var listener: (() -> Unit)? = null

    fun display(a: Article): Display {
        val e = Store.estimate(a.link)
        val words = e?.words ?: a.feedWords
        val images = e?.images ?: a.feedImages
        val seconds = ReadingTime.seconds(words, images, Prefs.wordsPerMinute, Prefs.images)
        return Display(seconds, approximate = e?.full != true, pending = e == null && a.link in pending)
    }

    fun enqueue(articles: List<Article>) {
        if (!Prefs.fullEstimate) return
        for (a in articles) {
            if (Store.estimate(a.link) != null) continue
            if (!pending.add(a.link)) continue
            pool.execute {
                try {
                    estimate(a)
                } finally {
                    pending.remove(a.link)
                    Bg.ui { listener?.invoke() }
                }
            }
        }
    }

    private fun estimate(a: Article) {
        val html = try {
            Pages.fetch(a.link)
        } catch (e: IOException) {
            return // offline or server error: try again on the next refresh
        } catch (e: Exception) {
            null
        }
        val count = html?.let { ReadingTime.analyze(it) }
        val estimate = if (count != null && count.words >= a.feedWords) {
            Estimate(count.words, count.images, true)
        } else {
            Estimate(a.feedWords, a.feedImages, a.feedWords >= FULL_FEED_WORDS)
        }
        Store.putEstimate(a.link, estimate)
    }
}
