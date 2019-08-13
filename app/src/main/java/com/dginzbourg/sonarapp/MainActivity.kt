package com.dginzbourg.sonarapp

import android.Manifest
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.media.AudioTrack
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
import kotlin.math.*


class MainActivity : AppCompatActivity() {
    private var mDBLevel: MutableLiveData<Float> = MutableLiveData()
    private var executor: ExecutorService = Executors.newCachedThreadPool()
    private lateinit var mAudioPlayer: AudioTrack
    private lateinit var mPlayerBuffer: ShortArray
    private lateinit var mAudioRecorder: AudioRecord
    private lateinit var mRecorderBuffer: ShortArray
    private val mCyclicBarrier = CyclicBarrier(2) // 2 = transmitter and listener

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
        Log.d(LOG_TAG, "App started")
    }

    override fun onResume() {
        initTransmitter()
        initListener()
        submitNextTranmissionCycle()
        super.onResume()
    }

    override fun onPause() {
        executor.shutdownNow()
        mAudioRecorder.release()
        mAudioPlayer.release()
        super.onPause()
    }

    private fun initTransmitter() {
        Log.d(LOG_TAG, "Initializing the Transmitter...")
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
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(PLAYER_BUFFER_SIZE * 2) // This is in bytes and we use Short
            .build()

        // TODO: check that the audio player has been initialized properly

        mPlayerBuffer = ShortArray(PLAYER_BUFFER_SIZE)
        for (sampleIndex in mPlayerBuffer.indices) {
            mPlayerBuffer[sampleIndex] = (
                    sin(MAIN_FREQUENCY * 2 * Math.PI * sampleIndex / SAMPLE_RATE) // The percentage of the max value
                            * Short.MAX_VALUE).toShort()
        }

        applyFade(floor(mPlayerBuffer.size * FADE_PERCENT).toInt())

        mAudioPlayer.setVolume(AudioTrack.getMaxVolume())
    }

    private fun applyFade(framesToFade: Int) {
        var fadeFactor: Double
        for (i in 0..framesToFade) {
            fadeFactor = i.toDouble() / framesToFade
            mPlayerBuffer[i] = (mPlayerBuffer[i] * fadeFactor).toShort()
            mPlayerBuffer[mPlayerBuffer.size - i - 1] = (mPlayerBuffer[mPlayerBuffer.size - i - 1]
                    * fadeFactor).toShort()
        }
    }

    private fun initListener() {
        Log.d(LOG_TAG, "Initializing the Listener...")
        val bufferSize = RECORDING_SAMPLES.toInt()
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
            .setBufferSizeInBytes(bufferSize * 2)
            .build()
        if (mAudioRecorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Unable to init recorder")
        }
    }

    private fun transmit() {
        try {
            Log.d(LOG_TAG, "Waiting for the Listener")
            mCyclicBarrier.await()
        } catch (ex: InterruptedException) {
            Log.d(LOG_TAG, "Barrier interrupted inside the transmitter")
            return
        }
        Log.d(LOG_TAG, "Transmitting...")
        mAudioPlayer.write(mPlayerBuffer, 0, mPlayerBuffer.size)
        mAudioPlayer.play()
        while (mAudioPlayer.playState != AudioTrack.PLAYSTATE_STOPPED) {}
    }

    private fun listen() {
        try {
            Log.d(LOG_TAG, "Waiting for the Transmitter...")
            mCyclicBarrier.await()
        } catch (ex: InterruptedException) {
            Log.d(LOG_TAG, "Barrier inside the listener has been interrupted. Starting next cycle")
            submitNextTranmissionCycle()
            return
        }
        Log.d(LOG_TAG, "Listening...")
        mAudioRecorder.startRecording()
        mAudioRecorder.read(mRecorderBuffer, 0, mRecorderBuffer.size)
        submitAnalyzerTask()

        mAudioRecorder.stop()
    }

    private fun analyzeRecordings(recorderBuffer: ShortArray) {
        /* Currently this just updates a textview to show the dB meter
        *
        * The reference point that is set is the maximum volume the mic can detect, so it doesn't measure 'real' dB
        * levels.
        * */
        Log.d(LOG_TAG, "Analyzing data...")
        val doubleArray = recorderBuffer.map { it.toDouble().pow(2) }
        mDBLevel.postValue((10 * log10(doubleArray.average() / Short.MAX_VALUE)).toFloat())
    }

    private fun submitAnalyzerTask() {
        executor.submit(Thread { analyzeRecordings(mRecorderBuffer.copyOf()) })
    }

    private fun submitNextTranmissionCycle() {
        mCyclicBarrier.reset()
        val transmissionThread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            transmit()
        }
        executor.submit(transmissionThread)
        executor.submit(Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            listen()
            transmissionThread.join()
            mAudioPlayer.stop()
            submitNextTranmissionCycle()
        })
    }


    companion object {
        const val MAIN_FREQUENCY: Double = 20000.0
        const val SAMPLE_RATE = 44100
        const val LOG_TAG = "sonar_app"
        const val NULL = "NULL"
        const val RECORDING_SAMPLES = 0.5 * SAMPLE_RATE // 0.5sec of recordings
        val PLAYER_BUFFER_SIZE = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) / 2 // The size returned is in bytes, we use Shorts (2b each)
        // How much fade to apply to each side of the player buffer's data
        const val FADE_PERCENT = 0.05
    }
}
