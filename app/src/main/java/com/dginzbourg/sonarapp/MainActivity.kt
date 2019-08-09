package com.dginzbourg.sonarapp

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.media.AudioTrack
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioFormat.CHANNEL_OUT_MONO
import android.widget.EditText
import kotlin.math.ceil
import kotlin.math.sin
import android.media.AudioAttributes
import android.media.AudioFormat




class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.button).setOnClickListener {
            val duration = findViewById<EditText>(R.id.duration).text.toString().toDouble()
            val frequency = findViewById<EditText>(R.id.frequency).text.toString().toDouble()
            playSound(frequency, duration)
        }
    }

    private fun playSound(frequency: Double, duration: Double) {
        val mBufferSize = AudioTrack.getMinBufferSize(
            DEFAULT_SAMPLE_RATE,
            CHANNEL_OUT_MONO,
            ENCODING_PCM_16BIT
        )
        val mAudioPlayer = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING_PCM_16BIT)
                    .setSampleRate(DEFAULT_SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(mBufferSize)
            .build()

        // Sine wave
        val mDuration = duration * ONE_SECOND
        val mSound = DoubleArray(ceil(mDuration).toInt())
        val mBuffer = ShortArray(ceil(mDuration).toInt())
        for (i in mSound.indices) {
            mSound[i] = sin(2.0 * Math.PI * i.toDouble() / (ONE_SECOND / frequency))
            mBuffer[i] = (mSound[i] * java.lang.Short.MAX_VALUE).toShort()
        }

        mAudioPlayer.setVolume(AudioTrack.getMaxVolume())
        mAudioPlayer.play()

        mAudioPlayer.write(mBuffer, 0, mSound.size)
        mAudioPlayer.stop()
        mAudioPlayer.release()

    }

    companion object {
        const val ONE_SECOND = 44100
        const val DEFAULT_SAMPLE_RATE = 44100
    }
}
