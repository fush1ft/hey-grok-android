package cz.fushift.heygrok.wakeword

import ai.onnxruntime.OrtEnvironment
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertTrue

class WakeWordDetectorTest {

    private val assets = File("../app/src/main/assets")

    // Voices held out from training (Piper en_US kristin and joe).
    @Test
    fun heyGrokTriggers() {
        assertTrue(maxScore("hey_grok_kristin.wav") > 0.9f)
        assertTrue(maxScore("hey_grok_joe.wav") > 0.9f)
    }

    @Test
    fun heyJarvisDoesNotTrigger() {
        assertTrue(maxScore("hey_jarvis.wav") < 0.2f)
        assertTrue(maxScore("hey_jarvis_gb.wav") < 0.2f)
    }

    @Test
    fun otherSpeechDoesNotTrigger() {
        assertTrue(maxScore("negative_speech.wav") < 0.2f)
    }

    private fun maxScore(wav: String): Float {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/$wav")).use { it.readBytes() }
        val pcm = ByteBuffer.wrap(bytes, WAV_HEADER, bytes.size - WAV_HEADER).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        // 1 s of silence on both sides, like openWakeWord's Model.predict_clip(padding=1).
        val padding = WakeWordDetector.SAMPLE_RATE
        val samples = ShortArray(padding + pcm.remaining() + padding)
        pcm.get(samples, padding, pcm.remaining())

        val detector = WakeWordDetector(
            OrtEnvironment.getEnvironment(),
            File(assets, "melspectrogram.onnx").readBytes(),
            File(assets, "embedding_model.onnx").readBytes(),
            File(assets, "wakeword.onnx").readBytes(),
        )
        val max = detector.use {
            (0..samples.size - WakeWordDetector.FRAME_SIZE step WakeWordDetector.FRAME_SIZE).maxOf { start ->
                detector.process(samples.copyOfRange(start, start + WakeWordDetector.FRAME_SIZE))
            }
        }
        println("$wav max score: $max")
        return max
    }

    private companion object {
        const val WAV_HEADER = 44
    }
}
