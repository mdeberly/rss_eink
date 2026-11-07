package io.github.mdeberly.rsseink

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
    }
}
