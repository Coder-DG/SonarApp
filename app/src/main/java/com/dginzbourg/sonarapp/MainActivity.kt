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
import org.jtransforms.fft.DoubleFFT_1D
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.*


class MainActivity : AppCompatActivity() {
    private var mSONARAmplitude: MutableLiveData<Float> = MutableLiveData()
    private var executor: ExecutorService = Executors.newCachedThreadPool()
    private lateinit var mAudioPlayer: AudioTrack
    private lateinit var mPlayerBuffer: ShortArray
    private lateinit var mAudioRecorder: AudioRecord
    private lateinit var mRecorderBuffer: ShortArray
    private var mAnalyzerBuffer = DoubleArray(FFT_BUFFER_SIZE)
    private var mSONARDataBuffer = DoubleArray(SONAR_DATA_BUFFER_SIZE)
    private var mFFT = DoubleFFT_1D(WINDOW_SIZE.toLong())
    private val mCyclicBarrier = CyclicBarrier(2) // 2 = transmitter and listener
    private val mAnalyzingLock = ReentrantLock()

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
        val mSONARAMplitudeView = findViewById<TextView>(R.id.sonar_amp_level)
        mSONARAmplitude.observe(this, Observer<Float> {
            if (it == null) {
                mSONARAMplitudeView.text = NULL
            }
            val text = "%.2f".format(it)
            mSONARAMplitudeView.text = text
        })
        Log.d(LOG_TAG, "App started")
    }

    override fun onResume() {
        initTransmitter()
        initListener()
        submitNextTransmissionCycle()
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
        while (mAudioPlayer.playState != AudioTrack.PLAYSTATE_STOPPED) {
        }
    }

    private fun listen() {
        try {
            Log.d(LOG_TAG, "Waiting for the Transmitter...")
            mCyclicBarrier.await()
        } catch (ex: InterruptedException) {
            Log.d(LOG_TAG, "Barrier inside the listener has been interrupted. Starting next cycle")
            submitNextTransmissionCycle()
            return
        }
        Log.d(LOG_TAG, "Listening...")
        mAudioRecorder.startRecording()
        mAudioRecorder.read(mRecorderBuffer, 0, mRecorderBuffer.size)
        submitAnalyzerTask()

        mAudioRecorder.stop()
    }

    private fun analyzeRecordings(recorderBuffer: ShortArray) {
        /* Currently this just gets the 20KHz amplitude over the transmission time.
        *
        * TODO: do the analysis in an orderly fashion. tryLock is not starvation free. This way we'll be able to give
        *  the user the relevant distances. This can be dealt with after finishing the FFT milestone.
        * */
        // Only one thread at a time is allowed to use the buffer. This also simplifies how we deliver the relevant
        // distance to the user (because there's only one analysis running at a time).
        mAnalyzingLock.tryLock()
        Log.d(LOG_TAG, "Analyzing data...")

        for (i in mSONARDataBuffer.indices) {
            val startPos = i * WINDOW_OVERLAP_EXTERIOR.toInt()
            val endPos = i * WINDOW_OVERLAP_EXTERIOR.toInt() + WINDOW_SIZE
            for (indexInWindow in startPos..endPos) {
                // Normalize data and copy to mAnalyzerBuffer
                mAnalyzerBuffer[indexInWindow - startPos] = recorderBuffer[indexInWindow] / Short.MAX_VALUE.toDouble()
            }
            mFFT.realForwardFull(mAnalyzerBuffer)
            /* There are 1024 frequency buckets, we need the one where the main frequency resides. Not sure about the
            * /2.0 but when we measure with a sample rate of X then the max frequency we can measure is 0.5X. I hope
            * this is the right calculation */
            mSONARDataBuffer[i] = mAnalyzerBuffer[(MAIN_FREQUENCY / (SAMPLE_RATE / 2.0 / WINDOW_SIZE)).toInt()]
        }
//        mSONARAmplitude.postValue(100*m)
        mAnalyzingLock.unlock()
    }

    private fun submitAnalyzerTask() {
        executor.submit(Thread { analyzeRecordings(mRecorderBuffer.copyOf()) })
    }

    private fun submitNextTransmissionCycle() {
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
            submitNextTransmissionCycle()
        })
    }


    private companion object {
        const val MAIN_FREQUENCY: Double = 20000.0
        const val SAMPLE_RATE = 44100
        const val LOG_TAG = "sonar_app"
        const val NULL = "NULL"
        // 0.5sec of recordings. Can't be too little (you'll get an error). Has to be at least WINDOW_SIZE samples
        const val RECORDING_SAMPLES = 0.5 * SAMPLE_RATE
        val PLAYER_BUFFER_SIZE = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) / 2 // The size returned is in bytes, we use Shorts (2b each)
        // How much fade to apply to each side of the player buffer's data
        const val FADE_PERCENT = 0.05
        const val WINDOW_SIZE = 1024
        val WINDOW_OVERLAP = floor(0.5 * WINDOW_SIZE)
        val WINDOW_OVERLAP_EXTERIOR = WINDOW_SIZE - WINDOW_OVERLAP
        const val FFT_BUFFER_SIZE = 2 * WINDOW_SIZE
        /* This amount represents the maximum amount of samples we'd analyse.
        * 0.03 = time it takes for sound to travel 10.29m in air that is 20c degrees hot. That's our threshold. */
        val LISTENING_SAMPLES_THRESHOLD = min(ceil(SAMPLE_RATE * 0.03).toInt(), RECORDING_SAMPLES.toInt())
        val SONAR_DATA_BUFFER_SIZE = floor(
            (LISTENING_SAMPLES_THRESHOLD - 1) / WINDOW_OVERLAP_EXTERIOR
        ).toInt()
    }
}
