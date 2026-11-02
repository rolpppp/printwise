package com.printwise.ai

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.subjectsegmentation.SubjectSegmentation
import com.google.mlkit.vision.subjectsegmentation.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

class MlKitCropSuggestionService(
    private val bitmap: Bitmap
) : CropSuggestionService {
    override suspend fun suggestCrops(): List<SuggestedCrop> {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val result = SubjectSegmentation
            .getClient(options)
            .process(InputImage.fromBitmap(bitmap, 0))
            .await()

        // ML Kit exposes the foreground bitmap, not authoritative crop boundaries.
        // The returned bounds are a suggestion and remain editable in the crop screen.
        val foreground = result.foregroundBitmap ?: throw SuggestionException("ML Kit returned no foreground")
        if (foreground.width == 0 || foreground.height == 0) {
            throw SuggestionException("ML Kit returned an empty foreground")
        }
        return listOf(
            SuggestedCrop(
                sourceRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            )
        )
    }
}
