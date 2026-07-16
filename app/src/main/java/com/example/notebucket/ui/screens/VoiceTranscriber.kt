package com.example.notebucket.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceTranscriber(context: Context) {

    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var isListening = false

    fun startListening(
        onResult: (String) -> Unit,
        onPartial: ((String) -> Unit)? = null,
        onEndOfSpeech: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (isListening) return
        isListening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, onPartial != null)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech")
            }
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
                isListening = false
                onEndOfSpeech?.invoke()
            }
            override fun onError(error: Int) {
                val msg = errorToString(error)
                Log.e(TAG, "onError: $error ($msg)")
                isListening = false
                onError?.invoke(msg)
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                Log.d(TAG, "onResults: '$text'")
                if (text.isNotBlank()) onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) onPartial?.invoke(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    fun stopListening() {
        if (isListening) {
            recognizer.stopListening()
            isListening = false
        }
    }

    fun destroy() {
        stopListening()
        recognizer.destroy()
    }

    private fun errorToString(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. Try again."
        SpeechRecognizer.ERROR_CLIENT -> "Recognition client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission."
        SpeechRecognizer.ERROR_NETWORK -> "Network error. Check connection."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy."
        SpeechRecognizer.ERROR_SERVER -> "Server error."
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        else -> "Voice input failed (error $error)."
    }

    companion object {
        private const val TAG = "VoiceTranscriber"

        fun isAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context)
    }
}
