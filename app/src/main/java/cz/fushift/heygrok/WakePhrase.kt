package cz.fushift.heygrok

/** Wake models in assets/. Hey Grok's threshold comes from the README evaluation; 0.5 is openWakeWord's default. */
enum class WakePhrase(val label: String, val asset: String, val defaultThreshold: Float, val thresholdKey: String) {
    HEY_GROK("Hey Grok", "wakeword_hey_grok.onnx", 0.9f, "threshold_hey_grok"),
    HEY_JARVIS("Hey Jarvis", "wakeword_hey_jarvis.onnx", 0.5f, "threshold_hey_jarvis"),
    ;

    companion object {
        fun fromKey(key: String?): WakePhrase = entries.firstOrNull { it.name == key } ?: HEY_GROK
    }
}
