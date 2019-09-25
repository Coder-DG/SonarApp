package com.dginzbourg.sonarapp

import android.Manifest
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Process
import android.speech.tts.TextToSpeech
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var mExecutor = Executors.newCachedThreadPool()
    private lateinit var mTempCalculator: TemperatureCalculator
    private val mListener = Listener()
    private val mTransmitter = Transmitter()
    private val mDistanceAnalyzer = DistanceAnalyzer()
    private val mNoiseFilter = NoiseFilter()
    private var mMLPClassifier = MutableLiveData<MLPClassifier>()
    private lateinit var mTTS: TextToSpeech
    private var mDistanceString = MutableLiveData<String>()
    private lateinit var mDistanceTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: make sure to not load anything if this is not granted
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1234)
        }

        setContentView(R.layout.activity_main)
        mTempCalculator = TemperatureCalculator(this)
        mTTS = TextToSpeech(this, this)
        TTSParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, TTS_VOLUME)
        initMLPClassifier()
        mDistanceTextView = findViewById(R.id.distanceTextView)
        mDistanceString.observe(this, object : Observer<String> {
            override fun onChanged(t: String?) {
                mDistanceTextView.text = t ?: return
            }
        })
        Log.d(LOG_TAG, "App started")
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e("TTS", "Initilization Failed!")
            return
        }
        val result = mTTS.setLanguage(Locale.getDefault())

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("TTS", "The Language specified is not supported!")
        }
    }

    private fun initMLPClassifier() {
        mExecutor.submit(
            SonarThread(Runnable {
                Log.d(LOG_TAG, "Loading MLP JSON files...")
                val weightsReader = JsonReader(InputStreamReader(resources.assets.open("MLPWeights.json")))
                val biasReader = JsonReader(InputStreamReader(resources.assets.open("MLPbias.json")))
                val json = Gson()
                val weights = json.fromJson<Array<Array<DoubleArray>>>(
                    weightsReader,
                    Array<Array<DoubleArray>>::class.java
                )
                val bias = json.fromJson<Array<DoubleArray>>(
                    biasReader,
                    Array<DoubleArray>::class.java
                )
                Log.d(LOG_TAG, "Building MLP instance...")
                val clf = MLPClassifier.buildClassifier(weights, bias)
                Log.d(LOG_TAG, "Done building MLP instance.")
                mMLPClassifier.postValue(clf)
            })
        )
    }


    override fun onResume() {
        if (mExecutor.isShutdown) mExecutor = Executors.newCachedThreadPool()
        mTransmitter.init()
        mListener.init()
        submitNextTransmissionCycle()
        super.onResume()
    }

    override fun onPause() {
        mExecutor.shutdownNow()
        mTransmitter.stop()
        mTransmitter.mAudioPlayer.release()
        mListener.stop()
        mListener.mAudioRecorder.release()
        mTTS.stop()
        super.onPause()
    }

    override fun onDestroy() {
        mTTS.shutdown()
        super.onDestroy()
    }

    private fun submitNextTransmissionCycle() {
        transmissionCycle++
        val transmissionCycle = SonarThread(Runnable {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (mMLPClassifier.value == null || mTTS.isSpeaking) {
                continue
            }

            mListener.mAudioRecorder.startRecording()
            mTransmitter.transmit()
            mListener.listen()
            Log.d(LOG_TAG, "Stopping transmission...")
            mTransmitter.stop()
            val correlation = mNoiseFilter.filterNoise(
                recordedBuffer = mListener.mRecorderBuffer,
                pulseBuffer = mTransmitter.mPlayerBuffer
            )
            if (correlation == null) {
                submitNextTransmissionCycle()
                return@Runnable
            }

            val soundSpeed = 331.3 + 0.606 * mTempCalculator.getTemp()
            val peaksPrediction = mDistanceAnalyzer.analyze(MAX_PEAK_DIST, MIN_PEAK_DIST, correlation, soundSpeed)
            if (peaksPrediction == null) {
                submitNextTransmissionCycle()
                return@Runnable
            }

            val paddedCorrelation = correlation.copyOf(MLP_CC_SIZE)
            val mlpPredictionClass: Int? = mMLPClassifier.value?.predict(paddedCorrelation)
            if (mlpPredictionClass == null) {
                showErrorMessage()
                return@Runnable
            }
            val mlpPrediction = mlpPredictionClass / 10.0

            val distanceString = "%.2f".format(0.1 * mlpPrediction + 0.9 * peaksPrediction)
            mDistanceString.postValue(distanceString)

            if (transmissionCycle % 3 == 0) {
                mTTS.speak(distanceString, TextToSpeech.QUEUE_FLUSH, TTSParams, "")
            }

            submitNextTransmissionCycle()
        })
        mExecutor.submit(transmissionCycle)
    }

    private fun showErrorMessage() {
        // TODO: "Please restart the app" or something.
    }

    companion object {
        // This was taken from the 'longest_cc' file at SonarApp_utils. This is the CC that the MLP accepts.
        const val MLP_CC_SIZE = 4346
        const val MIN_CHIRP_FREQ = 3000.0
        const val MAX_CHIRP_FREQ = MIN_CHIRP_FREQ
        const val CHIRP_DURATION = 0.01
        const val SAMPLE_RATE = 44100
        const val LOG_TAG = "sonar_app"
        // 10 meters
        const val MAX_PEAK_DIST = 2600
        // half chirp width
        val MIN_PEAK_DIST = (CHIRP_DURATION * SAMPLE_RATE * 0.5).roundToInt()
        val RECORDING_SAMPLES = (0.5 * SAMPLE_RATE).roundToInt()
        // Amount of samples to keep after chirp
        val RECORDING_CUT_OFF = (SAMPLE_RATE * (CHIRP_DURATION + 13.0 / DistanceAnalyzer.BASE_SOUND_SPEED)
                ).roundToInt() * 2
        var transmissionCycle = 0
        val TTSParams = Bundle()
        // TODO: make this customizable by the user
        const val TTS_VOLUME = 0.5f
    }
}