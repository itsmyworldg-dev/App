package com.example.storyeditor.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed class AiEditResult {
    data class SuccessImage(val bitmap: Bitmap, val description: String) : AiEditResult()
    data class SuccessStyle(val styleTitle: String, val suggestedAdjustments: String) : AiEditResult()
    data class Error(val message: String) : AiEditResult()
}

object GeminiEditorService {
    private const val TAG = "GeminiEditor"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val PRESET_PROMPTS = listOf(
        "Cyberpunk Neon Glow" to "Transform this into a vibrant futuristic cyberpunk scene with luminous cyan & magenta neon lighting, high-contrast glow, and aesthetic wet reflections.",
        "Golden Hour Warmth" to "Enhance this with sun-drenched golden hour lighting, radiant amber lens flare, soft skin tones, and dreamy 35mm film depth.",
        "90s Vintage Film" to "Add authentic 90s vintage film camera aesthetic, Kodak Portra warmth, subtle analog grain, and nostalgic color grading.",
        "Anime Watercolor" to "Stylize this photo in Makoto Shinkai anime aesthetic, luminous dramatic sky, vibrant colors, and soft painterly highlights.",
        "Studio Editorial" to "Elevate into a high-fashion luxury magazine editorial portrait with dramatic contrast, crisp rim lighting, and elegant studio tones.",
        "Pastel Dream Y2K" to "Apply aesthetic Y2K pastel glitter tones, soft lilac and baby pink hues, gentle diffusion glow, and retro sparkle."
    )

    suspend fun editWithGemini(
        sourceBitmap: Bitmap,
        userPrompt: String
    ): AiEditResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext AiEditResult.Error(
                "Gemini API key is not configured. Add your key in the AI Studio Secrets panel or .env file to enable cloud AI editing."
            )
        }

        try {
            // Compress bitmap for multimodal prompt payload
            val outputStream = ByteArrayOutputStream()
            // Downscale for network efficiency if large
            val maxDimension = 1024
            val scale = if (sourceBitmap.width > maxDimension || sourceBitmap.height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / sourceBitmap.width, maxDimension.toFloat() / sourceBitmap.height)
                ratio
            } else 1.0f

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    sourceBitmap,
                    (sourceBitmap.width * scale).toInt(),
                    (sourceBitmap.height * scale).toInt(),
                    true
                )
            } else {
                sourceBitmap
            }

            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            // Try image-to-image with gemini-2.5-flash-image
            val jsonPayload = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Edit and enhance this Instagram story/post image: $userPrompt. Return the visual result.")
                            })
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inlineData", inlineData)
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    val modalities = JSONArray().apply {
                        put("IMAGE")
                        put("TEXT")
                    }
                    put("responseModalities", modalities)
                }
                put("generationConfig", generationConfig)
            }

            val request = Request.Builder()
                .url("$BASE_URL/gemini-2.5-flash-image:generateContent?key=$apiKey")
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(TAG, "Image endpoint response code: ${response.code}, body: $responseBody")
                // Fallback to gemini-3.5-flash text styling guidance
                return@withContext fallbackTextGuidance(scaledBitmap, userPrompt, apiKey)
            }

            val responseJson = JSONObject(responseBody)
            val candidates = responseJson.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")

            var foundBitmap: Bitmap? = null
            var textDescription = ""

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("inlineData")) {
                        val dataObj = part.getJSONObject("inlineData")
                        val b64 = dataObj.getString("data")
                        val decodedBytes = Base64.decode(b64, Base64.DEFAULT)
                        foundBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    }
                    if (part.has("text")) {
                        textDescription += part.getString("text") + " "
                    }
                }
            }

            if (foundBitmap != null) {
                AiEditResult.SuccessImage(foundBitmap, textDescription.trim().ifEmpty { "AI Enhanced" })
            } else {
                fallbackTextGuidance(scaledBitmap, userPrompt, apiKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini editing error", e)
            AiEditResult.Error("Gemini error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }

    private fun fallbackTextGuidance(
        scaledBitmap: Bitmap,
        userPrompt: String,
        apiKey: String
    ): AiEditResult {
        try {
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val jsonPayload = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are an expert Instagram Story & Post aesthetic editor. Analyze this image and the user prompt: '$userPrompt'. Give 3 short, actionable creative filter adjustments (e.g. Warmth +20%, Saturation +15%, Cinematic Noir, Grain) and an aesthetic caption.")
                            })
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inlineData", inlineData)
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
            }

            val request = Request.Builder()
                .url("$BASE_URL/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return AiEditResult.Error("AI call failed (${response.code}). Please check API key.")
            }

            val responseJson = JSONObject(responseBody)
            val text = responseJson.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: "Applied trending aesthetic look."

            return AiEditResult.SuccessStyle(
                styleTitle = "Gemini Creative Suggestion",
                suggestedAdjustments = text
            )
        } catch (e: Exception) {
            return AiEditResult.Error("Failed to reach Gemini: ${e.message}")
        }
    }
}
