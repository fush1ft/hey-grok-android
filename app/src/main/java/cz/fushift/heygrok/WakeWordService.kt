package cz.fushift.heygrok

import ai.onnxruntime.OrtEnvironment
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import cz.fushift.heygrok.wakeword.WakeWordDetector
import kotlin.concurrent.thread

class WakeWordService : Service() {

    @Volatile
    private var stopping = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    // ServiceCompat drops the service type below API 29, where it does not exist.
    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(this).enabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.LISTENING_ID,
                Notifications.listening(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: RuntimeException) {
            // SecurityException without mic access; ForegroundServiceStartNotAllowedException
            // (an IllegalStateException, API 31+) when started from the background.
            if (e !is SecurityException && e !is IllegalStateException) throw e
            Log.w(TAG, "Cannot start listening", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker == null) worker = thread(name = "wake-word") { listen() }
        // Not sticky: a restart by the system from the background would get no mic access on Android 14+.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopping = true
        worker?.join(1000)
        super.onDestroy()
    }

    // startForeground with the microphone type has already failed if RECORD_AUDIO is missing.
    @SuppressLint("MissingPermission")
    private fun listen() {
        val prefs = Prefs(this)
        val detector = WakeWordDetector(
            OrtEnvironment.getEnvironment(),
            asset("melspectrogram.onnx"),
            asset("embedding_model.onnx"),
            asset("wakeword.onnx"),
        )
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            maxOf(minBuffer, WakeWordDetector.FRAME_SIZE * 2 * 4),
        )
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
        val frame = ShortArray(WakeWordDetector.FRAME_SIZE)
        var lastTrigger = 0L
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord failed to initialize")
                return
            }
            record.startRecording()
            LiveState.running.value = true
            while (readFrame(record, frame)) {
                val score = detector.process(frame)
                LiveState.score.value = score
                val now = SystemClock.elapsedRealtime()
                if (score >= prefs.threshold && now - lastTrigger >= RETRIGGER_MS) {
                    lastTrigger = now
                    LiveState.lastDetectionAt.value = now
                    if (prefs.chime) tone.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                    GrokLauncher.launch(this)
                    detector.reset()
                }
            }
        } finally {
            LiveState.running.value = false
            LiveState.score.value = 0f
            record.release()
            tone.release()
            detector.close()
            if (!stopping) stopSelf()
        }
    }

    private fun readFrame(record: AudioRecord, frame: ShortArray): Boolean {
        var filled = 0
        while (filled < frame.size) {
            if (stopping) return false
            val read = record.read(frame, filled, frame.size - filled)
            if (read < 0) {
                Log.w(TAG, "AudioRecord.read failed: $read")
                return false
            }
            filled += read
        }
        return true
    }

    private fun asset(name: String): ByteArray = assets.open(name).use { it.readBytes() }

    companion object {
        const val ACTION_STOP = "cz.fushift.heygrok.STOP"
        private const val TAG = "WakeWordService"
        private const val SAMPLE_RATE = WakeWordDetector.SAMPLE_RATE
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val RETRIGGER_MS = 2000L
    }
}
