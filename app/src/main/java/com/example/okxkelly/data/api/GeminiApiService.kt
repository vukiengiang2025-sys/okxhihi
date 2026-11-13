package com.example.okxkelly.data.api

import com.example.okxkelly.data.model.GeminiRequest
import com.example.okxkelly.data.model.GeminiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Header

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.6-flash:generateContent")
    suspend fun generateContent(
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
