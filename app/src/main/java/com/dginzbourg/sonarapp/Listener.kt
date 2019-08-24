package com.dginzbourg.sonarapp

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

class Listener {
    lateinit var mAudioRecorder: AudioRecord
    var mRecorderBuffer = ShortArray(MainActivity.RECORDING_SAMPLES)


    fun listen() {
        Log.d(MainActivity.LOG_TAG, "Listening...")
        mAudioRecorder.read(mRecorderBuffer, 0, mRecorderBuffer.size)
        mAudioRecorder.stop()
    }

    fun init() {
        Log.d(MainActivity.LOG_TAG, "Initializing the Listener...")
        mAudioRecorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                .setAudioFormat(
                        AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(MainActivity.SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build()
                )
                .setBufferSizeInBytes(MainActivity.RECORDING_SAMPLES * 2)
                .build()
        if (mAudioRecorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(MainActivity.LOG_TAG, "Unable to init recorder")
            // TODO: let the user know
        }
    }
}