package com.dginzbourg.sonarapp

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val ttsSpeed = MutableLiveData<Float>()
    val ttsVolume = MutableLiveData<Float>()

    fun updateTTSSpeed(speed: Float) {
        ttsSpeed.value = speed
    }

    fun updateTTSVolume(volume: Float) {
        ttsVolume.value = volume
    }
}