package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Dedicated Turn-by-Turn Voice Navigation Engine for Mera Thikaana.
 * Provides high-priority spoken directions (e.g. "In 200 meters, turn left onto Main Road",
 * "Ab turn right onto Circular Road", "Yaar, you've arrived at Dassam Falls") with
 * audio focus ducking, Indian English (en-IN) / Hindi (hi-IN) voice tuning,
 * and haptic feedback alerts for turns.
 */
class NavigationVoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "NavVoiceManager"
        private const val UTTERANCE_NAV_TURN = "utterance_nav_turn"
    }

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _isNavActive = MutableStateFlow(false)
    val isNavActive: StateFlow<Boolean> = _isNavActive.asStateFlow()

    private val _destinationName = MutableStateFlow("")
    val destinationName: StateFlow<String> = _destinationName.asStateFlow()

    private val _currentInstruction = MutableStateFlow("")
    val currentInstruction: StateFlow<String> = _currentInstruction.asStateFlow()

    private val _distance = MutableStateFlow("")
    val distance: StateFlow<String> = _distance.asStateFlow()

    private val _nextInstruction = MutableStateFlow("")
    val nextInstruction: StateFlow<String> = _nextInstruction.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _lastSpokenText = MutableStateFlow("")
    val lastSpokenText: StateFlow<String> = _lastSpokenText.asStateFlow()

    private var lastSpokenTimestamp = 0L

    init {
        initializeTts()
    }

    private fun initializeTts() {
        try {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.let { tts ->
                        // Configure high-priority Navigation Guidance Audio Attributes
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts.setAudioAttributes(audioAttributes)

                        // Select Indian-English (en-IN) voice for best pronunciation of Hinglish directions
                        var langSet = false
                        val inLocale = Locale("en", "IN")
                        val checkIn = tts.isLanguageAvailable(inLocale)
                        if (checkIn >= TextToSpeech.LANG_AVAILABLE) {
                            tts.language = inLocale
                            langSet = true
                        } else {
                            val hiLocale = Locale("hi", "IN")
                            if (tts.isLanguageAvailable(hiLocale) >= TextToSpeech.LANG_AVAILABLE) {
                                tts.language = hiLocale
                                langSet = true
                            }
                        }

                        if (!langSet) {
                            tts.language = Locale.getDefault()
                        }

                        // Try finding an en-IN voice in the installed voice list
                        try {
                            val voices = tts.voices
                            if (!voices.isNullOrEmpty()) {
                                val match = voices.find { v ->
                                    v.locale.country.equals("IN", ignoreCase = true) ||
                                            v.locale.language.equals("en", ignoreCase = true) && v.locale.country.equals("IN", ignoreCase = true)
                                } ?: voices.find { it.locale.language.equals("hi", ignoreCase = true) }
                                if (match != null) {
                                    tts.voice = match
                                }
                            }
                        } catch (_: Exception) {}

                        tts.setPitch(1.0f)
                        tts.setSpeechRate(0.98f) // Clear, measured navigation pace

                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _isSpeaking.value = true
                            }

                            override fun onDone(utteranceId: String?) {
                                _isSpeaking.value = false
                            }

                            override fun onError(utteranceId: String?) {
                                _isSpeaking.value = false
                            }
                        })

                        isTtsReady = true
                        Log.i(TAG, "Navigation Voice TTS ready with language ${tts.language}")
                    }
                } else {
                    Log.w(TAG, "Navigation Voice TTS init failed with code $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize navigation TTS", e)
        }
    }

    /**
     * Speaks a turn-by-turn instruction out loud (e.g. "Ab turn left onto Main Road", "In 200m, turn right").
     * Avoids rapid-fire identical speech within a short window unless forced.
     */
    fun speakNavInstruction(instruction: String, dist: String = "", force: Boolean = false) {
        val cleanInstr = instruction.trim()
        if (cleanInstr.isBlank() || _isMuted.value) return

        val now = System.currentTimeMillis()
        val speechText = formatInstructionForSpeech(cleanInstr, dist)

        if (!force && speechText.equals(_lastSpokenText.value, ignoreCase = true) && (now - lastSpokenTimestamp) < 4000L) {
            // Skip immediate duplicate
            return
        }

        lastSpokenTimestamp = now
        _lastSpokenText.value = speechText

        // Provide tactile haptic feedback for upcoming turns
        triggerManeuverHaptic(cleanInstr)

        if (isTtsReady && textToSpeech != null) {
            try {
                _isSpeaking.value = true
                textToSpeech?.speak(
                    speechText,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "$UTTERANCE_NAV_TURN-$now"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking navigation instruction", e)
                _isSpeaking.value = false
            }
        }
    }

    /**
     * Repeat the last instruction currently on screen or spoken.
     */
    fun repeatCurrentInstruction() {
        val instr = _currentInstruction.value.ifBlank { _lastSpokenText.value }
        if (instr.isNotBlank()) {
            speakNavInstruction(instr, _distance.value, force = true)
        }
    }

    /**
     * Formats raw navigation step text into natural spoken guidance.
     */
    private fun formatInstructionForSpeech(instruction: String, dist: String): String {
        var text = instruction

        // Expand common abbreviations for natural speech
        text = text.replace("m", " meters", ignoreCase = true)
            .replace("km", " kilometers", ignoreCase = true)
            .replace("Rd", "Road")
            .replace("St", "Street")
            .replace("Chowk", "Chowk")
            .replace("Marg", "Marg")

        // If distance is provided and not already included in the instruction text
        if (dist.isNotBlank() && !text.contains(dist, ignoreCase = true) && !text.startsWith("In ", ignoreCase = true)) {
            val spokenDist = dist.replace("m", " meters").replace("km", " kilometers")
            return "In $spokenDist, $text"
        }

        return text
    }

    /**
     * Generates a distinct double-tap haptic pulse on turns to notify driver/walker.
     */
    private fun triggerManeuverHaptic(instruction: String) {
        val lower = instruction.lowercase()
        val isTurn = lower.contains("turn") || lower.contains("left") ||
                lower.contains("right") || lower.contains("exit") ||
                lower.contains("fork") || lower.contains("roundabout") ||
                lower.contains("merge")

        if (isTurn && vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 60, 50, 70)
                    val amplitudes = intArrayOf(0, 180, 0, 240)
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
            } catch (_: Exception) {}
        }
    }

    fun updateNavState(
        isActive: Boolean,
        destinationName: String,
        currentInstruction: String,
        distance: String,
        nextInstruction: String
    ) {
        _isNavActive.value = isActive
        if (destinationName.isNotBlank()) {
            _destinationName.value = destinationName
        }
        if (currentInstruction.isNotBlank()) {
            _currentInstruction.value = currentInstruction
        }
        _distance.value = distance
        _nextInstruction.value = nextInstruction

        if (!isActive) {
            stopSpeaking()
        }
    }

    fun toggleMute(): Boolean {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        if (newMuted) {
            stopSpeaking()
        } else {
            // Unmuted: repeat current instruction to confirm voice is active
            repeatCurrentInstruction()
        }
        return newMuted
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) {
            stopSpeaking()
        }
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    fun destroy() {
        stopSpeaking()
        try {
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
    }
}
