package com.dginzbourg.sonarapp

import java.lang.Math.min

class DistanceAnalyzer {

    companion object {
        const val BASE_SOUND_SPEED = 331
    }

    fun analyze(
        maxPeakDist: Int,
        minPeakDist: Int,
        peakRatio: Double,
        correlation: DoubleArray,
        soundSpeed: Double
    ): Double {
        // find maximum peak and assume it is the transmitted peak
        val transmittedPeakIndex = correlation.indexOf(correlation.max()!!)
        var returnPeakIndex = transmittedPeakIndex
        do {
            if (returnPeakIndex > transmittedPeakIndex + maxPeakDist || correlation[returnPeakIndex] == 0.0
                || returnPeakIndex >= correlation.size - 1) {
                return -1.0 // failure
            }
            // find next maximum up to a distance of maxPeakDist from the first peak
            val slicedList = correlation.slice(
                returnPeakIndex + 1 until
                        min(transmittedPeakIndex + maxPeakDist, correlation.size)
            ).toDoubleArray()
            returnPeakIndex += slicedList.indexOf(slicedList.max()!!) + 1
            // check distance between second peak and first peak is greater than minPeakDist and check that first peak
            // is larger than second peak
        } while (returnPeakIndex - transmittedPeakIndex < minPeakDist ||
            correlation[transmittedPeakIndex] < peakRatio * correlation[returnPeakIndex]
        )

        val time = (returnPeakIndex - transmittedPeakIndex) * (1.0 / MainActivity.SAMPLE_RATE)
        return soundSpeed * time
    }
}