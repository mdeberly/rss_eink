package io.github.mdeberly.rsseink

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object Http {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36 LectureRSS/1.0"

    class Response(val url: String, val contentType: String?, val body: ByteArray)

    /** GET with redirects followed manually (HttpURLConnection does not follow http → https). */
    fun get(address: String, maxBytes: Int = 6_000_000): Response {
        var current = address
        for (hop in 0 until 8) {
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty(
                    "Accept",
                    "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8, */*;q=0.5",
                )
                conn.setRequestProperty("Accept-Language", "fr, en;q=0.8")
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("HTTP $code")
                    current = URL(URL(current), location).toString()
                    continue
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val body = conn.inputStream.use { readLimited(it, maxBytes) }
                return Response(current, conn.contentType, body)
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > maxBytes) break
        }
        return out.toByteArray()
    }

    /** Charset from a Content-Type header, or null to let the parser detect it. */
    fun charset(contentType: String?): String? =
        contentType?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim('"', '\'', ' ')
            ?.takeIf { it.isNotEmpty() && runCatching { charset(it) }.isSuccess }
}
