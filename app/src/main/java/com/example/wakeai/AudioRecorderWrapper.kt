package com.example.wakeai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class AudioRecorderWrapper(context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000 // Whisper API recommended rate
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ) * 4

    }

    private var audioRecord: AudioRecord? = null
    private var audioBuffer: ByteArrayOutputStream? = null
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startListeningToUserSpeech(onStopListening: (ByteArray?) -> Unit) {
        var silentFrames = 0
        val silenceThreshold = 50  // Adjust based on testing
        val SILENCE_TIMEOUT_MS = 2000L
        val CHECK_INTERVAL_MS = 100L
        val maxSilentChecks = (SILENCE_TIMEOUT_MS / CHECK_INTERVAL_MS).toInt()

        audioRecord = AudioRecord(
            AUDIO_SOURCE,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )
        audioRecord?.startRecording()
        Log.d("AudioRecorder", "Started recording audio")

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            // Handle AudioRecord initialization failure
            audioRecord = null
            return
        }

        audioBuffer = ByteArrayOutputStream()

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (readBytes <= 0) continue

                audioBuffer?.write(buffer, 0, readBytes)

                if (isSilent(buffer, readBytes, silenceThreshold)) {
                    silentFrames++
                    if (silentFrames >= maxSilentChecks) {
                        Log.d("AudioRecorder", "Stopped listening to user speech due to sustained silence.")
                        break // Stop after sustained silence
                    }
                } else {
                    silentFrames = 0
                }
                delay(CHECK_INTERVAL_MS)
            }

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            val pcmData = audioBuffer?.toByteArray()
            audioBuffer = null // Clear the buffer
            onStopListening(pcmData?.let { pcmToWav(it) })
        }
    }

    private fun playAudio(audioData: ByteArray) {
        val player = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            BUFFER_SIZE,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        player.play()
        val result = pcmToWav(audioData)
        player.write(result, 0, result?.size ?: 0)
        player.stop()
        player.release()
    }

    private fun isSilent(audioData: ByteArray, readBytes: Int, threshold: Int): Boolean {
        var sumAmplitude = 0L
        for (i in 0 until readBytes step 2) {
            val amplitude = audioData[i].toInt() or (audioData[i + 1].toInt() shl 8)
            sumAmplitude += Math.abs(amplitude)
        }
        val avgAmplitude = sumAmplitude / (readBytes / 2)
        return avgAmplitude < threshold
    }

    // Utility function to convert PCM audio data to WAV format
    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val byteRate = 16 * SAMPLE_RATE / 8
        val output = ByteArrayOutputStream()

        // WAV header
        output.write("RIFF".toByteArray())
        output.write(intToByteArray(36 + pcmData.size), 0, 4)
        output.write("WAVE".toByteArray())
        output.write("fmt ".toByteArray())
        output.write(intToByteArray(16), 0, 4) // Subchunk1Size for PCM
        output.write(shortToByteArray(1), 0, 2) // PCM format
        output.write(shortToByteArray(1), 0, 2) // Mono
        output.write(intToByteArray(SAMPLE_RATE), 0, 4)
        output.write(intToByteArray(byteRate), 0, 4)
        output.write(shortToByteArray(2), 0, 2) // Block align
        output.write(shortToByteArray(16), 0, 2) // Bits per sample
        output.write("data".toByteArray())
        output.write(intToByteArray(pcmData.size), 0, 4)
        output.write(pcmData)

        return output.toByteArray()
    }

    private fun intToByteArray(i: Int): ByteArray =
        byteArrayOf(
            (i and 0xff).toByte(),
            (i shr 8 and 0xff).toByte(),
            (i shr 16 and 0xff).toByte(),
            (i shr 24 and 0xff).toByte()
        )

    private fun shortToByteArray(i: Int): ByteArray =
        byteArrayOf((i and 0xff).toByte(), (i shr 8 and 0xff).toByte())
}