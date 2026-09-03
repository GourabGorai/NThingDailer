package com.example.nthingdailer.audio

import java.io.File
import java.io.FileOutputStream

object AudioFileGenerator {

    /**
     * Generates a valid 16-bit PCM Mono 44.1kHz WAV audio file with a loud, clear,
     * synthesized call voice tone sequence so that MediaPlayer can play call recordings
     * clearly at full audible volume.
     */
    fun generateSampleWavFile(file: File, durationSeconds: Int = 5) {
        try {
            val sampleRate = 44100
            val numSamples = sampleRate * durationSeconds
            val dataSize = numSamples * 2
            val totalSize = dataSize + 36

            val header = ByteArray(44)
            // RIFF/WAVE header
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            
            header[4] = (totalSize and 0xff).toByte()
            header[5] = ((totalSize shr 8) and 0xff).toByte()
            header[6] = ((totalSize shr 16) and 0xff).toByte()
            header[7] = ((totalSize shr 24) and 0xff).toByte()
            
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            
            header[16] = 16 // Subchunk1Size (16 for PCM)
            header[17] = 0
            header[18] = 0
            header[19] = 0
            
            header[20] = 1 // AudioFormat (1 for PCM)
            header[21] = 0
            
            header[22] = 1 // NumChannels (1 for Mono)
            header[23] = 0
            
            // SampleRate (44100)
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            
            // ByteRate (44100 * 2 = 88200)
            val byteRate = sampleRate * 2
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            
            header[32] = 2 // BlockAlign (2)
            header[33] = 0
            
            header[34] = 16 // BitsPerSample (16)
            header[35] = 0
            
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            
            header[40] = (dataSize and 0xff).toByte()
            header[41] = ((dataSize shr 8) and 0xff).toByte()
            header[42] = ((dataSize shr 16) and 0xff).toByte()
            header[43] = ((dataSize shr 24) and 0xff).toByte()

            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            FileOutputStream(file).use { fos ->
                fos.write(header)
                val buffer = ByteArray(2048)
                var bufferIndex = 0
                
                val f1 = 523.25 // C5 note (clear audible tone)
                val f2 = 659.25 // E5 note
                val twoPiF1 = 2.0 * Math.PI * f1
                val twoPiF2 = 2.0 * Math.PI * f2
                
                for (i in 0 until numSamples) {
                    val timeInSec = i.toDouble() / sampleRate
                    val cycleTime = timeInSec % 1.2
                    
                    val activePulse = cycleTime < 0.25 || (cycleTime in 0.35..0.6)
                    val amplitude = if (activePulse) 0.75 else 0.05
                    
                    val currentFreq = if (cycleTime < 0.25) twoPiF1 else twoPiF2
                    val sampleVal = (Math.sin(currentFreq * timeInSec) * 32767 * amplitude).toInt().coerceIn(-32768, 32767)
                    
                    buffer[bufferIndex++] = (sampleVal and 0xff).toByte()
                    buffer[bufferIndex++] = ((sampleVal shr 8) and 0xff).toByte()
                    
                    if (bufferIndex >= buffer.size) {
                        fos.write(buffer, 0, bufferIndex)
                        bufferIndex = 0
                    }
                }
                if (bufferIndex > 0) {
                    fos.write(buffer, 0, bufferIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
