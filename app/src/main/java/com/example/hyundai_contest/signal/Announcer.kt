package com.example.hyundai_contest.signal

import android.content.Context
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

/** Speaks short factual reminders: Pleos on-device TTS first, the platform TTS if Pleos is unavailable. */
class Announcer(private val context: Context) {
    private var pleos: TextToSpeech? = null
    @Volatile private var pleosReady = false
    private var platform: android.speech.tts.TextToSpeech? = null
    @Volatile private var platformReady = false
    @Volatile private var pending: String? = null
    @Volatile private var lastText: String? = null

    init {
        runCatching {
            val tts = TextToSpeech(context, Mode.ON_DEVICE)
            pleos = tts
            val events = object : EventListener {
                override fun onStart() = Unit
                override fun onReady() = Unit
                override fun onDone() = Unit
                override fun onStop() = Unit
                override fun onUpdatedRms(rms: Double) = Unit
                override fun onError(error: ErrorType) {
                    Log.w(TAG, "pleos tts error $error, switching to platform tts")
                    pleosReady = false
                    pending = lastText
                    startPlatform()
                }
            }
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.addEventListener(events)
                    tts.setLocale(Locale.KOREAN)
                    pleosReady = true
                    pending?.let { pending = null; say(it) }
                }
                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "pleos tts init failed $error")
                    startPlatform()
                }
            })
        }.onFailure {
            Log.w(TAG, "pleos tts unavailable: ${it.message}")
            startPlatform()
        }
    }

    private fun startPlatform() {
        if (platform != null) return
        platform = android.speech.tts.TextToSpeech(context) { status ->
            platformReady = status == android.speech.tts.TextToSpeech.SUCCESS
            if (platformReady) {
                platform?.language = Locale.KOREAN
                pending?.let { pending = null; say(it) }
            }
        }
    }

    fun say(text: String) {
        Log.i(TAG, "announce: $text")
        lastText = text
        when {
            pleosReady -> pleos?.speak(text, true)
            platformReady -> platform?.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, text)
            else -> pending = text
        }
    }

    fun release() {
        pleos?.release()
        platform?.shutdown()
    }
}
