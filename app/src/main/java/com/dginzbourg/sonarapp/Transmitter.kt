package com.dginzbourg.sonarapp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.*

class Transmitter {

    companion object {
        val PLAYER_BUFFER_SIZE = AudioTrack.getMinBufferSize(
            MainActivity.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) / 2 // The size returned is in bytes, we use Shorts (2b each)
    }

    lateinit var mAudioPlayer: AudioTrack
    // t1 < 1 second
    //var mPlayerBuffer = ShortArray(MainActivity.SAMPLE_RATE)
    var mPlayerBuffer = ShortArray(16384)

    fun transmit() {
        Log.d(MainActivity.LOG_TAG, "Transmitting ${mPlayerBuffer.size} samples...")
        mAudioPlayer.write(mPlayerBuffer, 0, mPlayerBuffer.size)
        mAudioPlayer.play()
        Log.d(MainActivity.LOG_TAG, "Sent transmission task.")
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
            .setBufferSizeInBytes(PLAYER_BUFFER_SIZE * 2) // This is in bytes and we use Short
            .build()

        // TODO: check that the audio player has been initialized properly, else notify the user

        val numSamples = (MainActivity.CHIRP_DURATION * MainActivity.SAMPLE_RATE).roundToInt()
        mPlayerBuffer = convertToShort(
            hanningWindow(
                chirp(
                    MainActivity.MIN_CHIRP_FREQ,
                    MainActivity.MAX_CHIRP_FREQ, MainActivity.CHIRP_DURATION, MainActivity.SAMPLE_RATE.toDouble()
                ), numSamples
            )
        )
        mAudioPlayer.setVolume(AudioTrack.getMaxVolume())
    }

    private fun chirp(f0: Double, f1: Double, t1: Double, samplingFreq: Double): DoubleArray {
        val k = (f1 - f0) / t1
        val samples = ceil(t1 * samplingFreq).toInt() + 1
        val chirp = DoubleArray(samples)
        val inc = 1 / samplingFreq
        var t = 0.0
        for (index in chirp.indices) {
            if (t <= t1) {
                chirp[index] = sin(2.0 * PI * (f0 * t + t.pow(2.0) * k / 2))
                t += inc
            }
        }

        return chirp
    }

    private fun hanningWindow(signal_in: DoubleArray, size: Int): DoubleArray {
        for (i in 0 until size) {
            signal_in[i] = signal_in[i] * 0.5 * (1.0 - cos(2.0 * PI * i.toDouble() / size))
        }
        return signal_in
    }

    private fun padSignal(signal: DoubleArray, length: Int, position: Int): DoubleArray {
        val newSignal = DoubleArray(length)
        val siglen = signal.size
        for (i in 0 until siglen) {
            newSignal[i + position] = signal[i]
        }
        return newSignal

    }

    private fun convertToShort(signal_in: DoubleArray): ShortArray {
        val generatedSnd = ShortArray(signal_in.size)
        for ((idx, dVal) in signal_in.withIndex()) {
            val `val` = (dVal * 32767).toShort()
            generatedSnd[idx] = `val`
        }
        return generatedSnd
    }
}