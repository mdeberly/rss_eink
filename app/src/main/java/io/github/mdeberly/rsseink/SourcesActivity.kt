package io.github.mdeberly.rsseink

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.net.URL

class SourcesActivity : Activity() {

    private lateinit var url: EditText
    private lateinit var add: TextView
    private lateinit var message: TextView
    private lateinit var rows: LinearLayout
    private var busy = false

    private val suggestions = listOf(
        Feed("https://www.francetvinfo.fr/titres.rss", "franceinfo – Les titres"),
        Feed("https://www.francetvinfo.fr/monde.rss", "franceinfo – Monde"),
        Feed("https://www.lemonde.fr/rss/une.xml", "Le Monde – À la une"),
        Feed("https://www.lefigaro.fr/rss/figaro_actualites.xml", "Le Figaro – Actualités"),
        Feed("https://www.france24.com/fr/rss", "France 24"),
        Feed("https://www.rfi.fr/fr/rss", "RFI"),
        Feed("https://www.courrierinternational.com/feed/all/rss.xml", "Courrier international"),
        Feed("https://theconversation.com/fr/articles.atom", "The Conversation France"),
        Feed("https://www.numerama.com/feed/", "Numerama"),
        Feed("https://feeds.bbci.co.uk/news/rss.xml", "BBC News"),
        Feed("https://www.theguardian.com/world/rss", "The Guardian – World"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)
        url = findViewById(R.id.url)
        add = findViewById(R.id.btn_add)
        message = findViewById(R.id.message)
        rows = findViewById(R.id.rows)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        add.setOnClickListener { addFrom(url.text.toString()) }
        url.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                addFrom(url.text.toString()); true
            } else false
        }
        render()
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A page shared from the browser: look for its feed. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val link = Regex("https?://\\S+").find(text)?.value ?: text.trim()
        url.setText(link)
        addFrom(link)
    }

    private fun addFrom(input: String) {
        if (busy || input.isBlank()) return
        busy = true
        add.alpha = 0.3f
        say(getString(R.string.finding_feed))
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(url.windowToken, 0)
        Bg.io.execute {
            val result = runCatching { FeedFinder.find(input) }
            Bg.ui {
                busy = false
                add.alpha = 1f
                if (isDestroyed) return@ui
                result.onSuccess { (feedUrl, parsed) ->
                    val title = parsed.title.ifBlank { runCatching { URL(feedUrl).host.removePrefix("www.") }.getOrDefault(feedUrl) }
                    if (Store.hasFeed(feedUrl)) {
                        say(getString(R.string.feed_exists, title))
                    } else {
                        Store.addFeed(Feed(feedUrl, title))
                        say(getString(R.string.feed_added, title))
                        url.setText("")
                    }
                    render()
                }.onFailure {
                    say(getString(R.string.feed_not_found))
                }
            }
        }
    }

    private fun say(text: String) {
        message.visibility = View.VISIBLE
        message.text = text
    }

    private fun render() {
        rows.removeAllViews()
        val feeds = Store.feeds()
        if (feeds.isNotEmpty()) {
            section(getString(R.string.following))
            feeds.forEachIndexed { i, f ->
                if (i > 0) layoutInflater.inflate(R.layout.divider, rows, true)
                row(f, getString(R.string.remove)) {
                    Store.removeFeed(f.url)
                    render()
                }
            }
        }
        val others = suggestions.filter { !Store.hasFeed(it.url) }
        if (others.isNotEmpty()) {
            section(getString(R.string.suggestions))
            others.forEachIndexed { i, f ->
                if (i > 0) layoutInflater.inflate(R.layout.divider, rows, true)
                row(f, getString(R.string.add)) {
                    Store.addFeed(f)
                    say(getString(R.string.feed_added, f.title))
                    render()
                }
            }
        }
    }

    private fun section(title: String) {
        val v = TextView(this)
        v.text = title
        v.textSize = 15f
        v.setTypeface(v.typeface, android.graphics.Typeface.BOLD)
        v.setTextColor(getColor(R.color.ink))
        val pad = (16 * resources.displayMetrics.density).toInt()
        v.setPadding(pad, pad, pad, pad / 3)
        v.paint.isUnderlineText = true
        rows.addView(v)
    }

    private fun row(feed: Feed, action: String, onAction: () -> Unit) {
        val v = layoutInflater.inflate(R.layout.item_source, rows, false)
        v.findViewById<TextView>(R.id.name).text = feed.title
        v.findViewById<TextView>(R.id.address).text = feed.url.removePrefix("https://").removePrefix("http://")
        v.findViewById<TextView>(R.id.action).apply {
            text = action
            setOnClickListener { onAction() }
        }
        rows.addView(v)
    }
}
