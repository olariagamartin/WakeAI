package com.example.wakeai

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

object OpenAIApiService {
    private const val BASE_URL = "https://api.openai.com/v1"
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    private val OPEN_AI_API_KEY = BuildConfig.OPENAI_API_KEY

    fun getChatGPTResponse(prompt: String, onResult: (String) -> Unit) {

        val jsonBody = JSONObject().apply {
            put("model", "gpt-4.1")
            put("input", prompt)
        }

        val requestBody = jsonBody.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url("$BASE_URL/responses")
            .header("Authorization", "Bearer $OPEN_AI_API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* handle failure */ }

            override fun onResponse(call: Call, response: Response) {
                val responseText = JSONObject(response.body!!.string())
                    .getJSONArray("output")
                    .getJSONObject(0)
                    .getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")

                Log.d("OpenAIApiService", "response: $responseText")
                onResult(responseText)
            }
        })
    }

    fun sendAudioToWhisperAPI(audioData: ByteArray, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", "speech.wav",
                    RequestBody.create("audio/wav".toMediaTypeOrNull(), audioData)
                )
                .addFormDataPart("model", "whisper-1")
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $OPEN_AI_API_KEY")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("Whisper API request failed: ${response.body?.string()}")
                } else {
                    val jsonResponse = JSONObject(response.body?.string() ?: "")
                    val transcribedText = jsonResponse.getString("text")
                    println("Transcribed Text: $transcribedText")

                    onResult(transcribedText)
                }
            }
        }
    }

    fun generateAudioFromText(text: String, onResult : (InputStream) -> Unit) {
        val client = OkHttpClient()

        val jsonBody = JSONObject().apply {
            put("model", "tts-1") // or latest TTS model available
            put("input", text)
            put("voice", "nova") // pick any voice provided
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .header("Authorization", "Bearer $OPEN_AI_API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* handle failure */ }

            override fun onResponse(call: Call, response: Response) {
                val audioStream = response.body!!.byteStream()
                onResult(audioStream)
            }
        })
    }

}