package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque
import java.util.Locale

class VoiceService : Service(), RecognitionListener {
    companion object {
        var instance: VoiceService? = null
            private set
        private const val CHANNEL = "jarvis_voice"
        private const val NOTIFICATION_ID = 777

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

        private val _isListening = MutableStateFlow(false)
        val isListening: StateFlow<Boolean> = _isListening

        private val _lastSpeech = MutableStateFlow("")
        val lastSpeech: StateFlow<String> = _lastSpeech
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val queue = ArrayDeque<Step>()
    private val handler = Handler(Looper.getMainLooper())

    private val timeout = Runnable {
        if (_isListening.value) {
            _isListening.value = false
            recognizer?.cancel()
            say("Listening timed out. Tap Speak to retry.")
        }
    }

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            "Jarvis Voice Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active voice controller status and quick controls"
        }
        manager.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis Controller Active")
            .setContentText("Listening for phone voice commands. Tap to open controls.")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Assistant",
                stopIntent
            )
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            stopSelf()
            return
        }

        instance = this
        _isServiceRunning.value = true

        tts = TextToSpeech(this) { code ->
            ttsReady = (code == TextToSpeech.SUCCESS)
            if (ttsReady) {
                tts?.language = Locale.US
            }
        }

        initRecognizer()
        ControlService.instance?.showPanel()
    }

    private fun initRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                recognizer = if (Build.VERSION.SDK_INT >= 31 &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(this)
                }
                recognizer?.setRecognitionListener(this)
            }
        } catch (e: Exception) {
            recognizer = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun speak(message: String) {
        if (ttsReady) {
            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis_tts_${System.currentTimeMillis()}"
            )
        }
    }

    fun say(message: String) {
        ControlService.instance?.showStatus(message)
        speak(message)
    }

    fun cancel() {
        _isListening.value = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        tts?.stop()
        queue.clear()
        ControlService.instance?.clearConfirmation()
        ControlService.instance?.showStatus("Cancelled. Tap Speak.")
    }

    fun listen() {
        if (_isListening.value) return

        cancel()

        if (recognizer == null) {
            initRecognizer()
        }

        if (recognizer == null) {
            say("Speech recognition is not available on this device.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            _isListening.value = true
            ControlService.instance?.showStatus("Listening for command...")
            recognizer?.startListening(intent)
            handler.postDelayed(timeout, 15000)
        } catch (e: Exception) {
            _isListening.value = false
            say("Could not activate microphone recognition.")
        }
    }

    private fun offerNext(previous: String = "") {
        val controls = ControlService.instance ?: run {
            queue.clear()
            return
        }

        if (queue.isEmpty()) {
            say(previous.ifBlank { "Commands completed." })
            return
        }

        val step = queue.removeFirst()
        val expectedPackage = controls.screenPackage()

        val message = buildString {
            if (previous.isNotBlank()) {
                append(previous.take(120))
                append("\n\n")
            }
            append("Next: ")
            append(Parser.describe(step))
            append("\nVerify target, then tap Confirm.")
        }

        controls.requestConfirmation(message) {
            val screenDependent = step.action in listOf("tap", "type", "down", "up", "read")
            if (screenDependent && controls.screenPackage() != expectedPackage) {
                queue.clear()
                say("Screen changed. Please repeat command.")
            } else {
                val result = try {
                    controls.execute(step)
                } catch (e: Exception) {
                    Outcome(false, "Execution error occurred.")
                }

                if (!result.accepted) {
                    queue.clear()
                    say(result.message)
                } else if (step.action == "read") {
                    queue.clear()
                    say(result.message)
                } else if (queue.isEmpty()) {
                    say(result.message)
                } else {
                    controls.showStatus(result.message + "\nPreparing next step...")
                    handler.postDelayed({ offerNext(result.message) }, 1200)
                }
            }
        }

        speak("Verify next action and confirm.")
    }

    fun processSimulatedCommand(text: String) {
        _lastSpeech.value = text
        val steps = Parser.parse(text)
        if (steps == null) {
            say("Could not parse command. Try: open [app], tap [label], type [text], scroll down, or go back.")
            return
        }
        queue.clear()
        queue.addAll(steps)
        offerNext()
    }

    override fun onResults(results: Bundle?) {
        if (!_isListening.value) return
        _isListening.value = false
        handler.removeCallbacks(timeout)

        val heard = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()

        if (heard.isNullOrBlank()) {
            say("I did not catch that. Tap Speak to retry.")
            return
        }

        _lastSpeech.value = heard

        if (heard.equals("cancel", ignoreCase = true) || heard.equals("stop", ignoreCase = true)) {
            cancel()
            return
        }

        val steps = Parser.parse(heard)
        if (steps == null) {
            say("Command not recognized. Try open [app], tap [name], type [text], scroll, or read screen.")
            return
        }

        if (steps.size > 1 && steps.any { it.action == "read" }) {
            say("Please run 'read screen' as a standalone command.")
            return
        }

        queue.clear()
        queue.addAll(steps)
        offerNext()
    }

    override fun onError(error: Int) {
        handler.removeCallbacks(timeout)
        val wasListening = _isListening.value
        _isListening.value = false

        if (wasListening) {
            say("Speech recognition paused ($error). Tap Speak to retry.")
        }
    }

    override fun onDestroy() {
        _isListening.value = false
        _isServiceRunning.value = false
        handler.removeCallbacksAndMessages(null)
        queue.clear()
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        ControlService.instance?.hidePanel()
        if (instance === this) instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
