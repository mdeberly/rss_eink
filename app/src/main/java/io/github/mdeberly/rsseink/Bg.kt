package io.github.mdeberly.rsseink

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Bg {
    val main = Handler(Looper.getMainLooper())
    val io: ExecutorService = Executors.newFixedThreadPool(3)

    fun ui(block: () -> Unit) {
        main.post(block)
    }
}
