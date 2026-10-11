package com.outsmartis.yoke.cockpit

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Speech to text for quick-add, with partial results. Uses the on-device recognizer when the
 * phone has one (Android 12+), else the phone's default recognition service. When neither is
 * there, [available] is false and the caller falls back to the system dialog ([dialogIntent]).
 * Callbacks arrive on the main thread.
 */
class Dictation(
    private val context: Context,
    private val onText: (text: String, final: Boolean) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (code: Int) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = false
    private var language: String = ""

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context) || onDeviceAvailable()

    private fun onDeviceAvailable() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(language: String) {
        this.language = language
        stop()
        start(preferOnDevice = onDeviceAvailable())
    }

    private fun start(preferOnDevice: Boolean) {
        recognizer?.destroy()
        onDevice = preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val r = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(listener)
        r.startListening(intent(language))
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onListening(true)
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            best(partialResults)?.let { onText(it, false) }
        }

        override fun onResults(results: Bundle?) {
            onListening(false)
            best(results)?.let { onText(it, true) }
        }

        override fun onError(error: Int) {
            // The on-device model may lack the language: try the phone's recognition service once.
            if (onDevice && error in LANGUAGE_ERRORS && SpeechRecognizer.isRecognitionAvailable(context)) {
                start(preferOnDevice = false)
                return
            }
            onListening(false)
            onError(error)
        }
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        /** ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE (API 31). */
        private val LANGUAGE_ERRORS = setOf(12, 13)

        fun intent(language: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (language.isNotEmpty()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        }

        /** The system's own dictation dialog, for phones without a recognition service Yoke can bind. */
        fun dialogIntent(language: String): Intent = intent(language)
    }
}
