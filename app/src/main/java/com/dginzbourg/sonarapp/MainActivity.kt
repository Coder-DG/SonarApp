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
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock


class MainActivity : AppCompatActivity() {
    var lock: Lock = ReentrantLock()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.button).setOnClickListener {
            val duration = findViewById<EditText>(R.id.duration).text.toString().toDouble()
            val frequency = findViewById<EditText>(R.id.frequency).text.toString().toDouble()
            Thread {
                playSound(frequency, duration)
            }.start()
        }
    }

    private fun playSound(frequency: Double, duration: Double) {
        // Ignore other button calls during the time this plays sound
        if (!lock.tryLock()) {
            return
        }
        val mBufferSize = ceil(AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_OUT_MONO,
            ENCODING_PCM_16BIT
        ) / 2.0).toInt() // The size returned is in bytes, we use Shorts (2b each)
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
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(mBufferSize)
            .build()

        val mBuffer = ShortArray(ceil(duration * SAMPLE_RATE).toInt())
        for (i in mBuffer.indices) {
            mBuffer[i] = (
                    sin(frequency * 2 * Math.PI * i / SAMPLE_RATE) // This is the percentage of the max value
                            * java.lang.Short.MAX_VALUE).toShort()
        }

        mAudioPlayer.setVolume(AudioTrack.getMaxVolume())
        mAudioPlayer.play()

        mAudioPlayer.write(mBuffer, 0, mBuffer.size)
        mAudioPlayer.stop()
        mAudioPlayer.release()

        lock.unlock()

    }

    companion object {
        const val SAMPLE_RATE = 44100
    }
}
