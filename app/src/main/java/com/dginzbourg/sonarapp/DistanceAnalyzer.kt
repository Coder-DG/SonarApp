package com.dginzbourg.sonarapp

import kotlin.math.pow
import kotlin.math.min

class DistanceAnalyzer {

    companion object {
        const val BASE_SOUND_SPEED = 331
        // todo change this
        val PEAK_NOISE_THRESHOLD = 10.0.pow(0.7)
    }

    fun analyze(
        maxPeakDist: Int,
        minPeakDist: Int,
        correlation: DoubleArray,
        soundSpeed: Double
    ): Double? {
        // todo change: width of peak
        val peakWidth = HomeFragment.CHIRP_DURATION * HomeFragment.SAMPLE_RATE
        // find maximum peak and assume it is the transmitted peak
        val ccMax = correlation.max() ?: return null
        val transmittedPeakIndex = correlation.indexOf(ccMax)
        // the first return peak must be at least after the transmission ends
        var returnPeakIndex = transmittedPeakIndex + minPeakDist
        for (i in returnPeakIndex + 1 until min(transmittedPeakIndex + maxPeakDist, correlation.size)) {
            val returnPeakWidth = (peakWidth / 4).toInt()
            var j = i - returnPeakWidth
            if (correlation[i] > PEAK_NOISE_THRESHOLD) {
                while (correlation[i] >= correlation[j] && j < min(correlation.size - 1, i + returnPeakWidth)) {
                    j += 1
                }
            }

            if (j == i + returnPeakWidth || j == correlation.size - 1) {
                returnPeakIndex = i
                break // i is the return peak
            }
        }

        // didn't find a peak
        if (returnPeakIndex == transmittedPeakIndex + minPeakDist) {
            return  0.0
        }

        val time = (returnPeakIndex - transmittedPeakIndex) * (1.0 / HomeFragment.SAMPLE_RATE)
        return soundSpeed * time / 2
    }
}