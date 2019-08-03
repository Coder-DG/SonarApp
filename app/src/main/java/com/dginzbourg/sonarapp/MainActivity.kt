package com.dginzbourg.sonarapp

import android.media.AudioFormat
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.media.AudioTrack
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioFormat.CHANNEL_OUT_MONO
import android.media.AudioManager
import android.media.AudioFormat.ENCODING_PCM_8BIT
import android.widget.EditText


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
        // AudioTrack definition
        val mBufferSize = AudioTrack.getMinBufferSize(
            ONE_SECOND,
            CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_8BIT
        )

        val mAudioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            ONE_SECOND,
            CHANNEL_OUT_MONO,
            ENCODING_PCM_16BIT,
            mBufferSize, AudioTrack.MODE_STREAM
        )

        // Sine wave
        val mDuration = duration * ONE_SECOND
        val mSound = DoubleArray(Math.ceil(mDuration).toInt())
        val mBuffer = ShortArray(Math.ceil(mDuration).toInt())
        for (i in mSound.indices) {
            mSound[i] = Math.sin(2.0 * Math.PI * i.toDouble() / (ONE_SECOND / frequency))
            mBuffer[i] = (mSound[i] * java.lang.Short.MAX_VALUE).toShort()
        }

        mAudioTrack.setStereoVolume(AudioTrack.getMaxVolume(), AudioTrack.getMaxVolume())
        mAudioTrack.play()

        mAudioTrack.write(mBuffer, 0, mSound.size)
        mAudioTrack.stop()
        mAudioTrack.release()

    }

    companion object {
        const val ONE_SECOND = 44100
    }
}
