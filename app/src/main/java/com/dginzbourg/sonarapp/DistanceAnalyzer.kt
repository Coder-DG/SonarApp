package com.dginzbourg.sonarapp

import org.apache.commons.math3.complex.Complex
import org.jtransforms.fft.DoubleFFT_1D
import java.lang.Math.min

class DistanceAnalyzer {
    companion object {
        const val BASE_SOUND_SPEED = 331
        // TODO: change that to the correct value (or find a value)
        const val RECORDING_NOISE_THRESHOLD = 10
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
        pulseBuffer: ShortArray
    ): DoubleArray {
        // find first index of recorded buffer where it starts recording the transmitting signal
        val firstSampleIndex = recordedBuffer.indexOfFirst { it > RECORDING_NOISE_THRESHOLD }

        if (firstSampleIndex == -1 || firstSampleIndex == recordedBuffer.lastIndex) {
            // todo fail
        }

        // Trim the recorded buffer to not include the start noise and 0's
        val n = recordedBuffer.size - firstSampleIndex
        val recordedDoubleBuffer = DoubleArray(n * 2)
        val pulseDoubleBuffer = DoubleArray(n * 2)
        // Filling up the real values, leaving the imaginary values as 0's
        recordedBuffer.forEachIndexed { i, sh -> recordedDoubleBuffer[i * 2] = sh.toDouble() }
        pulseBuffer.forEachIndexed { i, sh -> pulseDoubleBuffer[i * 2] = sh.toDouble() }
        // TODO: maybe we should use Floats instead? Or normalize the amplitude data to be between -1 and 1?
        //  we might get more precision out of Float if we are using big values
        val fftCalculator = DoubleFFT_1D(n.toLong())

        // calculate the correlation according to: inverse_fft(fft(record).conjugate * fft(pulse))
        fftCalculator.complexForward(pulseDoubleBuffer)
        fftCalculator.complexForward(recordedDoubleBuffer)
        val correlation = DoubleArray(n * 2)
        var pComplex: Complex
        var rComplex: Complex
        var mulResult: Complex
        for (i in 0 until n step 2) {
            pComplex = Complex(pulseDoubleBuffer[i], pulseDoubleBuffer[i + 1])
            rComplex = Complex(recordedDoubleBuffer[i], recordedDoubleBuffer[i + 1])
            mulResult = pComplex.conjugate().multiply(rComplex)
            correlation[i] = mulResult.real
            correlation[i + 1] = mulResult.imaginary
        }

        // TODO: Check whether we need to scale or not
        fftCalculator.complexInverse(correlation, true)
        return correlation
    }
}