package com.dginzbourg.sonarapp

import android.Manifest
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.media.AudioTrack
import android.widget.EditText
import kotlin.math.ceil
import kotlin.math.sin
import android.media.AudioAttributes
import android.media.AudioFormat
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import android.media.AudioRecord
import android.media.MediaRecorder
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.TextView
import kotlin.math.log10
import kotlin.math.pow


class MainActivity : AppCompatActivity() {
    private var mPlaySoundLock: Lock = ReentrantLock()
    private var mShouldContinue: Boolean = false
    private var mDBLevel: MutableLiveData<Float> = MutableLiveData()
    private lateinit var mRecordButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 123)
        }

        setContentView(R.layout.activity_main)
        val mDBLevelView = findViewById<TextView>(R.id.db_level)
        mDBLevel.observe(this, Observer<Float> {
            if (it == null){
                mDBLevelView.text = NULL
            }
            val text = "%.2f".format(it)
            mDBLevelView.text = text
        })
        findViewById<Button>(R.id.play_sound_button).setOnClickListener {
            val duration = findViewById<EditText>(R.id.duration).text.toString().toDouble()
            val frequency = findViewById<EditText>(R.id.frequency).text.toString().toDouble()
            Thread {
                playSound(frequency, duration)
            }.start()
        }
        mRecordButton = findViewById(R.id.record_button)
        mRecordButton.setOnClickListener {
            when (mShouldContinue) {
                false -> {
                    mRecordButton.text = STOP_RECORDING
                    mShouldContinue = true
                    Thread {
                        record()
                    }.start()
                }
                else -> {
                    stopRecording()
                }
            }
        }
    }

    private fun stopRecording() {
        mRecordButton.text = START_RECORDING
        mShouldContinue = false
    }

    private fun playSound(frequency: Double, duration: Double) {
        // Ignore other button calls during the time this plays sound
        if (!mPlaySoundLock.tryLock()) {
            return
        }
        val mBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) / 2// The size returned is in bytes, we use Shorts (2b each)
        val mAudioPlayer = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(mBufferSize)
            .build()

        val mBuffer = ShortArray(ceil(duration * SAMPLE_RATE).toInt())
        for (i in mBuffer.indices) {
            mBuffer[i] = (
                    sin(frequency * 2 * Math.PI * i / SAMPLE_RATE) // This is the percentage of the max value
                            * Short.MAX_VALUE).toShort()
        }

        mAudioPlayer.setVolume(AudioTrack.getMaxVolume())
        mAudioPlayer.play()

        mAudioPlayer.write(mBuffer, 0, mBuffer.size)
        mAudioPlayer.stop()
        mAudioPlayer.release()

        mPlaySoundLock.unlock()

    }

    private fun record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioBuffer = ShortArray(bufferSize / 2)
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Unable to init recorder")
            return
        }
        record.startRecording()


        while (mShouldContinue) {
            record.read(audioBuffer, 0, audioBuffer.size)
            updateAvgDB(audioBuffer)
        }

        record.stop()
        record.release()

    }

    private fun updateAvgDB(shortArray: ShortArray) {
        val doubleArray = shortArray.map { it.toDouble().pow(2) / Short.MAX_VALUE }
        mDBLevel.postValue((10 * log10(doubleArray.average())).toFloat())
    }


    override fun onPause() {
        stopRecording()
        super.onPause()
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val LOG_TAG = "sonar_app"
        const val STOP_RECORDING = "Stop Recording"
        const val START_RECORDING = "Start Recording"
        const val NULL = "NULL"
    }
}
