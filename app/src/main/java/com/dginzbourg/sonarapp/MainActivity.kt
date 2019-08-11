package com.dginzbourg.sonarapp

import android.Manifest
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.media.AudioTrack
import kotlin.math.ceil
import kotlin.math.sin
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.TextView
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.log10
import kotlin.math.pow


class MainActivity : AppCompatActivity() {
    private var mDBLevel: MutableLiveData<Float> = MutableLiveData()
    private var executor: ExecutorService = Executors.newCachedThreadPool()
    private lateinit var mAudioPlayer: AudioTrack
    private lateinit var mPlayerBuffer: ShortArray
    private lateinit var mAudioRecorder: AudioRecord
    private lateinit var mRecorderBuffer: ShortArray
    private val mCyclicBarrier = CyclicBarrier(2) // Transmitter and listener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1234)
        }

        setContentView(R.layout.activity_main)
        val mDBLevelView = findViewById<TextView>(R.id.db_level)
        mDBLevel.observe(this, Observer<Float> {
            if (it == null) {
                mDBLevelView.text = NULL
            }
            val text = "%.2f".format(it)
            mDBLevelView.text = text
        })
        submitNextTransmissionTasks()
    }

    override fun onResume() {
        initTransmitter()
        initListener()
        super.onResume()
    }

    override fun onPause() {
        executor.shutdownNow()
        super.onPause()
    }

    private fun initTransmitter() {
//        val mBufferSize = AudioTrack.getMinBufferSize(
//            SAMPLE_RATE,
//            AudioFormat.CHANNEL_OUT_MONO,
//            AudioFormat.ENCODING_PCM_16BIT
//        ) / 2// The size returned is in bytes, we use Shorts (2b each)
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
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(PLAYBACK_BUFFER_SIZE)
            .build()

        mPlayerBuffer = ShortArray(PLAYBACK_BUFFER_SIZE)
        for (sampleIndex in mPlayerBuffer.indices) {
            mPlayerBuffer[sampleIndex] = (
                    sin(MAIN_FREQUENCY * 2 * Math.PI * sampleIndex / SAMPLE_RATE) // The percentage of the max value
                            * Short.MAX_VALUE).toShort()
        }

        mAudioPlayer.setVolume(AudioTrack.getMaxVolume())
    }

    private fun initListener() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val bufferSize = RECORDING_SAMPLES
        mRecorderBuffer = ShortArray(bufferSize)
        mAudioRecorder = AudioRecord.Builder()
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
    }

    private fun transmit() {
        mAudioPlayer.play()

        try {
            mCyclicBarrier.await()
        } catch (ex: InterruptedException) {
            return
        }

        mAudioPlayer.write(mPlayerBuffer, 0, mPlayerBuffer.size)
        mAudioPlayer.stop()
        mAudioPlayer.release()
    }

    private fun listen() {
        if (mAudioRecorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Unable to init recorder")
            return
        }
        try {
            mCyclicBarrier.await()
        } catch (ex: InterruptedException) {
            submitNextTransmissionTasks()
            return
        }

        mAudioRecorder.startRecording()
        mAudioRecorder.read(mRecorderBuffer, 0, mRecorderBuffer.size)
        submitAnalyzerTask()

        mAudioRecorder.stop()
        mAudioRecorder.release()
        mCyclicBarrier.reset()

        submitNextTransmissionTasks()
    }

    private fun submitAnalyzerTask() {
        executor.submit(Thread { updateAvgDB(mRecorderBuffer.copyOf()) })
    }

    private fun submitNextTransmissionTasks() {
        mCyclicBarrier.reset()
        executor.submit(Thread { transmit() })
        executor.submit(Thread { listen() })
    }

    private fun updateAvgDB(recorderBuffer: ShortArray) {
        val doubleArray = recorderBuffer.map { it.toDouble().pow(2) / Short.MAX_VALUE }
        mDBLevel.postValue((10 * log10(doubleArray.average())).toFloat())
    }


    companion object {
        const val MAIN_FREQUENCY: Double = 20000.0
        const val SAMPLE_RATE = 44100
        val PLAYBACK_BUFFER_SIZE = ceil(SAMPLE_RATE / MAIN_FREQUENCY).toInt()
        const val LOG_TAG = "sonar_app"
        const val NULL = "NULL"
        const val RECORDING_SAMPLES = 2 * SAMPLE_RATE // 2 seconds of recordings
    }
}
