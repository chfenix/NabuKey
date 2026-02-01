package com.nabukey.audio

import android.util.Log

class SpeechDetector(
    private val threshold: Float = 0.5f,
    private val minSpeechDurationMs: Long = 60,
    private val minSilenceDurationMs: Long = 800
) {
    private var isSpeaking = false
    private var speechStartTime: Long = 0
    private var silenceStartTime: Long = 0
    
    // State to track if we have confirmed speech in the current session
    var hasDetectedSpeech = false 
        private set

    fun reset() {
        isSpeaking = false
        hasDetectedSpeech = false
        speechStartTime = 0
        silenceStartTime = 0
    }

    /**
     * Process a VAD probability.
     * @return Action to take: None, Start, or End
     */
    fun process(probability: Float): Action {
        val now = System.currentTimeMillis()

        if (probability >= threshold) {
            // Speech detected
            if (!isSpeaking) {
                // Potential start
                if (speechStartTime == 0L) {
                    speechStartTime = now
                } else if (now - speechStartTime >= minSpeechDurationMs) {
                    // Confirmed start
                    isSpeaking = true
                    hasDetectedSpeech = true
                    silenceStartTime = 0
                    return Action.START
                }
            } else {
                // Continuing speech, reset silence
                silenceStartTime = 0
            }
        } else {
            // Silence detected
            if (isSpeaking) {
                if (silenceStartTime == 0L) {
                    silenceStartTime = now
                } else if (now - silenceStartTime >= minSilenceDurationMs) {
                    // Confirmed end
                    isSpeaking = false
                    speechStartTime = 0
                    return Action.END
                }
            } else {
                // Was not speaking, reset start timer
                speechStartTime = 0
            }
        }
        return Action.NONE
    }

    enum class Action {
        NONE,
        START,
        END
    }
}
