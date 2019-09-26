package com.dginzbourg.sonarapp

import android.app.Activity
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class TemperatureCalculator(context: Context) : SensorEventListener {
    private val sensorManager: SensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager
    private var tempSensor: Sensor? = null

    init {
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            tempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, tempSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // fai! we dont have an accelerometer!
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        temperature = try {
            event.values[0]
        } catch (e: Exception) {
            23f
        }
    }

    companion object {
        var temperature = 23f
    }

    fun getTemp() : Float {
        return temperature
    }

}