package io.github.mdeberly.rsseink

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {

    companion object {
        /** Articles per page, at most (fewer if they do not fit on the screen) */
        private const val MAX_PER_PAGE = 4
    }

    private lateinit var headerTitle: TextView
    private lateinit var status: TextView
    private lateinit var pageFrame: View
    private lateinit var page: LinearLayout
    private lateinit var empty: TextView
    private lateinit var pageLabel: TextView
    private lateinit var pageUp: ImageButton
    private lateinit var pageDown: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var navToday: TextView
    private lateinit var navSaved: TextView

    private var savedMode = false
    private var items: List<Article> = emptyList()
    private val pageStarts = ArrayList<Int>()
    private var pageIndex = 0
    private var refreshing = false
    private var feedsVersionSeen = -1
    private val visible = ArrayList<Pair<Article, View>>()
    private var updateScheduled = false
    private var flung = false

    private val gestures by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dy = e2.y - (e1?.y ?: e2.y)
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dy) < 80 && abs(dx) < 80) return false
                flung = true
                val forward = if (abs(dy) >= abs(dx)) dy < 0 else dx < 0
                showPage(pageIndex + if (forward) 1 else -1)
                return true
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        headerTitle = findViewById(R.id.header_title)
        status = findViewById(R.id.status)
        pageFrame = findViewById(R.id.page_frame)
        page = findViewById(R.id.page)
        empty = findViewById(R.id.empty)
        pageLabel = findViewById(R.id.page_label)
        pageUp = findViewById(R.id.page_up)
        pageDown = findViewById(R.id.page_down)
        btnRefresh = findViewById(R.id.btn_refresh)
        navToday = findViewById(R.id.nav_today)
        navSaved = findViewById(R.id.nav_saved)

        savedMode = savedInstanceState?.getBoolean("saved") ?: false

        btnRefresh.setOnClickListener { refresh() }
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        navToday.setOnClickListener { setMode(false) }
        navSaved.setOnClickListener { setMode(true) }
        findViewById<View>(R.id.nav_sources).setOnClickListener {
            startActivity(Intent(this, SourcesActivity::class.java))
        }
        pageUp.setOnClickListener { showPage(pageIndex - 1) }
        pageDown.setOnClickListener { showPage(pageIndex + 1) }
        pageFrame.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) pageFrame.post { rebuild(resetPage = false) }
        }

        if (Store.feeds().isNotEmpty() && System.currentTimeMillis() - Store.lastUpdated > 20 * 60_000L) {
            refresh()
        } else {
            Estimator.enqueue(Store.articles())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("saved", savedMode)
    }

    override fun onResume() {
        super.onResume()
        Estimator.listener = { scheduleUpdate() }
        if (feedsVersionSeen != -1 && feedsVersionSeen != Store.feedsVersion) refresh()
        feedsVersionSeen = Store.feedsVersion
        rebuild(resetPage = false)
    }

    override fun onDestroy() {
        Estimator.listener = null
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) flung = false
        gestures.onTouchEvent(ev)
        if (flung && ev.actionMasked == MotionEvent.ACTION_UP) {
            // A swipe turns the page; it must not also open the article under the finger
            ev.action = MotionEvent.ACTION_CANCEL
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { showPage(pageIndex + 1); return true }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> { showPage(pageIndex - 1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setMode(saved: Boolean) {
        if (savedMode == saved) return
        savedMode = saved
        rebuild(resetPage = true)
    }

    // ---- Data

    private fun refresh() {
        if (refreshing) return
        refreshing = true
        btnRefresh.alpha = 0.3f
        updateStatus()
        Bg.io.execute {
            val result = runCatching { Refresher.refreshAll() }
            Bg.ui {
                refreshing = false
                if (isDestroyed) return@ui
                btnRefresh.alpha = 1f
                rebuild(resetPage = true)
                Estimator.enqueue(Store.articles())
                val failed = result.getOrNull()?.failed ?: listOf("?")
                if (failed.isNotEmpty()) {
                    Toast.makeText(this, getString(R.string.feeds_failed, failed.joinToString(", ")), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun rebuild(resetPage: Boolean) {
        headerTitle.setText(if (savedMode) R.string.tab_saved else R.string.tab_today)
        setSelected(navToday, !savedMode)
        setSelected(navSaved, savedMode)

        val anchor = if (resetPage) null else pageStarts.getOrNull(pageIndex)?.let { items.getOrNull(it)?.link }
        val source = if (savedMode) Store.saved().map { it.article } else Store.articles()
        // Most recent first
        items = source.sortedByDescending { it.date }
        paginate()
        val anchorIndex = anchor?.let { link -> items.indexOfFirst { it.link == link } } ?: -1
        pageIndex = if (anchorIndex < 0) 0 else pageStarts.indexOfLast { it <= anchorIndex }.coerceAtLeast(0)
        showPage(pageIndex)
        updateStatus()
    }

    private fun setSelected(tab: TextView, selected: Boolean) {
        tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        tab.paint.isUnderlineText = selected
        tab.invalidate()
    }

    // ---- Pagination: as many whole articles as fit on the screen, no scrolling

    private fun paginate() {
        pageStarts.clear()
        if (items.isEmpty()) return
        val height = pageFrame.height
        val width = pageFrame.width
        if (height <= 0 || width <= 0) {
            pageStarts += 0
            return
        }
        val probe = layoutInflater.inflate(R.layout.item_article, page, false)
        val dividerHeight = resources.getDimensionPixelSize(R.dimen.divider)
        val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var start = 0
        var used = 0
        for (i in items.indices) {
            bind(probe, items[i])
            probe.measure(wSpec, hSpec)
            val h = probe.measuredHeight
            if (i == start) {
                used = h
            } else if (used + dividerHeight + h > height || i - start >= MAX_PER_PAGE) {
                pageStarts += start
                start = i
                used = h
            } else {
                used += dividerHeight + h
            }
        }
        pageStarts += start
    }

    private fun showPage(index: Int) {
        val count = pageStarts.size
        if (count > 0 && (index < 0 || index >= count)) return
        pageIndex = index.coerceAtLeast(0)
        page.removeAllViews()
        visible.clear()

        if (items.isEmpty()) {
            empty.visibility = View.VISIBLE
            empty.setText(
                when {
                    savedMode -> R.string.empty_saved
                    Store.feeds().isEmpty() -> R.string.empty_no_sources
                    else -> R.string.empty_no_articles
                },
            )
        } else {
            empty.visibility = View.GONE
            val start = pageStarts[pageIndex]
            val end = pageStarts.getOrNull(pageIndex + 1) ?: items.size
            for (i in start until end) {
                if (i > start) layoutInflater.inflate(R.layout.divider, page, true)
                val article = items[i]
                val v = layoutInflater.inflate(R.layout.item_article, page, false)
                bind(v, article)
                v.setOnClickListener {
                    startActivity(Intent(this, ReaderActivity::class.java).putExtra(ReaderActivity.EXTRA_LINK, article.link))
                }
                v.setOnLongClickListener {
                    toggleSaved(article)
                    true
                }
                page.addView(v)
                visible += article to v
            }
        }
        val total = maxOf(count, 1)
        pageLabel.text = getString(R.string.page_x_of_y, pageIndex + 1, total)
        pageUp.alpha = if (pageIndex > 0) 1f else 0.25f
        pageDown.alpha = if (pageIndex < total - 1) 1f else 0.25f
    }

    private fun bind(v: View, a: Article) {
        val title = v.findViewById<TextView>(R.id.title)
        title.text = a.title
        title.setTextColor(getColor(if (!savedMode && Store.isRead(a.link)) R.color.faded else R.color.ink))
        v.findViewById<TextView>(R.id.meta).text = a.feedTitle + " · " + formatDate(a.date)
        v.findViewById<TextView>(R.id.time).text = Estimator.display(a).label
    }

    private fun toggleSaved(a: Article) {
        if (Store.isSaved(a.link)) {
            Store.unsave(a.link)
            Toast.makeText(this, R.string.unsaved_toast, Toast.LENGTH_SHORT).show()
            if (savedMode) rebuild(resetPage = false)
        } else {
            Store.save(a, Pages.cached(a.link) ?: a.content)
            Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show()
            // Download the full text now so it can be read offline
            Bg.io.execute {
                val html = runCatching { Pages.fetch(a.link) }.getOrNull()
                if (html != null && Store.isSaved(a.link)) Store.save(a, html)
            }
        }
    }

    /** New reading times arrived: refresh the visible rows, at most once every 1.5 s (E Ink friendly). */
    private fun scheduleUpdate() {
        if (updateScheduled) return
        updateScheduled = true
        Bg.main.postDelayed({
            updateScheduled = false
            if (isDestroyed) return@postDelayed
            for ((a, v) in visible) {
                val t = v.findViewById<TextView>(R.id.time)
                val label = Estimator.display(a).label
                if (t.text != label) t.text = label
            }
            updateStatus()
        }, 1500)
    }

    private fun updateStatus() {
        if (refreshing) {
            status.setText(R.string.updating)
            return
        }
        val unread = items.filter { savedMode || !Store.isRead(it.link) }
        val total = unread.sumOf { Estimator.display(it).seconds }
        var text = getString(R.string.status_count, items.size, ReadingTime.format(total))
        if (!savedMode && Store.lastUpdated > 0) {
            text += " · " + getString(R.string.status_updated, formatDate(Store.lastUpdated))
            if (Store.lastRefreshFailed) text += " · " + getString(R.string.status_offline)
        }
        status.text = text
    }

    private fun formatDate(time: Long): String {
        if (time <= 0) return ""
        val time24 = DateFormat.getTimeFormat(this).format(Date(time))
        val then = Calendar.getInstance().apply { timeInMillis = time }
        val now = Calendar.getInstance()
        val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) time24 else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(time)) + " " + time24
    }
}
