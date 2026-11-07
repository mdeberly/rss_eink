package io.github.mdeberly.rsseink

import org.jsoup.Jsoup
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Estimated reading time.
 *
 * Words are counted on the visible text of the article. Characters of
 * scripts written without spaces (Chinese, Japanese, Korean) count as half
 * a word each. Each picture adds a few seconds (12 s for the first one,
 * then one second less for each following picture, never under 3 s).
 */
object ReadingTime {

    data class Count(val words: Int, val images: Int)

    private val CJK = setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
    )

    fun countWords(text: String): Int {
        var words = 0
        var cjk = 0
        var inWord = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                Character.isLetterOrDigit(cp) && Character.UnicodeScript.of(cp) in CJK -> {
                    if (inWord) { words++; inWord = false }
                    cjk++
                }
                Character.isLetterOrDigit(cp) -> inWord = true
                // Apostrophes and hyphens keep a word together: "aujourd'hui", "peut-être"
                inWord && (cp == '\''.code || cp == '’'.code || cp == '-'.code) -> Unit
                else -> if (inWord) { words++; inWord = false }
            }
        }
        if (inWord) words++
        return words + (cjk + 1) / 2
    }

    /** Counts words and pictures in an HTML fragment (or plain text). */
    fun analyze(html: String): Count {
        if (html.isBlank()) return Count(0, 0)
        val doc = Jsoup.parseBodyFragment(html)
        return Count(countWords(doc.text()), doc.select("img").size)
    }

    fun seconds(words: Int, images: Int, wordsPerMinute: Int, countImages: Boolean): Int {
        var s = words * 60.0 / max(wordsPerMinute, 1)
        if (countImages) for (i in 0 until images) s += max(3, 12 - i)
        return s.roundToInt()
    }

    /** "< 1 min", "4 min", "1 h 05" */
    fun format(seconds: Int): String {
        if (seconds < 45) return "< 1 min"
        val minutes = max(1, (seconds + 30) / 60)
        return if (minutes < 60) "$minutes min" else "%d h %02d".format(minutes / 60, minutes % 60)
    }
}
