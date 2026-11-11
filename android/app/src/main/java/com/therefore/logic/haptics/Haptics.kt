package com.therefore.logic.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short vibration patterns for the puzzle screen. Respects the in-app setting. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    @Volatile var enabled = true

    private fun predefined(id: Int, fallbackMs: Long): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(id)
        else VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)

    private fun play(effect: () -> VibrationEffect) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        runCatching { v.vibrate(effect()) }
    }

    /** A keypad press. */
    fun key() = play { predefined(VibrationEffect.EFFECT_TICK, 8) }

    /** A line was accepted. */
    fun accept() = play { predefined(VibrationEffect.EFFECT_CLICK, 20) }

    /** A line was rejected: two firm buzzes. */
    fun reject() = play { VibrationEffect.createWaveform(longArrayOf(0, 45, 70, 45), intArrayOf(0, 200, 0, 200), -1) }

    /** A hint was shown. */
    fun hint() = play { predefined(VibrationEffect.EFFECT_DOUBLE_CLICK, 15) }

    /** The proof is complete: a rising flourish. */
    fun solved() = play {
        VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 40, 50, 90), intArrayOf(0, 90, 0, 160, 0, 255), -1)
    }
}
