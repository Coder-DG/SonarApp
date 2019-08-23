package com.dginzbourg.sonarapp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.floor
import kotlin.math.sin

class Transmitter {

    lateinit var mAudioPlayer: AudioTrack
    var mPlayerBuffer = ShortArray(MainActivity.PLAYER_BUFFER_SIZE)

    private fun applyFade(framesToFade: Int) {
        var fadeFactor: Double
        for (i in 0..framesToFade) {
            fadeFactor = i.toDouble() / framesToFade
            mPlayerBuffer[i] = (mPlayerBuffer[i] * fadeFactor).toShort()
            mPlayerBuffer[mPlayerBuffer.size - i - 1] = (mPlayerBuffer[mPlayerBuffer.size - i - 1]
                    * fadeFactor).toShort()
        }
    }

    fun transmit() {
        Log.d(MainActivity.LOG_TAG, "Transmitting...")
        mAudioPlayer.write(mPlayerBuffer, 0, mPlayerBuffer.size)
        mAudioPlayer.play()
        while (mAudioPlayer.playState != AudioTrack.PLAYSTATE_STOPPED) {
        }
    }

    fun init() {
        Log.d(MainActivity.LOG_TAG, "Initializing the Transmitter...")
        mAudioPlayer = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(MainActivity.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(MainActivity.PLAYER_BUFFER_SIZE * 2) // This is in bytes and we use Short
            .build()

        // TODO: check that the audio player has been initialized properly

        // TODO: pad the beginning of the buffer with enough 0's so it'll fit in the listener's recording
        for (sampleIndex in mPlayerBuffer.indices) {
            mPlayerBuffer[sampleIndex] = (
                    sin(MainActivity.MAIN_FREQUENCY * 2 * Math.PI * sampleIndex / MainActivity.SAMPLE_RATE) // The percentage of the max value
                            * Short.MAX_VALUE).toShort()
        }

        applyFade(floor(mPlayerBuffer.size * MainActivity.FADE_PERCENT).toInt())

        // TODO: check return value
        mAudioPlayer.setVolume(AudioTrack.getMaxVolume())
    }
}