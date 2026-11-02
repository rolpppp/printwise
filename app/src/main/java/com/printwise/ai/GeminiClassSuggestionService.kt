package com.printwise.ai

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiClassSuggestionService(
    private val bitmap: Bitmap,
    private val endpoint: String
) : ClassSuggestionService {
    override suspend fun suggestClasses(): List<SuggestedClass> {
        if (endpoint.isBlank()) {
            throw SuggestionException("Gemini backend endpoint is not configured")
        }
        val requestUrl = "${endpoint.trimEnd('/')}/v1/class-suggestions"
        val encoded = bitmap.toJpegBase64()
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { output ->
                output.write(JSONObject().put("imageBase64", encoded).toString().toByteArray())
            }
            if (connection.responseCode !in 200..299) {
                throw SuggestionException("Gemini backend returned HTTP ${connection.responseCode}")
            }
            return parseResponse(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (error: IOException) {
            throw SuggestionException("Gemini backend request failed", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(body: String): List<SuggestedClass> {
        val classes = JSONObject(body).optJSONArray("classes")
            ?: throw SuggestionException("Gemini response is missing classes")
        return classes.toSuggestedClasses()
    }
}

private fun JSONArray.toSuggestedClasses(): List<SuggestedClass> {
    return (0 until length()).map { index ->
        val item = optJSONObject(index)
            ?: throw SuggestionException("Gemini returned an invalid class")
        val label = item.optString("label").trim()
        if (label.isEmpty()) throw SuggestionException("Gemini returned an empty class label")
        val crops = item.optJSONArray("cropIndexes") ?: JSONArray()
        SuggestedClass(
            label = label,
            cropIndexes = (0 until crops.length()).map { crops.getInt(it) },
            confidence = if (item.has("confidence")) item.optDouble("confidence").toFloat() else null
        )
    }
}

private fun Bitmap.toJpegBase64(): String {
    val bytes = java.io.ByteArrayOutputStream().also {
        compress(Bitmap.CompressFormat.JPEG, 90, it)
    }.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}
