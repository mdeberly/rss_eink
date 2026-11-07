package io.github.mdeberly.rsseink

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    var wordsPerMinute: Int
        get() = sp.getInt("wpm", 230)
        set(v) = sp.edit().putInt("wpm", v).apply()

    var fullEstimate: Boolean
        get() = sp.getBoolean("full_estimate", true)
        set(v) = sp.edit().putBoolean("full_estimate", v).apply()

    /** Reader text size in CSS pixels */
    var textSize: Int
        get() = sp.getInt("text_size", 20)
        set(v) = sp.edit().putInt("text_size", v).apply()

    var serif: Boolean
        get() = sp.getBoolean("serif", true)
        set(v) = sp.edit().putBoolean("serif", v).apply()

    var images: Boolean
        get() = sp.getBoolean("images", true)
        set(v) = sp.edit().putBoolean("images", v).apply()

    var maxAgeDays: Int
        get() = sp.getInt("max_age_days", 2)
        set(v) = sp.edit().putInt("max_age_days", v).apply()
}
