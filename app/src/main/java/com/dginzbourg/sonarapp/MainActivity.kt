package com.dginzbourg.sonarapp

import android.Manifest
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Process
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.math.*

// TODO: Performance enhancements
class MainActivity : AppCompatActivity() {
    private var mSONARAmplitude: MutableLiveData<LineData> = MutableLiveData()
    private var executor = Executors.newCachedThreadPool()
    //private lateinit var mTempCalculator : TemperatureCalculator
    private val mListener = Listener()
    private val mTransmitter = Transmitter()
    private val mDistanceAnalyzer = DistanceAnalyzer()
    private val mNoiseFilter = NoiseFilter()
    private val requestQueue = Volley.newRequestQueue(this)

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
        //mTempCalculator = TemperatureCalculator(this)
        val sonarAmplitudeChart = findViewById<LineChart>(R.id.amp_chart)
        setAmpChartGraphSettings(sonarAmplitudeChart)
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
        mTransmitter.init()
        mListener.init()
        submitNextTransmissionCycle()
        super.onResume()
    }

    override fun onPause() {
        executor.shutdownNow()
        mTransmitter.mAudioPlayer.release()
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

    private fun postDataToServer(data: DoubleArray) {
        val jsonRequestBody = HashMap<String, String>(1)
        jsonRequestBody["data"] = data.toString()
        val request = object : JsonObjectRequest(
            SERVER_URL,
            JSONObject(jsonRequestBody),
            Response.Listener<JSONObject> {},
            Response.ErrorListener {}
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
            val filteredRecording = mNoiseFilter.filterNoise(
                recordedBuffer = mListener.mRecorderBuffer,
                pulseBuffer = mTransmitter.mPlayerBuffer
            )
            postDataToServer(filteredRecording)
            // TODO: move speed of sound calculation to distance analyzer to a companion object (make it static)
            //val soundSpeed = 331 + 0.6 * mTempCalculator.getTemp()
            val soundSpeed = 331 + 0.6 * 15
            //mDistanceAnalyzer.analyze(soundSpeed)
            submitNextTransmissionCycle()
        })
        executor.submit(transmissionCycle)
    }

    companion object {
        // TODO: go ultrasonic when this works (min: 20, max: 22)
        const val MIN_CHIRP_FREQ = 4000.0
        const val MAX_CHIRP_FREQ = 8000.0
        // TODO: try shorten the chirp to try cover only 1m
        const val CHIRP_DURATION = 0.01
        const val SAMPLE_RATE = 44100
        const val LOG_TAG = "sonar_app"
        // 0.5sec of recordings. Can't be too little (you'll get an error). Has to be at least WINDOW_SIZE samples
        val RECORDING_SAMPLES = max(
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) / 2.0,
            // Time it takes it to reach 10m (5m forward, 5m back), at 0 degrees celsius
            SAMPLE_RATE * 10.0 / DistanceAnalyzer.BASE_SOUND_SPEED
        ).roundToInt()
        /* DEBUG URL CONSTANTS */
        const val SERVER_URL = "http://YOUR_IP:5000/"
        const val REQUESTS_CONTENT_TYPE_HEADER = "Content-Type"
        const val REQUESTS_CONTENT_TYPE_JSON = "application/json"
    }
}