package com.dginzbourg.sonarapp

import java.lang.Math.min

class DistanceAnalyzer {

    companion object {
        const val BASE_SOUND_SPEED = 331
        // todo change this
        const val PEAK_NOISE_THRESHOLD = 10
    }

    fun analyze(
        maxPeakDist: Int,
        minPeakDist: Int,
        correlation: DoubleArray,
        soundSpeed: Double
    ): Double {
        // todo change: width of peak
        val peakWidth = MainActivity.CHIRP_DURATION * MainActivity.SAMPLE_RATE
        // find maximum peak and assume it is the transmitted peak
        val transmittedPeakIndex = correlation.indexOf(correlation.max()!!)
        // the first return peak must be at least after the transmission ends
        var returnPeakIndex = transmittedPeakIndex + minPeakDist
        for (i in returnPeakIndex + 1 until min(transmittedPeakIndex + maxPeakDist, correlation.size)) {
            var j = i - (peakWidth / 2).toInt()
            if (correlation[i] > PEAK_NOISE_THRESHOLD) {
                while (correlation[i] >= correlation[j] && j < i + (peakWidth / 2).toInt()) {
                    j += 1
                }
            }

            if (j == i + (peakWidth / 2).toInt()) {
                returnPeakIndex = i
                break // i is the return peak
            }
        }

        val time = (returnPeakIndex - transmittedPeakIndex) * (1.0 / MainActivity.SAMPLE_RATE)
        return soundSpeed * time
    }
}