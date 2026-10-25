package cz.fushift.heygrok

import kotlinx.coroutines.flow.MutableStateFlow

/** Published by [WakeWordService] for the UI. Times are SystemClock.elapsedRealtime(). */
object LiveState {
    val score = MutableStateFlow(0f)
    val running = MutableStateFlow(false)
    val lastDetectionAt = MutableStateFlow(0L)
}
