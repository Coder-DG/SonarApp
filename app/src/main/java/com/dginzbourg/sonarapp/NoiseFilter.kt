package com.dginzbourg.sonarapp

import org.apache.commons.math3.complex.Complex
import org.jtransforms.fft.DoubleFFT_1D

class NoiseFilter {
    companion object {
        // TODO: change that to the correct value (or find a value)
        const val RECORDING_NOISE_THRESHOLD = 10
        // TODO: use the value from the article
        const val CROSS_CORRELATION_SOUND_THRESHOLD = 10
    }

    fun filterNoise(
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
        pulseBuffer.forEachIndexed { i, sh -> pulseDoubleBuffer[i * 2] = sh.toDouble() }
        for (i in firstSampleIndex until recordedBuffer.size) {
            recordedDoubleBuffer[i * 2] = recordedBuffer[i].toDouble()
        }

        // TODO: maybe we should use Floats instead? Or normalize the amplitude data to be between -1 and 1?
        //  we might get more precision out of Float if we are using big values
        val fftCalculator = DoubleFFT_1D(n.toLong())
        var correlation = crossCorrelation(fftCalculator, pulseDoubleBuffer, recordedDoubleBuffer, n)
        correlation = correlation.map { if (it > CROSS_CORRELATION_SOUND_THRESHOLD) it else 0.0 }.toDoubleArray()

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
            mulResult = pComplex.conjugate().multiply(rComplex)
            correlation[i] = mulResult.real
            correlation[i + 1] = mulResult.imaginary
        }

        // TODO: Check whether we need to scale or not
        fft.complexInverse(correlation, true)
        return correlation
    }

}