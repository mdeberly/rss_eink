package io.github.mdeberly.rsseink

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import org.jsoup.Jsoup
import java.text.NumberFormat
import java.util.Date
import kotlin.math.abs

/**
 * Clean, paginated reader. The text is laid out in screen-wide CSS columns,
 * so a page turn never cuts a line in half, and the remaining reading time
 * is shown next to the page number.
 */
class ReaderActivity : Activity() {

    companion object {
        const val EXTRA_LINK = "link"
    }

    private lateinit var web: WebView
    private lateinit var pageLabel: TextView
    private lateinit var readerTime: TextView
    private lateinit var btnSave: ImageButton
    private lateinit var article: Article

    private var body: String? = null
    private var totalSeconds = 0
    private var pageCount = 1
    private var page = 0

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent.getStringExtra(EXTRA_LINK)
        article = link?.let { Store.findArticle(it) } ?: run { finish(); return }
        Store.markRead(article.link)

        setContentView(R.layout.activity_reader)
        web = findViewById(R.id.web)
        pageLabel = findViewById(R.id.page_label)
        readerTime = findViewById(R.id.reader_time)
        btnSave = findViewById(R.id.btn_save)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_open).setOnClickListener { openExternal(Uri.parse(article.link)) }
        btnSave.setOnClickListener { toggleSaved() }
        findViewById<ImageButton>(R.id.page_up).setOnClickListener { goTo(page - 1) }
        findViewById<ImageButton>(R.id.page_down).setOnClickListener { goTo(page + 1) }
        updateSaveIcon()

        // JavaScript only measures and turns pages; article HTML is sanitized (no scripts).
        web.settings.javaScriptEnabled = true
        web.settings.loadsImagesAutomatically = Prefs.images
        web.settings.blockNetworkImage = !Prefs.images
        web.settings.textZoom = 100
        web.settings.setSupportZoom(false)
        web.isHorizontalScrollBarEnabled = false
        web.isVerticalScrollBarEnabled = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                openExternal(request.url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                measurePages()
                // Pictures may still change the layout
                view.postDelayed({ measurePages() }, 800)
            }
        }
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                val dy = e2.y - (e1?.y ?: e2.y)
                if (abs(dx) < 60 && abs(dy) < 60) return false
                val forward = if (abs(dx) >= abs(dy)) dx < 0 else dy < 0
                goTo(page + if (forward) 1 else -1)
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Tap on the left third: back one page; elsewhere: forward
                goTo(page + if (e.x < web.width / 3f) -1 else 1)
                return true
            }
        })
        // The page never scrolls by itself: every touch is a page turn
        web.setOnTouchListener { _, ev -> gestures.onTouchEvent(ev); true }

        readerTime.setText(R.string.loading)
        pageLabel.text = ""
        load()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { goTo(page + 1); return true }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> { goTo(page - 1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    private fun load() {
        val link = article.link
        Store.savedArticle(link)?.let { show(it.html, fallback = false); return }
        Pages.cached(link)?.let { show(it, fallback = false); return }
        if (article.feedWords >= FULL_FEED_WORDS) { show(article.content, fallback = false); return }
        Bg.io.execute {
            val html = try { Pages.fetch(link) } catch (e: Exception) { null }
            val count = html?.let { ReadingTime.analyze(it) }
            Bg.ui {
                if (isFinishing || isDestroyed) return@ui
                if (html != null && count != null && count.words >= article.feedWords) {
                    Store.putEstimate(link, Estimate(count.words, count.images, true))
                    show(html, fallback = false)
                } else {
                    show(article.content, fallback = true)
                }
            }
        }
    }

    private fun show(html: String, fallback: Boolean) {
        val clean = Jsoup.clean(html, article.link, Extractor.safelist)
        body = clean
        val count = ReadingTime.analyze(clean)
        totalSeconds = ReadingTime.seconds(count.words, count.images, Prefs.wordsPerMinute, Prefs.images)
        val time = getString(R.string.reading_time, ReadingTime.format(totalSeconds))
        readerTime.text = time + " · " + getString(R.string.words, NumberFormat.getInstance().format(count.words))
        page = 0
        pageCount = 1
        web.loadDataWithBaseURL(article.link, buildPage(clean, time, fallback), "text/html", "utf-8", null)
    }

    private fun buildPage(content: String, time: String, fallback: Boolean): String {
        val font = if (Prefs.serif) "'Noto Serif', Georgia, serif" else "Lato, Roboto, 'Noto Sans', sans-serif"
        val date = if (article.date > 0) {
            DateFormat.getMediumDateFormat(this).format(Date(article.date)) + " " +
                DateFormat.getTimeFormat(this).format(Date(article.date))
        } else ""
        val meta = listOf(article.feedTitle, date, time).filter { it.isNotBlank() }
            .joinToString(" · ") { TextUtils.htmlEncode(it) }
        val note = if (fallback) "<p class=\"note\">${TextUtils.htmlEncode(getString(R.string.fallback_note))}</p>" else ""
        val images = if (Prefs.images) "" else "img,figure{display:none!important}"
        return """<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
html{height:100%;overflow:hidden}
body{margin:0;padding:12px 18px 8px;box-sizing:border-box;height:100vh;
 column-width:100vw;column-gap:36px;column-fill:auto;
 font-family:$font;font-size:${Prefs.textSize}px;line-height:1.5;color:#000;background:#fff;
 overflow-wrap:break-word;hyphens:auto;-webkit-hyphens:auto}
.meta{font-family:Lato,Roboto,sans-serif;font-size:.72em;color:#222;margin:0 0 .4em}
h1{font-size:1.45em;line-height:1.2;margin:.1em 0 .5em;break-after:avoid}
h2,h3,h4{line-height:1.25;break-after:avoid}
hr.top{border:0;border-top:2px solid #000;margin:.6em 0 .9em}
p{margin:0 0 .8em;orphans:2;widows:2}
img{display:block;max-width:100%;height:auto;max-height:calc(100vh - 80px);margin:.4em auto;border-radius:10px;
 filter:grayscale(1);break-inside:avoid}
figure{margin:0 0 .8em;break-inside:avoid}figcaption{font-size:.75em;color:#333}
a{color:#000}blockquote{margin:0 0 .8em;padding-left:.8em;border-left:3px solid #000}
pre{white-space:pre-wrap;font-size:.8em}
.note{font-style:italic;font-size:.85em;border:1px solid #000;padding:.5em}
.end{text-align:center;margin-top:1.2em}
$images
</style></head><body>
<p class="meta">$meta</p>
<h1>${TextUtils.htmlEncode(article.title)}</h1>
<hr class="top">
$note
$content
<p class="end">■</p>
</body></html>"""
    }

    private fun measurePages() {
        if (isDestroyed) return
        web.evaluateJavascript(
            "(function(){return Math.max(1,Math.ceil((document.documentElement.scrollWidth-2)/window.innerWidth));})()",
        ) { value ->
            pageCount = value?.trim('"')?.toDoubleOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
            goTo(page.coerceAtMost(pageCount - 1))
        }
    }

    private fun goTo(target: Int) {
        if (body == null) return
        if (target < 0 || target >= pageCount) return
        page = target
        // A transform rather than scrolling: the browser would clamp the scroll of the last page
        web.evaluateJavascript("document.body.style.transform='translateX(-'+($page*window.innerWidth)+'px)'", null)
        val remaining = if (page >= pageCount - 1) {
            getString(R.string.finished)
        } else {
            getString(R.string.remaining, ReadingTime.format(totalSeconds * (pageCount - page - 1) / pageCount))
        }
        pageLabel.text = "${page + 1} / $pageCount · $remaining"
    }

    private fun toggleSaved() {
        if (Store.isSaved(article.link)) {
            Store.unsave(article.link)
            Toast.makeText(this, R.string.unsaved_toast, Toast.LENGTH_SHORT).show()
        } else {
            Store.save(article, body ?: article.content)
            Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show()
        }
        updateSaveIcon()
    }

    private fun updateSaveIcon() {
        btnSave.setImageResource(if (Store.isSaved(article.link)) R.drawable.ic_star_filled else R.drawable.ic_star)
    }

    private fun openExternal(uri: Uri) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}
