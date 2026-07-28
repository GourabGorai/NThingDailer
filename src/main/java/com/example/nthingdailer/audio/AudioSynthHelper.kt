package com.example.nthingdailer.audio

import android.media.AudioManager
import android.media.ToneGenerator

object AudioSynthHelper {
    private var toneGen: ToneGenerator? = null
    var isSoundEnabled: Boolean = true

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playKeyTone(key: Char) {
        if (!isSoundEnabled) return
        val toneType = when (key) {
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '0' -> ToneGenerator.TONE_DTMF_0
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        try {
            toneGen?.startTone(toneType, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playBackspaceTone() {
        if (!isSoundEnabled) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 50)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playCallStartTone() {
        if (!isSoundEnabled) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_SUP_DIAL, 250)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playCallEndTone() {
        if (!isSoundEnabled) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_SUP_BUSY, 200)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
