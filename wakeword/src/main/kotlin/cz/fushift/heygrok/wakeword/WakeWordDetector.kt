package cz.fushift.heygrok.wakeword

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Streaming port of openWakeWord 0.6.0 (AudioFeatures._streaming_features + Model.predict)
 * for a single wake model fed 1280-sample frames of 16 kHz mono PCM.
 */
class WakeWordDetector(
    private val env: OrtEnvironment,
    melModel: ByteArray,
    embeddingModel: ByteArray,
    wakeModel: ByteArray,
) : AutoCloseable {

    private val melSession = env.createSession(melModel)
    private val embeddingSession = env.createSession(embeddingModel)
    private var wakeSession = env.createSession(wakeModel)
    private var wakeInputName = wakeSession.inputNames.first()

    private val audio = FloatArray(AUDIO_WINDOW)
    private var audioFilled = 0
    private val melRows = ArrayDeque<FloatArray>()
    private val embeddings = ArrayDeque<FloatArray>()

    private val melInput = directFloats(AUDIO_WINDOW)
    private val embeddingInput = directFloats(MEL_WINDOW * MEL_BINS)
    private val wakeInput = directFloats(WAKE_WINDOW * EMBEDDING_SIZE)

    init {
        reset()
    }

    fun reset() {
        audio.fill(0f)
        audioFilled = 0
        melRows.clear()
        repeat(MEL_WINDOW) { melRows.addLast(FloatArray(MEL_BINS) { 1f }) }
        embeddings.clear()
    }

    fun setWakeModel(wakeModel: ByteArray) {
        val session = env.createSession(wakeModel)
        wakeSession.close()
        wakeSession = session
        wakeInputName = session.inputNames.first()
        reset()
    }

    /** Returns the wake-word score (0..1) for one 80 ms frame of exactly [FRAME_SIZE] samples. */
    fun process(frame: ShortArray): Float {
        require(frame.size == FRAME_SIZE) { "Expected $FRAME_SIZE samples, got ${frame.size}" }
        audio.copyInto(audio, 0, FRAME_SIZE, AUDIO_WINDOW)
        for (i in frame.indices) audio[AUDIO_WINDOW - FRAME_SIZE + i] = frame[i].toFloat()
        audioFilled = minOf(audioFilled + FRAME_SIZE, AUDIO_WINDOW)

        appendMelRows()
        appendEmbedding()
        // The reference pre-fills the feature buffer with embeddings of random noise; we wait for
        // real ones instead. This also covers the reference zeroing its first 5 predictions.
        if (embeddings.size < WAKE_WINDOW) return 0f
        return predictWake()
    }

    private fun appendMelRows() {
        melInput.clear()
        // The reference feeds raw int16 values as floats, not normalised to -1..1.
        melInput.put(audio, AUDIO_WINDOW - audioFilled, audioFilled).flip()
        val output = run(melSession, "input", melInput, longArrayOf(1, audioFilled.toLong()))
        while (output.remaining() >= MEL_BINS) {
            val row = FloatArray(MEL_BINS)
            output.get(row)
            for (j in row.indices) row[j] = row[j] / 10f + 2f
            melRows.addLast(row)
        }
        while (melRows.size > MAX_MEL_ROWS) melRows.removeFirst()
    }

    private fun appendEmbedding() {
        embeddingInput.clear()
        for (i in melRows.size - MEL_WINDOW until melRows.size) embeddingInput.put(melRows[i])
        embeddingInput.flip()
        val output = run(embeddingSession, "input_1", embeddingInput, longArrayOf(1, MEL_WINDOW.toLong(), MEL_BINS.toLong(), 1))
        val embedding = FloatArray(EMBEDDING_SIZE)
        output.get(embedding)
        embeddings.addLast(embedding)
        while (embeddings.size > MAX_EMBEDDINGS) embeddings.removeFirst()
    }

    private fun predictWake(): Float {
        wakeInput.clear()
        for (i in embeddings.size - WAKE_WINDOW until embeddings.size) wakeInput.put(embeddings[i])
        wakeInput.flip()
        val output = run(wakeSession, wakeInputName, wakeInput, longArrayOf(1, WAKE_WINDOW.toLong(), EMBEDDING_SIZE.toLong()))
        return output.get(0)
    }

    private fun run(session: OrtSession, inputName: String, input: FloatBuffer, shape: LongArray): FloatBuffer =
        OnnxTensor.createTensor(env, input, shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                (result[0] as OnnxTensor).floatBuffer
            }
        }

    override fun close() {
        melSession.close()
        embeddingSession.close()
        wakeSession.close()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE = 1280

        // The melspectrogram model needs 3 hops of 160 samples of left context.
        private const val AUDIO_WINDOW = FRAME_SIZE + 160 * 3
        private const val MEL_BINS = 32
        private const val MEL_WINDOW = 76
        private const val MAX_MEL_ROWS = 970
        private const val EMBEDDING_SIZE = 96
        private const val MAX_EMBEDDINGS = 120
        private const val WAKE_WINDOW = 16

        // Direct, native-order buffers are wrapped by OnnxTensor without a copy.
        private fun directFloats(size: Int): FloatBuffer =
            ByteBuffer.allocateDirect(size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }
}
