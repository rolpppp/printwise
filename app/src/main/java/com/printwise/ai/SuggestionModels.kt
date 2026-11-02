package com.printwise.ai

import android.graphics.RectF

data class SuggestedCrop(
    val sourceRect: RectF,
    val confidence: Float? = null
)

data class SuggestedClass(
    val label: String,
    val cropIndexes: List<Int> = emptyList(),
    val confidence: Float? = null
)

interface CropSuggestionService {
    suspend fun suggestCrops(): List<SuggestedCrop>
}

interface ClassSuggestionService {
    suspend fun suggestClasses(): List<SuggestedClass>
}

class SuggestionException(message: String, cause: Throwable? = null) : Exception(message, cause)
