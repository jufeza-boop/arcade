package com.retroarcade.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.retroarcade.core.SettingsRepository
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sintetizador chiptune procedural en memoria.
 * Genera archivos WAV temporales en almacenamiento local en tiempo de ejecución.
 * Cero dependencias de assets externos.
 */
class ChiptuneAudio(private val settingsRepository: SettingsRepository) {

    private val sampleRate = 22050
    private var sfxMove: Sound? = null
    private var sfxRotate: Sound? = null
    private var sfxDrop: Sound? = null
    private var sfxClear: Sound? = null
    private var sfxGameOver: Sound? = null

    init {
        try {
            sfxMove = createSound("move.wav") { generateSquarePcm(420f, 0.035f) }
            sfxRotate = createSound("rotate.wav") { generateSquarePcm(660f, 0.045f) }
            sfxDrop = createSound("drop.wav") { generateSquarePcm(150f, 0.07f) }
            sfxClear = createSound("clear.wav") { generateArpeggioPcm(listOf(523f, 659f, 784f, 1046f), 0.055f) }
            sfxGameOver = createSound("gameover.wav") { generateSlidePcm(420f, 120f, 0.35f) }
        } catch (_: Throwable) {
            // Protección: si el audio de la plataforma no estuviera disponible, continúa en silencio
        }
    }

    fun playMove() {
        if (!settingsRepository.isMuted()) sfxMove?.play(0.5f)
    }

    fun playRotate() {
        if (!settingsRepository.isMuted()) sfxRotate?.play(0.6f)
    }

    fun playDrop() {
        if (!settingsRepository.isMuted()) sfxDrop?.play(0.7f)
    }

    fun playClear() {
        if (!settingsRepository.isMuted()) sfxClear?.play(0.85f)
    }

    fun playGameOver() {
        if (!settingsRepository.isMuted()) sfxGameOver?.play(0.9f)
    }

    private fun createSound(filename: String, pcmGenerator: () -> ByteArray): Sound? {
        return try {
            val pcm = pcmGenerator()
            val wav = pcmToWav(pcm, sampleRate)
            val file = Gdx.files.local(filename)
            file.writeBytes(wav, false)
            Gdx.audio.newSound(file)
        } catch (_: Throwable) {
            null
        }
    }

    private fun generateSquarePcm(freq: Float, durationSec: Float): ByteArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ByteArray(numSamples)
        val period = (sampleRate / freq).toInt().coerceAtLeast(1)

        for (i in 0 until numSamples) {
            val phase = (i % period).toFloat() / period
            val sample = if (phase < 0.5f) 90 else -90
            val env = 1f - (i.toFloat() / numSamples)
            // En WAV 8-bit, 128 es el silencio central
            pcm[i] = (128 + (sample * env).toInt()).toByte()
        }
        return pcm
    }

    private fun generateArpeggioPcm(notes: List<Float>, noteDuration: Float): ByteArray {
        val noteSamples = (sampleRate * noteDuration).toInt()
        val totalSamples = noteSamples * notes.size
        val pcm = ByteArray(totalSamples)
        var offset = 0

        for (freq in notes) {
            val period = (sampleRate / freq).toInt().coerceAtLeast(1)
            for (i in 0 until noteSamples) {
                val phase = (i % period).toFloat() / period
                val sample = if (phase < 0.5f) 80 else -80
                val env = 1f - (i.toFloat() / noteSamples)
                if (offset + i < totalSamples) {
                    pcm[offset + i] = (128 + (sample * env).toInt()).toByte()
                }
            }
            offset += noteSamples
        }
        return pcm
    }

    private fun generateSlidePcm(startFreq: Float, endFreq: Float, durationSec: Float): ByteArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ByteArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val freq = startFreq + (endFreq - startFreq) * t
            val deltaPhase = 2.0 * PI * freq / sampleRate
            phase += deltaPhase
            val sample = if (sin(phase) > 0) 75 else -75
            val env = 1f - t
            pcm[i] = (128 + (sample * env).toInt()).toByte()
        }
        return pcm
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 1 * 1
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0 // PCM
        header[22] = 1; header[23] = 0 // 1 canal (mono)

        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = 1; header[33] = 0
        header[34] = 8; header[35] = 0 // 8 bits

        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        return header + pcmData
    }

    fun dispose() {
        try {
            sfxMove?.dispose()
            sfxRotate?.dispose()
            sfxDrop?.dispose()
            sfxClear?.dispose()
            sfxGameOver?.dispose()
        } catch (_: Throwable) {}
    }
}
