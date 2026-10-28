package cz.fushift.heygrok.wakeword

import ai.onnxruntime.OrtEnvironment
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class WakeWordDetectorTest {

    private val assets = File("../app/src/main/assets")

    // Voices held out from training (Piper en_US kristin and joe).
    @Test
    fun heyGrokTriggers() {
        assertTrue(maxScore(GROK, wav("hey_grok_kristin.wav")) > 0.9f)
        assertTrue(maxScore(GROK, wav("hey_grok_joe.wav")) > 0.9f)
    }

    @Test
    fun heyJarvisDoesNotTriggerGrok() {
        assertTrue(maxScore(GROK, wav("hey_jarvis.wav")) < 0.2f)
        assertTrue(maxScore(GROK, wav("hey_jarvis_gb.wav")) < 0.2f)
    }

    @Test
    fun otherSpeechDoesNotTriggerGrok() {
        assertTrue(maxScore(GROK, wav("negative_speech.wav")) < 0.2f)
    }

    @Test
    fun heyJarvisTriggers() {
        assertTrue(maxScore(JARVIS, wav("hey_jarvis.wav")) > 0.5f)
        assertTrue(maxScore(JARVIS, wav("hey_jarvis_gb.wav")) > 0.5f)
    }

    @Test
    fun otherSpeechDoesNotTriggerJarvis() {
        assertTrue(maxScore(JARVIS, wav("hey_grok_kristin.wav")) < 0.2f)
        assertTrue(maxScore(JARVIS, wav("negative_speech.wav")) < 0.2f)
    }

    @Test
    fun silenceAndNoiseDoNotTrigger() {
        val silence = ShortArray(3 * WakeWordDetector.SAMPLE_RATE)
        val random = Random(42)
        val noise = ShortArray(3 * WakeWordDetector.SAMPLE_RATE) { random.nextInt(-3000, 3001).toShort() }
        for (model in listOf(GROK, JARVIS)) {
            assertTrue(maxScore(model, silence) < 0.2f)
            assertTrue(maxScore(model, noise) < 0.2f)
        }
    }

    @Test
    fun setWakeModelSwitchesPhrase() {
        detector(GROK).use {
            it.setWakeModel(File(assets, JARVIS).readBytes())
            assertTrue(it.maxScore(wav("hey_jarvis.wav")) > 0.5f)
        }
    }

    private fun wav(name: String): ShortArray {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/$name")).use { it.readBytes() }
        val pcm = ByteBuffer.wrap(bytes, WAV_HEADER, bytes.size - WAV_HEADER).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        // 1 s of silence on both sides, like openWakeWord's Model.predict_clip(padding=1).
        val padding = WakeWordDetector.SAMPLE_RATE
        val samples = ShortArray(padding + pcm.remaining() + padding)
        pcm.get(samples, padding, pcm.remaining())
        return samples
    }

    private fun detector(model: String) = WakeWordDetector(
        OrtEnvironment.getEnvironment(),
        File(assets, "melspectrogram.onnx").readBytes(),
        File(assets, "embedding_model.onnx").readBytes(),
        File(assets, model).readBytes(),
    )

    private fun maxScore(model: String, samples: ShortArray): Float =
        detector(model).use { it.maxScore(samples) }.also { println("$model max score: $it") }

    private fun WakeWordDetector.maxScore(samples: ShortArray): Float =
        (0..samples.size - WakeWordDetector.FRAME_SIZE step WakeWordDetector.FRAME_SIZE).maxOf { start ->
            process(samples.copyOfRange(start, start + WakeWordDetector.FRAME_SIZE))
        }

    private companion object {
        const val WAV_HEADER = 44
        const val GROK = "wakeword_hey_grok.onnx"
        const val JARVIS = "wakeword_hey_jarvis.onnx"
    }
}
