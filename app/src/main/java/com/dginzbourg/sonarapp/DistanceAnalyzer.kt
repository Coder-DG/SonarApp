package com.dginzbourg.sonarapp

import org.jtransforms.fft.DoubleFFT_1D
import java.lang.Math.min

class DistanceAnalyzer {
    companion object {
        const val BASE_SOUND_SPEED = 331
    }

    fun analyze(
        recordedBuffer: ShortArray,
        pulseBuffer: ShortArray,
        noiseThreshold: Short,
        maxPeakDist: Int,
        minPeakDist: Int,
        peakRatio: Double,
        soundSpeed: Double
    ): Double {

        var correlation = crossCorrelation(recordedBuffer, pulseBuffer, noiseThreshold)
        correlation = correlation.map { if (it > noiseThreshold) it else 0.0 }.toDoubleArray() // filter out noise
        // find maximum peak and assume it is the transmitted peak
        val transmittedPeakIndex = correlation.indexOf(correlation.max()!!)
        var returnPeakIndex = transmittedPeakIndex
        do {
            if (returnPeakIndex > transmittedPeakIndex + maxPeakDist || correlation[returnPeakIndex] == 0.0) {
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

    private fun crossCorrelation(
        recordedBuffer: ShortArray,
        pulseBuffer: ShortArray,
        noiseThreshold: Short
    ): DoubleArray {
        // TODO: Why not trim this after the cross correlation graph calculation? Not sure what this give us
        // find first index of recorded buffer where it starts recording the transmitting signal
        var index = 0
        for (recordIndex in recordedBuffer.indices) {
            if (recordedBuffer[recordIndex] > noiseThreshold) {
                index = recordIndex
                break
            }
        }

        if (index >= 16383) {
            // todo fail
        }

        // trim the recorded buffer to not include the start noise
//        val recordedBuffer = recordedBuffer.slice(index..(index + MainActivity.RECORDING_SAMPLES))
        val n = recordedBuffer.size
        val pulse = DoubleFFT_1D(n.toLong() * 2)
        val record = DoubleFFT_1D(n.toLong() * 2)
        val pulseDoubleBuffer = DoubleArray(n)
        // Pulse is much shorter than pulseDoubleBuffer.size
        pulseBuffer.forEachIndexed { i, sh -> pulseDoubleBuffer[i] = sh.toDouble() }
        val recordedDoubleBuffer = recordedBuffer.map { it.toDouble() }.toDoubleArray()

        // calculate the correlation according to: inverse_fft(fft(record) * fft(pulse))
        pulse.complexForward(pulseDoubleBuffer)
        record.complexForward(recordedDoubleBuffer)
        val correlation = DoubleArray(16384)
        for (i in 0 until 16384) {
            // TODO: pulse numbers need to be conjugated before the multiplication
            correlation[i] = pulseDoubleBuffer[i] * recordedDoubleBuffer[i]
        }

        record.complexInverse(correlation, true)
        return correlation
    }
}