package com.example.wakeai

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object OpenAIApiService {
    private const val BASE_URL = "https://api.openai.com/v1"
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    private const val OPEN_AI_API_KEY = "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"

    fun getChatGPTResponse(prompt: String) {

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
                //generateAudioFromText(responseText)
            }
        })
    }

}