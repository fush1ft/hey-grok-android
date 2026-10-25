package cz.fushift.heygrok

/** Describes assets/wakeword.onnx. Update these values when swapping in another model. */
object WakeModel {
    const val PHRASE = "Hey Grok"
    const val IS_PLACEHOLDER = false

    /** Picked from the evaluation in the README: about 0.2 false triggers per hour of background audio. */
    const val DEFAULT_THRESHOLD = 0.9f
}
