package com.dginzbourg.sonarapp

import org.apache.commons.math3.complex.Complex
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class NoiseFilter {
    companion object {
        // TODO: change that to the correct value (or find a value)
        const val RECORDING_NOISE_THRESHOLD = 10e7
        // TODO: use the value from the article
        const val CROSS_CORRELATION_SOUND_THRESHOLD = 10
    }

    fun filterNoise(
        recordedBuffer: ShortArray,
        pulseBuffer: ShortArray
    ): DoubleArray? {
        val chirpMiddle = recordedBuffer.indexOfFirst { it == recordedBuffer.max() && it > RECORDING_NOISE_THRESHOLD }
        if (chirpMiddle == -1) return null

        val chirpStart = max(chirpMiddle - MainActivity.SAMPLE_RATE * MainActivity.CHIRP_DURATION * 0.5, 0.0)
        val firstSampleIndex = chirpStart.toInt()

        val n = MainActivity.RECORDING_CUT_OFF
        if (n < pulseBuffer.size) return null

        // TODO: Check when is the tranmission too close to the end of the recording and invalidate this calculation.
        val recordedDoubleBuffer = DoubleArray(n * 2)
        val pulseDoubleBuffer = DoubleArray(n * 2)
        // Filling up the real values, leaving the imaginary values as 0's
        pulseBuffer.forEachIndexed { i, sh -> pulseDoubleBuffer[i * 2] = sh.toDouble() }
        for (i in 0 until n) {
            recordedDoubleBuffer[i * 2] = recordedBuffer[firstSampleIndex + i].toDouble()
        }

        val fftCalculator = DoubleFFT_1D(n.toLong())
        var correlation = crossCorrelation(fftCalculator, pulseDoubleBuffer, recordedDoubleBuffer, n)

        return correlation
    }

    private fun crossCorrelation(
        fft: DoubleFFT_1D,
        pBuffer: DoubleArray,
        rBuffer: DoubleArray,
        n: Int
    ): DoubleArray {
        // calculate the correlation according to: inverse_fft(fft(record).conjugate * fft(pulse))
        fft.complexForward(pBuffer)
        fft.complexForward(rBuffer)
        val correlation = DoubleArray(n * 2)
        var pComplex: Complex
        var rComplex: Complex
        var mulResult: Complex
        for (i in 0 until n step 2) {
            pComplex = Complex(pBuffer[i], pBuffer[i + 1])
            rComplex = Complex(rBuffer[i], rBuffer[i + 1])
            mulResult = pComplex.multiply(rComplex)
            correlation[i] = mulResult.real
            correlation[i + 1] = mulResult.imaginary
        }

        // TODO: Check whether we need to scale or not
        fft.complexInverse(correlation, true)
        val halfIndices = correlation.indices step 2
        return halfIndices.map { i -> Complex(correlation[i], correlation[i + 1]).abs() }.toDoubleArray()
    }

}