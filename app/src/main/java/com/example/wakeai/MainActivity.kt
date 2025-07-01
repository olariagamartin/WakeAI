package com.example.wakeai

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import com.example.wakeai.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private val PORCUPINE_API_KEY = "9mluvEpgDHKo6dIRdHOv3alIMQBsVY5dDCcf3NPkkpkqaluJNuKYXQ=="
    private lateinit var porcupineManager: PorcupineManager
    private lateinit var binding: ActivityMainBinding
    private val AUDIO_PERMISSION_CODE = 101

    private val audioRecorderWrapper = AudioRecorderWrapper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setContentView(binding.root) // Set the content view to the root of the binding
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets -> // Use binding.main
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.button.setOnClickListener {
            binding.infoText.text = "Sleeping..."
            requestAudioPermission()
        }
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                AUDIO_PERMISSION_CODE
            )
        } else {
            startWakeWordDetection()
        }
    }

    private fun startWakeWordDetection() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(PORCUPINE_API_KEY)
                .setKeywords(arrayOf(Porcupine.BuiltInKeyword.JARVIS))
                .setSensitivity(0.5f)
                .build(applicationContext, porcupineCallback)

            porcupineManager.start()
        } catch (e: PorcupineException) {
            Toast.makeText(this, "Failed to initialize Porcupine: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private val porcupineCallback = PorcupineManagerCallback { keywordIndex ->
        if (keywordIndex == 0) {
            Log.d("VoiceDetector", "detected")
            binding.infoText.text = "Awake"
            audioRecorderWrapper.startListeningToUserSpeech { result ->
                if (result != null) {
                    Log.d("MainActivity", "Audio data received, size: ${result.size}")
                    runOnUiThread {
                        binding.infoText.text = "Transcribing audio..."
                    }
                    OpenAIApiService.sendAudioToWhisperAPI(result) { whisperResponse ->
                        runOnUiThread {
                            binding.infoText.text = "Loading audio..."
                        }
                        OpenAIApiService.getChatGPTResponse(whisperResponse) { textResponse ->
                            runOnUiThread {
                                binding.infoText.text = "Loading response audio..."
                            }
                            OpenAIApiService.generateAudioFromText(textResponse) { audioTranscribed ->
                                runOnUiThread {
                                    binding.infoText.text = "Playing response..."
                                }
                                Log.d("MainActivity", "Audio generated")
                                playAudio(audioTranscribed)
                            }
                        }
                    }
                } else {
                    Log.d("MainActivity", "No audio data received")
                }
            }
        } else if (keywordIndex == 1) {
            // bumblebee detected
            Log.d("VoiceDetector", "not detected")
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startWakeWordDetection()
        } else {
            Toast.makeText(this, "Microphone permission required!", Toast.LENGTH_SHORT).show()
        }
    }

    fun playAudio(audioStream: InputStream) {
        val tempAudioFile = File.createTempFile("tts_audio", ".mp3", cacheDir)
        audioStream.use { input ->
            FileOutputStream(tempAudioFile).use { output ->
                input.copyTo(output)
            }
        }

        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(tempAudioFile.absolutePath)
        mediaPlayer.prepare()
        mediaPlayer.setOnCompletionListener {
            mediaPlayer.release()
            tempAudioFile.delete()
            // Resume listening if needed
        }
        mediaPlayer.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        porcupineManager.stop()
        porcupineManager.delete()
    }
}