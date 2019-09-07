package com.dginzbourg.sonarapp

import android.Manifest
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.pm.PackageManager
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Process
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import org.json.JSONObject
import java.io.BufferedReader
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.*

// TODO: Performance enhancements
class MainActivity : AppCompatActivity() {
    private var mSONARAmplitude: MutableLiveData<LineData> = MutableLiveData()
    private var executor = Executors.newCachedThreadPool()
    private lateinit var mTempCalculator : TemperatureCalculator
    private val mListener = Listener()
    private val mTransmitter = Transmitter()
    private val mDistanceAnalyzer = DistanceAnalyzer()
    private val mNoiseFilter = NoiseFilter()
    private lateinit var requestQueue: RequestQueue

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
        requestQueue = Volley.newRequestQueue(this)
        //mTempCalculator = TemperatureCalculator(this)
        val sonarAmplitudeChart = findViewById<LineChart>(R.id.amp_chart)
        mTempCalculator = TemperatureCalculator(this)
//        setAmpChartGraphSettings(sonarAmplitudeChart)
        mSONARAmplitude.observe(this, Observer<LineData> {
            if (it == null)
                return@Observer
            sonarAmplitudeChart.data = it
            sonarAmplitudeChart.invalidate()
        })
        Log.d(LOG_TAG, "App started")
    }

    private fun setAmpChartGraphSettings(sonarAmplitudeChart: LineChart) {
        // This sets the zoom levels so we won't have to zoom in or out
        val maxValue = 4e10f
        val minValue = 0f
        sonarAmplitudeChart.axisLeft.axisMaximum = maxValue
        sonarAmplitudeChart.axisLeft.axisMinimum = minValue
        sonarAmplitudeChart.axisRight.axisMaximum = maxValue
        sonarAmplitudeChart.axisRight.axisMinimum = minValue
        sonarAmplitudeChart.xAxis.axisMinimum = 0f
        sonarAmplitudeChart.xAxis.axisMaximum = RECORDING_SAMPLES.toFloat()
    }


    override fun onResume() {
        requestQueue = Volley.newRequestQueue(this)
        mTransmitter.init()
        mListener.init()
        submitNextTransmissionCycle()
        super.onResume()
    }

    override fun onPause() {
        requestQueue.cancelAll { true }
        executor.shutdownNow()
        mTransmitter.mAudioPlayer.stop()
        mTransmitter.mAudioPlayer.release()
        mListener.mAudioRecorder.stop()
        mListener.mAudioRecorder.release()
        super.onPause()
    }

    private fun postDataToGraph(data: DoubleArray) {
        val entries = ArrayList<Entry>()
        data.forEachIndexed { index, db -> entries.add(Entry(index.toFloat(), db.toFloat())) }
        val dataSet = LineDataSet(entries, "SONAR Amplitude")
        dataSet.color = Color.BLACK
        dataSet.lineWidth = 1f
        dataSet.valueTextSize = 0.5f
        dataSet.setDrawCircles(false)
        mSONARAmplitude.postValue(LineData(dataSet))
    }

    private fun postDataToServer(recording: DoubleArray, cc: DoubleArray, cycle: Int) {
        val jsonRequestBody = HashMap<String, Any>(1)
        jsonRequestBody["recording"] = recording
        jsonRequestBody["cc"] = cc
        jsonRequestBody["cycle"] = cycle
        val request = object : JsonObjectRequest(
            SERVER_URL,
            JSONObject(jsonRequestBody),
            Response.Listener<JSONObject> {
                Log.d(LOG_TAG, "Server replied with $it")
            },
            Response.ErrorListener {
                Log.e(LOG_TAG, "Error sending data to server: $it")
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return HashMap<String, String>(1).also {
                    it[REQUESTS_CONTENT_TYPE_HEADER] = REQUESTS_CONTENT_TYPE_JSON
                }
            }
        }

        requestQueue.add(request)
    }

    private fun submitNextTransmissionCycle() {
        val transmissionCycle = SonarThread(Runnable {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            mListener.mAudioRecorder.startRecording()
            mTransmitter.transmit()
            mListener.listen()
            Log.d(LOG_TAG, "Stopping transmission...")
            mTransmitter.mAudioPlayer.stop()
            val correlation = mNoiseFilter.filterNoise(
                recordedBuffer = mListener.mRecorderBuffer,
                pulseBuffer = mTransmitter.mPlayerBuffer
            )
            if (correlation == null) {
                submitNextTransmissionCycle()
                return@Runnable
            }

            postDataToServer(
                mListener.mRecorderBuffer.map { it.toDouble() }.toDoubleArray(),
                correlation,
                ++transmissionCycle
            )

            val soundSpeed = 331.3 + 0.6 * mTempCalculator.getTemp()
            val dist = mDistanceAnalyzer.analyze(MAX_PEAK_DIST, MIN_PEAK_DIST, correlation, soundSpeed)
            val prediction = getNeuralNetworkPrediction(correlation)

            postDataToGraph(mListener.mRecorderBuffer.map { it.toDouble() }.toDoubleArray())
            submitNextTransmissionCycle()
        })
        executor.submit(transmissionCycle)
    }

    private fun getNeuralNetworkPrediction(correlation: DoubleArray) : Double {
        val fileWeights = resources.assets.open("MLPWeights.txt")
        val fileBias = resources.assets.open("MLPbias.txt")
//        val filePredict = resources.assets.open("predict.txt")

        val strWeights = fileWeights.bufferedReader().use(BufferedReader::readText)
        val strBias = fileBias.bufferedReader().use(BufferedReader::readText)
//        val strPredict = filePredict.bufferedReader().use(BufferedReader::readText)
//              double[] features = gson.fromJson(predict, double[].class);
        val prediction = MLPClassifier.classify(correlation, strWeights, strBias)
        return MLPClassifier.getDistance(prediction)
    }

    companion object {
        // TODO: go ultrasonic when this works (min: 20, max: 22)
        const val MIN_CHIRP_FREQ = 8000.0
        const val MAX_CHIRP_FREQ = 8000.0
        // TODO: try shorten the chirp to try cover only 1m
        const val CHIRP_DURATION = 0.01
        const val SAMPLE_RATE = 44100
        const val LOG_TAG = "sonar_app"
        // 10 meters
        const val MAX_PEAK_DIST = 2600
        // half chirp width
        const val MIN_PEAK_DIST = 220
        // 0.5sec of recordings. Can't be too little (you'll get an error). Has to be at least WINDOW_SIZE samples
        val RECORDING_SAMPLES = (0.5 * SAMPLE_RATE).roundToInt()
        //        val RECORDING_SAMPLES = max(
//            AudioRecord.getMinBufferSize(
//                SAMPLE_RATE,
//                AudioFormat.CHANNEL_IN_MONO,
//                AudioFormat.ENCODING_PCM_16BIT
//            ) / 2.0,
//            // Time it takes it to reach 10m (5m forward, 5m back), at 0 degrees celsius
//            SAMPLE_RATE * 10.0 / DistanceAnalyzer.BASE_SOUND_SPEED
//        ).roundToInt()
        // Amount of samples to keep after chirp
        val RECORDING_CUT_OFF = (SAMPLE_RATE * (CHIRP_DURATION + 10.0 / DistanceAnalyzer.BASE_SOUND_SPEED)).roundToInt() * 2
        /* DEBUG URL CONSTANTS */
        const val SERVER_URL = "http://YOUR_IP:5000/"
        const val REQUESTS_CONTENT_TYPE_HEADER = "Content-Type"
        const val REQUESTS_CONTENT_TYPE_JSON = "application/json"
        var transmissionCycle = 0
    }
}