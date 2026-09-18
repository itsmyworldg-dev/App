package com.example.editor

import android.content.Context
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

data class GeminiStoryPrompt(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val prompt: String
)

object GeminiStoryEngine {
    private const val TAG = "GeminiStoryEngine"
    private const val PREFS_NAME = "gemini_story_prefs"
    private const val PREF_CUSTOM_API_KEY = "custom_gemini_api_key"

    // Supported model per skill guidance for image generation/editing tasks
    private const val MODEL_NAME = "gemini-2.5-flash-image"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val curatedPrompts = listOf(
        GeminiStoryPrompt(
            id = "sunset_glow",
            title = "Golden Sunset",
            subtitle = "Amber lens flare & Kodak Portra warmth",
            emoji = "🌅",
            prompt = "Enhance this photo with stunning Instagram story golden hour sunset lighting, warm amber lens flare, soft cinematic glow, radiant Kodak Portra color grading, pristine clarity."
        ),
        GeminiStoryPrompt(
            id = "cyberpunk_neon",
            title = "Cyberpunk Neon",
            subtitle = "Magenta & cyan neon rim lights, night vibe",
            emoji = "⚡",
            prompt = "Reimagine this photo with cinematic cyberpunk aesthetics, glowing magenta and cyan neon rim lighting, futuristic night vibe, reflective wet surfaces, high contrast dramatic feel."
        ),
        GeminiStoryPrompt(
            id = "anime_ghibli",
            title = "Anime Aesthetic",
            subtitle = "Dreamy Studio Ghibli watercolor style",
            emoji = "🎨",
            prompt = "Transform this photo into an enchanting hand-painted Japanese anime watercolor painting reminiscent of Studio Ghibli, with lush vibrant colors, dreamy clouds, and whimsical painterly textures."
        ),
        GeminiStoryPrompt(
            id = "vintage_90s",
            title = "90s Disposable",
            subtitle = "35mm grain, retro light leak & nostalgia",
            emoji = "📸",
            prompt = "Add authentic 1990s disposable camera aesthetic with realistic 35mm film grain, subtle light leak on the edge, warm nostalgic tones, and slightly faded retro highlights."
        ),
        GeminiStoryPrompt(
            id = "editorial_vogue",
            title = "Luxury Editorial",
            subtitle = "Vogue magazine studio rim lighting",
            emoji = "👑",
            prompt = "Transform into a luxury high-fashion magazine cover portrait, dramatic studio rim lighting, crisp contrast, refined shadows, clean modern magazine-worthy color grading."
        ),
        GeminiStoryPrompt(
            id = "fairycore_dream",
            title = "Fairycore Glow",
            subtitle = "Pastel haze, ethereal starlight sparkles",
            emoji = "🌸",
            prompt = "Infuse with magical dreamy fairycore aesthetic, ethereal pastel pink and lavender haze, soft diffused lighting, gentle glowing starlight sparkles, fantasy atmosphere."
        ),
        GeminiStoryPrompt(
            id = "cinematic_teal",
            title = "Blockbuster Cinema",
            subtitle = "Hollywood teal & orange dramatic grade",
            emoji = "🎬",
            prompt = "Color grade with Hollywood blockbuster cinematic teal and orange tones, dramatic anamorphic lens depth, moody contrast, and cinematic movie still quality."
        ),
        GeminiStoryPrompt(
            id = "dramatic_bw",
            title = "Timeless Noir",
            subtitle = "Fine-art silver monochrome & deep blacks",
            emoji = "🖤",
            prompt = "Convert into stunning fine-art black and white photography with deep rich blacks, radiant highlights, silver midtones, and dramatic lighting."
        )
    )

    fun getEffectiveApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customKey = prefs.getString(PREF_CUSTOM_API_KEY, "")?.trim() ?: ""
        if (customKey.isNotBlank()) return customKey

        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun setCustomApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_CUSTOM_API_KEY, key.trim()).apply()
    }

    suspend fun editImageWithGemini(
        context: Context,
        sourceBitmap: Bitmap,
        prompt: String,
        isStoryRatio: Boolean = true
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is required. Tap the key icon to configure your key.")
            )
        }

        try {
            // Resize source bitmap to optimal size for upload (max 1024px to remain fast and responsive)
            val maxDimension = 1024
            val scale = (maxDimension.toFloat() / maxOf(sourceBitmap.width, sourceBitmap.height)).coerceAtMost(1.0f)
            val scaledWidth = (sourceBitmap.width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (sourceBitmap.height * scale).toInt().coerceAtLeast(1)
            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(sourceBitmap, scaledWidth, scaledHeight, true)
            } else {
                sourceBitmap
            }

            // Encode to JPEG Base64
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            // Construct REST payload as required by gemini-api skill
            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()

                // Text prompt part
                val textPart = JSONObject().apply {
                    put("text", prompt)
                }
                partsArray.put(textPart)

                // Inline image part
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Data)
                }
                val imagePart = JSONObject().apply {
                    put("inlineData", inlineData)
                }
                partsArray.put(imagePart)

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                put("contents", contentsArray)

                // Generation Config with response modalities ["TEXT", "IMAGE"]
                val genConfig = JSONObject().apply {
                    val modalities = JSONArray().apply {
                        put("TEXT")
                        put("IMAGE")
                    }
                    put("responseModalities", modalities)
                    val imageConfig = JSONObject().apply {
                        put("aspectRatio", if (isStoryRatio) "9:16" else "1:1")
                    }
                    put("imageConfig", imageConfig)
                }
                put("generationConfig", genConfig)
            }

            val endpoint = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"
            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API failed with code ${response.code}: $responseString")
                val errMsg = try {
                    val errJson = JSONObject(responseString)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP error ${response.code}"
                } catch (_: Exception) {
                    "API error (Code ${response.code})"
                }
                return@withContext Result.failure(Exception(errMsg))
            }

            // Parse response to find generated image candidate
            val rootJson = JSONObject(responseString)
            val candidates = rootJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("Gemini returned no candidates"))
            }

            var generatedBitmap: Bitmap? = null
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val inlineDataObj = part.optJSONObject("inlineData")
                    if (inlineDataObj != null) {
                        val base64Img = inlineDataObj.optString("data")
                        if (base64Img.isNotBlank()) {
                            val decodedBytes = Base64.decode(base64Img, Base64.DEFAULT)
                            generatedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            if (generatedBitmap != null) break
                        }
                    }
                }
            }

            if (generatedBitmap != null) {
                Result.success(generatedBitmap)
            } else {
                // If model returned text only without image, notify user
                val textResponse = parts?.optJSONObject(0)?.optString("text") ?: ""
                Result.failure(Exception(if (textResponse.isNotBlank()) textResponse else "No image was returned by Gemini"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI image edit", e)
            Result.failure(e)
        }
    }
}
