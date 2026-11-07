package io.github.mdeberly.rsseink

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTimeTest {

    @Test fun countsFrenchWords() {
        assertEquals(5, ReadingTime.countWords("Aujourd'hui, l'Iran — peut-être demain ? 2026"))
        assertEquals(3, ReadingTime.countWords("  Bonjour   le\nmonde. "))
        assertEquals(0, ReadingTime.countWords(" — … ! "))
    }

    @Test fun countsCjkAsHalfWords() {
        assertEquals(2, ReadingTime.countWords("新闻阅读"))
    }

    @Test fun analyzesHtml() {
        val c = ReadingTime.analyze("<p>Un <b>deux</b> trois</p><figure><img src='a.jpg'><figcaption>quatre</figcaption></figure>")
        assertEquals(4, c.words)
        assertEquals(1, c.images)
    }

    @Test fun seconds() {
        assertEquals(60, ReadingTime.seconds(230, 0, 230, true))
        assertEquals(60 + 12 + 11, ReadingTime.seconds(230, 2, 230, true))
        assertEquals(60, ReadingTime.seconds(230, 2, 230, false))
    }

    @Test fun formats() {
        assertEquals("< 1 min", ReadingTime.format(20))
        assertEquals("1 min", ReadingTime.format(50))
        assertEquals("5 min", ReadingTime.format(300))
        assertEquals("1 h 02", ReadingTime.format(3720))
    }
}
