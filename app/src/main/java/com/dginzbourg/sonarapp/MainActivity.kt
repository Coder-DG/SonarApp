package com.dginzbourg.sonarapp

import android.Manifest
import android.app.AlertDialog
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Process
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.*
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v4.view.GestureDetectorCompat
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button


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
    private var mTTSInitialized = false
    private val mTTSParams = Bundle()
    private var mPermissionsDialog: AlertDialog? = null
    private lateinit var mDetector: GestureDetectorCompat
    private lateinit var mTTSVolumeSeekBarTitle: TextView
    private var mTransmissionShouldStart = false
    private lateinit var mDistanceTitleTextView: TextView
    private lateinit var mMetersTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mDistanceTitleTextView = findViewById(R.id.distanceTitleTextView)
        mMetersTextView = findViewById(R.id.metersTextView)
        loadPreferences()
        mTempCalculator = TemperatureCalculator(this)
        mTTS = TextToSpeech(this, this)
        mDistanceTextView = findViewById(R.id.distanceTextView)
        mDistanceString.observe(this, object : Observer<String> {
            override fun onChanged(t: String?) {
                if (mTransmissionShouldStart) mDistanceTextView.text = t ?: return
            }
        })
        findViewById<View>(R.id.fab).setOnClickListener {
            speak(R.string.help_msg, addToQueue = false)
        }

        setFont()
        mTTSVolumeSeekBarTitle = findViewById(R.id.ttsVolumeSeekBarTitle)
        val text = getString(R.string.tts_volume_seekbar_title) + " (${stringPercent(getTTSVolume())}%)"
        mTTSVolumeSeekBarTitle.text = text
        mDetector = GestureDetectorCompat(this, MyGestureListener())

        setDistanceStoppedState()
        findViewById<Button>(R.id.mainStartStopButton).apply {
            setOnClickListener {
                mTransmissionShouldStart = !mTransmissionShouldStart
                this.text = if (mTransmissionShouldStart) getString(R.string.stop) else getString(R.string.start)
                refreshAppState()
            }
        }

        Log.d("onCreate", "App started")
    }

    private fun setFont() {
        val font = Typeface.createFromAsset(assets, FONT_PATH)
        findViewById<TextView>(R.id.distanceTitleTextView).typeface = font
        findViewById<TextView>(R.id.ttsVolumeSeekBarTitle).typeface = font
        findViewById<TextView>(R.id.metersTextView).typeface = font
    }

    private fun stringPercent(float: Float) = (float * 100).roundToInt()

    private fun speak(@StringRes resId: Int, addToQueue: Boolean = true): Int {
        return speak(getString(resId), addToQueue)
    }

    private fun setDistanceStoppedState() {
        mDistanceTextView.text = getString(R.string.distance_text_view_start_text)
        mDistanceTitleTextView.visibility = View.GONE
        mMetersTextView.visibility = View.GONE
    }

    private fun refreshAppState() {
        if (mTransmissionShouldStart) {
            setDistanceTextStartedState()
            initAndStartTransmissionCycle()
        } else {
            mExecutor.shutdownNow()
            setDistanceStoppedState()
        }
    }

    private fun setDistanceTextStartedState() {
        mDistanceTextView.text = getString(R.string.loading)
        mDistanceTitleTextView.visibility = View.VISIBLE
        mMetersTextView.visibility = View.VISIBLE
    }

    private fun speak(string: String, addToQueue: Boolean = true): Int {
        val queueMode = if (addToQueue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        return mTTS.speak(string, queueMode, mTTSParams, "")
    }

    private fun changeTTSVolumeBy(delta: Float) {
        Log.d("changeTTSVolumeBy", "$delta")
        val newTTSVolume = getTTSVolume() + delta
        val ttsVolume = when {
            delta > 0 -> min(1f, newTTSVolume)
            else -> max(0f, newTTSVolume)
        }
        val textHolder =
            getString(R.string.tts_volume_seekbar_title) + " (${stringPercent(ttsVolume)}%)"
        mTTSVolumeSeekBarTitle.text = textHolder
        setTTSVolume(ttsVolume)
        speak(textHolder, addToQueue = false)
    }

    private fun setTTSVolume(volume: Float) {
        mTTSParams.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
    }

    private fun getTTSVolume() = mTTSParams.getFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, TTS_INITIAL_VOLUME)

    private fun checkMediaVolume() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (musicVolume == musicMaxVolume) return

        Snackbar.make(
            findViewById(R.id.mainCoordinatorLayout),
            R.string.increase_volume_snackbar_txt,
            Snackbar.LENGTH_LONG
        ).show()
        mExecutor.submit(SonarThread(Runnable {
            while (!mTTSInitialized) continue
            speak(R.string.increase_volume_snackbar_txt)
        }))
    }

    private fun validatePermissionsGranted(): Boolean {
        Log.d("validatePermissionsGran", "Called")
        for (permission in permissionArray) {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) continue

            Log.d("permissions request", "Requested permission $permission")
            requestPermissions(permissionArray, 123)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.d("onRequestPermission", "Called")
        if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            if (mPermissionsDialog?.isShowing == true) return

            Log.d("onRequestPermission", "Displaying permissions dialog")
            val dialogMsg = getString(R.string.permissions_dialog_msg)
            speak(dialogMsg)
            mPermissionsDialog = getAlertDialog(
                this,
                dialogMsg,
                getString(R.string.permissions_dialog_title),
                getString(R.string.permissions_dialog_pos_btn_txt),
                { dialog, _ ->
                    dialog.cancel()
                    val intent = Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts("package", this@MainActivity.packageName, null)
                        data = uri
                    }
                    startActivity(intent)
                },
                getString(R.string.permissions_dialog_neg_btn_txt),
                { _, _ -> finish() }
            )
            mPermissionsDialog?.show()
            return
        }
        initAndStartTransmissionCycle()
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e("TTS", "Initilization Failed!")
            return
        }
        val locale = Locale.US
        val result = mTTS.setLanguage(locale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Shouldn't ever happen
            val errorMsg = "The specified Locale ($locale) is not supported."
            Log.e("TTS", errorMsg)
            showErrorMessage(errorMsg)
            return
        }

        mTTSInitialized = true
    }

    private fun initMLPClassifier() {
        mExecutor.submit(
            SonarThread(Runnable {
                Log.d("initMLPClassifier", "Loading MLP JSON files...")
                val weightsReader = JsonReader(InputStreamReader(resources.assets.open(MLP_WEIGHTS_FILE)))
                val biasReader = JsonReader(InputStreamReader(resources.assets.open(MLP_BIAS_FILE)))
                val json = Gson()
                val weights = json.fromJson<Array<Array<DoubleArray>>>(
                    weightsReader,
                    Array<Array<DoubleArray>>::class.java
                )
                val bias = json.fromJson<Array<DoubleArray>>(
                    biasReader,
                    Array<DoubleArray>::class.java
                )
                Log.d("initMLPClassifier", "Building MLP instance...")
                val clf = MLPClassifier.buildClassifier(weights, bias)
                Log.d("initMLPClassifier", "Done building MLP instance.")
                mMLPClassifier.postValue(clf)
            })
        )
    }

    private fun initAndStartTransmissionCycle() {
        if (mPermissionsDialog?.isShowing == true || !validatePermissionsGranted()) return

        if (mExecutor.isShutdown) mExecutor = Executors.newCachedThreadPool()
        checkMediaVolume()
        if (mMLPClassifier.value == null) initMLPClassifier()
        try {
            try {
                mTransmitter.init()
            } catch (e: UnsupportedOperationException) {
                showErrorMessage(e.toString())
            }
            mListener.init()
        } catch (e: SonarException) {
            showErrorMessage(e.toString())
        }
        submitNextTransmissionCycle()
    }

    override fun onResume() {
        Log.d("onResume", "Called")
        refreshAppState()
        super.onResume()
    }

    override fun onPause() {
        Log.d("onPause", "Called")
        savePreferences()
        mExecutor.shutdownNow()
        mTransmitter.stop()
        mTransmitter.release()
        mListener.stop()
        mListener.release()
        mTTS.stop()
        super.onPause()
    }

    override fun onDestroy() {
        mTTS.shutdown()
        super.onDestroy()
    }

    private fun savePreferences() {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putFloat(TTS_VOLUME_PREFERENCES_KEY, getTTSVolume())
            apply()
        }
    }

    private fun loadPreferences() {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val ttsVolume = sharedPref.getFloat(TTS_VOLUME_PREFERENCES_KEY, TTS_INITIAL_VOLUME)
        setTTSVolume(ttsVolume)
    }

    private fun isTTSReady() = mTTSInitialized && !mTTS.isSpeaking

    private fun submitNextTransmissionCycle() {
        transmissionCycle++
        val transmissionCycle = SonarThread(Runnable {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            while (mMLPClassifier.value == null || !isTTSReady()) continue

            mListener.mAudioRecorder.startRecording()
            mTransmitter.transmit()
            mListener.listen()
            Log.d("transmissionCycle", "Stopping transmission...")
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
            val mlpPrediction = if (mlpPredictionClass != null) {
                mlpPredictionClass / 10.0
            } else {
                peaksPrediction
            }

            val avgPrediction = 0.1 * mlpPrediction + 0.9 * peaksPrediction
            val distanceString = getDistanceTextToDisplay(avgPrediction.toFloat(), peaksPrediction.toFloat())
            mDistanceString.postValue(distanceString)

            if (transmissionCycle % 3 == 0) {
                Log.d("transmissionCycle", "Reading out loud distance of cycle $transmissionCycle")
                speak(getTTSDistanceText(avgPrediction.toFloat(), peaksPrediction.toFloat()))
            }

            submitNextTransmissionCycle()
        })
        mExecutor.submit(transmissionCycle)
    }

    private fun getTTSDistanceText(avgPrediction: Float, peaksPrediction: Float): String {
        return if (peaksPrediction == 0f) {
            getString(R.string.tts_upper_limit_distance)
        } else if (avgPrediction >= 2.2) {
            "%.1f meters".format(avgPrediction)
        } else {
            getString(R.string.tts_lower_limit_distance)
        }
    }

    private fun getDistanceTextToDisplay(avgPrediction: Float, peaksPrediction: Float): String {
        return if (peaksPrediction == 0f) {
            "> 5.0"
        } else if (avgPrediction >= 2.2) {
            "%.1f".format(avgPrediction)
        } else {
            "< 2.2"
        }
    }

    private fun showErrorMessage(errorMsg: String) {
        speak(R.string.error_tts_msg, addToQueue = false)
        getAlertDialog(
            this,
            errorMsg,
            getString(R.string.error_dialog_title),
            getString(R.string.error_dialog_pos_btn_txt),
            { _, _ -> finish() }
        )?.show()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    inner class MyGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            event1: MotionEvent,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            Log.d("onFling", "$event1 $event2")
            when {
                velocityX > 0 -> changeTTSVolumeBy(TTS_INCREMENT_DELTA)
                velocityX < 0 -> changeTTSVolumeBy(TTS_DECREMENT_DELTA)
            }
            return true
        }
    }

    companion object {
        val permissionArray = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )
        // This was taken from the 'longest_cc' file at SonarApp_utils. This is the CC that the MLP accepts.
        const val MLP_CC_SIZE = 4346
        const val MIN_CHIRP_FREQ = 3000.0
        const val MAX_CHIRP_FREQ = MIN_CHIRP_FREQ
        const val CHIRP_DURATION = 0.01
        const val SAMPLE_RATE = 44100
        // 10 meters
        const val MAX_PEAK_DIST = 2600
        // half chirp width
        val MIN_PEAK_DIST = (CHIRP_DURATION * SAMPLE_RATE * 0.5).roundToInt()
        val RECORDING_SAMPLES = (0.5 * SAMPLE_RATE).roundToInt()
        // Amount of samples to keep after chirp
        val RECORDING_CUT_OFF = (SAMPLE_RATE * (CHIRP_DURATION + 13.0 / DistanceAnalyzer.BASE_SOUND_SPEED)
                ).roundToInt() * 2
        var transmissionCycle = 0
        const val TTS_INITIAL_VOLUME = 0.5f
        const val MLP_WEIGHTS_FILE = "MLPWeights.json"
        const val MLP_BIAS_FILE = "MLPbias.json"
        const val TTS_VOLUME_PREFERENCES_KEY = "tts_volume"
        const val TTS_INCREMENT_DELTA = 0.1f
        const val TTS_DECREMENT_DELTA = -0.1f
        const val FONT_PATH = "Scada-Regular.ttf"
    }
}