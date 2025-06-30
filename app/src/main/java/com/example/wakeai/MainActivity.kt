package com.example.wakeai

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.pm.PackageManager
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

class MainActivity : AppCompatActivity() {

    private val PORCUPINE_API_KEY = "9mluvEpgDHKo6dIRdHOv3alIMQBsVY5dDCcf3NPkkpkqaluJNuKYXQ=="
    private lateinit var porcupineManager: PorcupineManager
    private lateinit var binding: ActivityMainBinding
    private val AUDIO_PERMISSION_CODE = 101

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
            OpenAIApiService.getChatGPTResponse("give me a one-sentece joke")
            //requestAudioPermission()
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

    private val porcupineCallback = PorcupineManagerCallback { keywordIndex ->
        if (keywordIndex == 0) {
            Log.d("VoiceDetector", "detected")
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

    override fun onDestroy() {
        super.onDestroy()
        porcupineManager.stop()
        porcupineManager.delete()
    }
}