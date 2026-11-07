package io.github.mdeberly.rsseink

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.RadioGroup

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        radio(
            R.id.speed,
            mapOf(R.id.speed_180 to 180, R.id.speed_230 to 230, R.id.speed_280 to 280, R.id.speed_350 to 350),
            Prefs.wordsPerMinute,
        ) { Prefs.wordsPerMinute = it }
        radio(
            R.id.age,
            mapOf(R.id.age_1 to 1, R.id.age_2 to 2, R.id.age_3 to 3, R.id.age_7 to 7),
            Prefs.maxAgeDays,
        ) { Prefs.maxAgeDays = it }
        radio(
            R.id.size,
            mapOf(R.id.size_s to 17, R.id.size_m to 20, R.id.size_l to 23, R.id.size_xl to 27),
            Prefs.textSize,
        ) { Prefs.textSize = it }
        radio(
            R.id.font,
            mapOf(R.id.font_serif to 1, R.id.font_sans to 0),
            if (Prefs.serif) 1 else 0,
        ) { Prefs.serif = it == 1 }

        check(R.id.full_estimate, Prefs.fullEstimate) {
            Prefs.fullEstimate = it
            if (it) Estimator.enqueue(Store.articles())
        }
        check(R.id.images, Prefs.images) { Prefs.images = it }
    }

    private fun radio(groupId: Int, values: Map<Int, Int>, current: Int, onChange: (Int) -> Unit) {
        val group = findViewById<RadioGroup>(groupId)
        val selected = values.entries.minByOrNull { kotlin.math.abs(it.value - current) }?.key
        if (selected != null) group.check(selected)
        group.setOnCheckedChangeListener { _, id -> values[id]?.let(onChange) }
    }

    private fun check(id: Int, value: Boolean, onChange: (Boolean) -> Unit) {
        val box = findViewById<CheckBox>(id)
        box.isChecked = value
        box.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }
}
