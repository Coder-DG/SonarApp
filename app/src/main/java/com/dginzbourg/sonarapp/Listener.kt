package com.dginzbourg.sonarapp

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

class Listener {
    lateinit var mAudioRecorder: AudioRecord
    var mRecorderBuffer = ShortArray(HomeFragment.RECORDING_SAMPLES)


    fun listen() {
        Log.d("Listener.listen", "Listening for (${mRecorderBuffer.size} samples)...")
        mAudioRecorder.read(mRecorderBuffer, 0, mRecorderBuffer.size)
        Log.d("Listener.listen", "Done listening")
        mAudioRecorder.stop()
    }

    fun init() {
        Log.d("Listener.init", "Initializing the Listener...")
        mAudioRecorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(HomeFragment.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(HomeFragment.RECORDING_SAMPLES * 2)
            .build()
        Log.d("Listener.init", "Record buffer size is ${HomeFragment.RECORDING_SAMPLES}")
        if (mAudioRecorder.state != AudioRecord.STATE_INITIALIZED) {
            throw SonarException("The audio recorder was unable to initialize.")
        }
    }

    fun stop() {
        if (!::mAudioRecorder.isInitialized || mAudioRecorder.state != AudioRecord.STATE_INITIALIZED) return
        mAudioRecorder.stop()
    }

    fun release() {
        if (!::mAudioRecorder.isInitialized) return
        mAudioRecorder.stop()
    }
}