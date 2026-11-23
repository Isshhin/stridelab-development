package io.stridelab.privatealpha

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.delay

internal enum class PlayerCue(val tone: Int, val milliseconds: Int, val haptic: Int) {
    Countdown(ToneGenerator.TONE_PROP_BEEP, 65, HapticFeedbackConstants.CLOCK_TICK),
    WorkStart(ToneGenerator.TONE_PROP_ACK, 115, HapticFeedbackConstants.VIRTUAL_KEY),
    WorkComplete(ToneGenerator.TONE_PROP_BEEP2, 145, HapticFeedbackConstants.VIRTUAL_KEY),
    RestComplete(ToneGenerator.TONE_PROP_ACK, 135, HapticFeedbackConstants.VIRTUAL_KEY)
}

internal class PlayerAudioFeedback(context: Context, private val view: View) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setOnAudioFocusChangeListener { }
        .build()
    private var generator: ToneGenerator? = null

    suspend fun play(cue: PlayerCue, muted: Boolean) {
        view.performHapticFeedback(cue.haptic)
        if (muted || manager?.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        try {
            val tone = generator ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull()
                ?.also { generator = it } ?: return
            if (tone.startTone(cue.tone, cue.milliseconds)) delay(cue.milliseconds.toLong())
        } finally {
            generator?.stopTone()
            manager.abandonAudioFocusRequest(request)
        }
    }

    fun release() {
        generator?.release()
        generator = null
        manager?.abandonAudioFocusRequest(request)
    }
}
