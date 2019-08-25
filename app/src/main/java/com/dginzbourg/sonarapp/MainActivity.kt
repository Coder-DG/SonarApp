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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.math.*


class MainActivity : AppCompatActivity() {
    private var mSONARAmplitude: MutableLiveData<LineData> = MutableLiveData()
    private var executor = Executors.newCachedThreadPool()
    //private lateinit var mTempCalculator : TemperatureCalculator
//    private var mFFT = DoubleFFT_1D(WINDOW_SIZE.toLong())
    private val mAnalysisLock = ReentrantLock()
    private val mListener = Listener()
    private val mTransmitter = Transmitter()
    private val mDistanceAnalyzer = DistanceAnalyzer()
    private val mNoiseFilter = NoiseFilter()

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
        sonarAmplitudeChart.axisLeft.axisMaximum = Short.MAX_VALUE.toFloat()
        sonarAmplitudeChart.axisLeft.axisMinimum = Short.MIN_VALUE.toFloat()
        sonarAmplitudeChart.axisRight.axisMaximum = Short.MAX_VALUE.toFloat()
        sonarAmplitudeChart.axisRight.axisMinimum = Short.MIN_VALUE.toFloat()
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

//    private fun analyzeRecordings(recorderBuffer: ShortArray) {
//        /* Currently this just gets the 20KHz amplitude over the transmission time.
//        *
//        * TODO: do the analysis in an orderly fashion. tryLock is not starvation free. This way we'll be able to give
//        *  the user the relevant distances. This can be dealt with after finishing the FFT milestone.
//        * */
//        // Only one thread at a time is allowed to use the buffer. This also simplifies how we deliver the relevant
//        // distance to the user (because there's only one analysis running at a time).
//        mAnalyzingLock.lock()
//        Log.d(LOG_TAG, "Analyzing data...")
//        for (i in mSONARDataBuffer.indices) {
//            val startPos = i * WINDOW_OVERLAP_EXTERIOR.toInt()
//            val endPos = i * WINDOW_OVERLAP_EXTERIOR.toInt() + WINDOW_SIZE
//            for (indexInWindow in startPos..endPos) {
//                // Normalize data and copy to mAnalyzerBuffer
//                mAnalyzerBuffer[indexInWindow - startPos] = recorderBuffer[indexInWindow] / Short.MAX_VALUE.toDouble()
//            }
//            mFFT.realForwardFull(mAnalyzerBuffer)
//            /* There are 1024 frequency buckets, we need the one where the main frequency resides. Not sure about the
//            * /2.0 but when we measure with a sample rate of X then the max frequency we can measure is 0.5X. I hope
//            * this is the right calculation */
//            mSONARDataBuffer[i] = mAnalyzerBuffer[(MAIN_FREQUENCY / (SAMPLE_RATE / 2.0 / WINDOW_SIZE)).toInt()]
//        }
//        postSoundRecording()
//        mAnalyzingLock.unlock()
//    }

//    private fun saveToFile(recorderBuffer: ShortArray) = runOnUiThread {
//        val file = File(filesDir, "SonarApp_" + Calendar.getInstance().time.time)
//        file.createNewFile()
//        val writer = FileWriter(file)
//        writer.use { w ->
//            recorderBuffer.forEach {
//                w.write(it.toString() + '\n')
//            }
//        }
//    }

    private fun postSoundRecording() {
        val entries = ArrayList<Entry>()
        mListener.mRecorderBuffer.forEachIndexed { index, db -> entries.add(Entry(index.toFloat(), db.toFloat())) }
        val dataSet = LineDataSet(entries, "SONAR Amplitude")
        dataSet.color = Color.BLACK
        dataSet.lineWidth = 1f
        dataSet.valueTextSize = 0.5f
        dataSet.setDrawCircles(false)
        mSONARAmplitude.postValue(LineData(dataSet))
    }

    private fun submitNextTransmissionCycle() {
        val transmissionCycle = SonarThread(Runnable {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            mListener.mAudioRecorder.startRecording()
            mTransmitter.transmit()
            mListener.listen()
            Log.d(LOG_TAG, "Stopping transmission...")
            mTransmitter.mAudioPlayer.stop()
            submitNextTransmissionCycle()
            mAnalysisLock.lock()
            postSoundRecording()
            mNoiseFilter.filterNoise()
            // TODO: move speed of sound calculation to distance analyzer to a companion object (make it static)
            //val soundSpeed = 331 + 0.6 * mTempCalculator.getTemp()
            val soundSpeed = 331 + 0.6 * 15
            //mDistanceAnalyzer.analyze(soundSpeed)
            mAnalysisLock.unlock()
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
        const val RECORDING_SAMPLES = SAMPLE_RATE
        const val WINDOW_SIZE = 1024
        val WINDOW_OVERLAP = floor(0.5 * WINDOW_SIZE)
        val WINDOW_OVERLAP_EXTERIOR = WINDOW_SIZE - WINDOW_OVERLAP
        const val FFT_BUFFER_SIZE = 2 * WINDOW_SIZE
        /* This amount represents the maximum amount of samples we'd analyse.
        * 0.03 = time it takes for sound to travel 10.29m in air that is 20c degrees hot. That's our threshold. */
        val LISTENING_SAMPLES_THRESHOLD = min(ceil(SAMPLE_RATE * 0.03).toInt(), RECORDING_SAMPLES)
        val SONAR_DATA_BUFFER_SIZE = floor(
            (LISTENING_SAMPLES_THRESHOLD - 1) / WINDOW_OVERLAP_EXTERIOR
        ).toInt()
    }
}