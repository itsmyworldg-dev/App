package com.example.voice

import android.util.Log
import com.example.BuildConfig
import com.example.ui.ThikanaTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * High-performance Gen Z AI voice navigation engine for Mera Thikaana.
 * Blends sub-second offline intent classification for navigation with
 * real-time Gemini AI (gemini-3.1-flash-live-preview / gemini-3.5-flash)
 * for witty conversational Q&A and local Ranchi recommendations.
 */
class GeminiVoiceService {

    sealed class VoiceCommandResult {
        data class Navigate(val tab: ThikanaTab, val genZReply: String) : VoiceCommandResult()
        data class ExecuteAction(val action: String, val genZReply: String) : VoiceCommandResult()
        data class Conversation(val reply: String) : VoiceCommandResult()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val liveModel = "gemini-3.1-flash-live-preview"
    private val fallbackModel = "gemini-3.5-flash"

    /**
     * Quickly classifies instant navigation intents locally so user doesn't wait
     * on network latency for direct commands.
     */
    fun checkLocalNavigationIntent(query: String): VoiceCommandResult? {
        val q = query.lowercase().trim()

        // Turn-by-Turn GPS Navigation Guidance Commands (e.g. "go left", "take turn", "next turn", "which way")
        if (q.contains("left") || q.contains("right") || q.contains("turn") ||
            q.contains("go left") || q.contains("take turn") || q.contains("which way") ||
            q.contains("next turn") || q.contains("where to turn") || q.contains("where do i turn") ||
            q.contains("repeat direction") || q.contains("repeat turn") || q.contains("guidance")
        ) {
            return VoiceCommandResult.ExecuteAction(
                "REPEAT_NAV_TURN",
                "Checking your live turn guidance! Follow the spoken directions and drive safe."
            )
        }

        // Direct Destination Navigation (e.g. "navigate to Hundru Falls", "directions to Patratu")
        if (q.startsWith("navigate to ") || q.startsWith("directions to ") || q.startsWith("take me to ") || q.startsWith("route to ")) {
            val dest = q.replace("navigate to ", "")
                .replace("directions to ", "")
                .replace("take me to ", "")
                .replace("route to ", "")
                .trim()
            if (dest.equals("feed", ignoreCase = true) || dest.equals("the feed", ignoreCase = true) || dest.equals("home", ignoreCase = true)) {
                return VoiceCommandResult.Navigate(ThikanaTab.FEED, "Bet! Taking you straight to the Feed rn, no cap.")
            }
            if (dest.isNotBlank()) {
                return VoiceCommandResult.ExecuteAction(
                    "START_NAV:$dest",
                    "Locked in! Launching turn-by-turn navigation for $dest. Spoken directions will guide each turn!"
                )
            }
        }

        // Feed navigation
        if (q.contains("feed") || q.contains("home") || q.contains("main") || q.contains("timeline") ||
            q.contains("go home") || q.contains("show feed") || q.contains("for you") || q.contains("foryou")
        ) {
            val phrases = listOf(
                "Bet! Taking you straight to the Feed rn, no cap.",
                "Say less! Loading up the freshest feed drops.",
                "On it bestie! Back to the main feed.",
                "Feed locked and loaded, enjoy the vibes!"
            )
            return VoiceCommandResult.Navigate(ThikanaTab.FEED, phrases.random())
        }

        // Discover / Explore / Search / Waterfalls / Food / Spots
        if (q.contains("explore") || q.contains("search") || q.contains("discover") ||
            q.contains("spot") || q.contains("waterfall") || q.contains("food") ||
            q.contains("places") || q.contains("find") || q.contains("cafes") ||
            q.contains("where to go") || q.contains("hangout")
        ) {
            val phrases = listOf(
                "Say less! Pulling up Discover so you can find the sickest spots in Ranchi.",
                "Discover mode activated! Let's uncover some hidden gems, fr fr.",
                "Bet! Headed to Discover — waterfalls, viewpoints, and pure aesthetic.",
                "Locked in! Exploring Ranchi's best thikanas rn."
            )
            return VoiceCommandResult.Navigate(ThikanaTab.DISCOVER, phrases.random())
        }

        // Create / Post / Upload
        if (q.contains("create") || q.contains("post") || q.contains("upload") ||
            q.contains("drop") || q.contains("camera") || q.contains("add post") ||
            q.contains("new post") || q.contains("share") || q.contains("publish")
        ) {
            val phrases = listOf(
                "Vibe check passed! Opening create so you can drop that fire content, slay!",
                "Say less fam! Opening upload chooser, let's post.",
                "Bet! Time to flex on the feed. Launching creator mode!",
                "Gotchu! Ready for your post drop, no cap."
            )
            return VoiceCommandResult.Navigate(ThikanaTab.CREATE, phrases.random())
        }

        // Messages / Notifications / Activity
        if (q.contains("notification") || q.contains("activity") || q.contains("alerts") ||
            q.contains("who tagged") || q.contains("likes") || q.contains("pings") ||
            q.contains("updates") || q.contains("notifs") || q.contains("message") ||
            q.contains("dm") || q.contains("chat")
        ) {
            val phrases = listOf(
                "Pulling up your messages! Let's see who's sliding into your DMs.",
                "Say less! Checking your message stream, you're poppin off.",
                "Bet! Opening messages. All eyes on you!",
                "Gotchu! Showing your latest chats and hype."
            )
            return VoiceCommandResult.Navigate(ThikanaTab.MESSAGES, phrases.random())
        }

        // Profile / Account
        if (q.contains("profile") || q.contains("my account") || q.contains("my posts") ||
            q.contains("my bio") || q.contains("settings") || q.contains("who am i") ||
            q.contains("my page")
        ) {
            val phrases = listOf(
                "Here is your profile aesthetic! Looking iconic, fr fr.",
                "Say less! Heading to your profile, you're the main character.",
                "Profile unlocked! Flex that bio and those thikana memories.",
                "Bet! Taking you right to your personal hub."
            )
            return VoiceCommandResult.Navigate(ThikanaTab.PROFILE, phrases.random())
        }

        // Scrolling actions
        if (q.contains("scroll down") || q.contains("scroll") || q.contains("next") || q.contains("more")) {
            return VoiceCommandResult.ExecuteAction(
                "SCROLL_DOWN",
                "Scrolling down for more heat, keep vibing!"
            )
        }
        if (q.contains("scroll up") || q.contains("top") || q.contains("go up")) {
            return VoiceCommandResult.ExecuteAction(
                "SCROLL_UP",
                "Zooming back to the top!"
            )
        }

        // Refresh / Reload
        if (q.contains("refresh") || q.contains("reload") || q.contains("restart")) {
            return VoiceCommandResult.ExecuteAction(
                "RELOAD",
                "Refreshing the page for the freshest tea!"
            )
        }

        return null
    }

    /**
     * Queries Gemini live model with fallback to generate a snappy Gen Z response.
     */
    suspend fun askGemini(prompt: String): VoiceCommandResult = withContext(Dispatchers.IO) {
        val localMatch = checkLocalNavigationIntent(prompt)
        if (localMatch != null) {
            return@withContext localMatch
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // High quality fallback if API key is not configured in secrets yet
            return@withContext VoiceCommandResult.Conversation(
                getMockGenZResponse(prompt)
            )
        }

        val systemInstruction = """
            You are VibeNav, the ultra-cool, witty Gen Z AI voice co-pilot for "Mera Thikaana" (a local social app for discovering Ranchi's waterfalls, street food, and scenic viewpoints).
            Guidelines:
            1. Speak in genuine, punchy Gen Z slang ('say less', 'no cap', 'fr fr', 'bet', 'slay', 'ate and left no crumbs', 'bussin', 'vibe check', 'lowkey', 'highkey').
            2. Keep responses short: exactly 1 or 2 concise, energetic sentences.
            3. If the user asks about going somewhere in the app, tell them to check Feed, Explore, Create, or Profile.
            4. If they ask for Ranchi spots, recommend iconic locations like Dassam Falls, Hundru Falls, Patratu Valley, Tagore Hill, or tasty litti chokha.
        """.trimIndent()

        // Try primary live model first, then fallback model
        val reply = requestGeminiModel(liveModel, prompt, systemInstruction, apiKey)
            ?: requestGeminiModel(fallbackModel, prompt, systemInstruction, apiKey)
            ?: getMockGenZResponse(prompt)

        VoiceCommandResult.Conversation(reply)
    }

    private fun requestGeminiModel(
        modelName: String,
        prompt: String,
        systemInstruction: String,
        apiKey: String
    ): String? {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.75)
                    put("maxOutputTokens", 120)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("GeminiVoiceService", "Model $modelName returned code: ${response.code}")
                    return null
                }
                val bodyString = response.body?.string() ?: return null
                val rootJson = JSONObject(bodyString)
                val candidates = rootJson.optJSONArray("candidates") ?: return null
                val firstCandidate = candidates.optJSONObject(0) ?: return null
                val content = firstCandidate.optJSONObject("content") ?: return null
                val parts = content.optJSONArray("parts") ?: return null
                val firstPart = parts.optJSONObject(0) ?: return null
                val text = firstPart.optString("text")
                if (text.isNotBlank()) text.trim() else null
            }
        } catch (e: Exception) {
            Log.e("GeminiVoiceService", "Error calling $modelName: ${e.message}")
            null
        }
    }

    private fun getMockGenZResponse(prompt: String): String {
        val p = prompt.lowercase()
        return when {
            p.contains("waterfall") || p.contains("dassam") || p.contains("hundru") ->
                "Dassam and Hundru are peak aesthetics rn, the water flow is immaculate. Go snap some fire stories, fr fr!"
            p.contains("food") || p.contains("momo") || p.contains("eat") || p.contains("litti") ->
                "Lowkey craving hot litti chokha or street momos at Morabadi! That spot hits different every single time, no cap."
            p.contains("patratu") || p.contains("drive") || p.contains("valley") ->
                "Patratu Valley sunset drive is an absolute 10 out of 10. Bring the aux cord and soak in the cinematic curves, bestie."
            p.contains("vibe") || p.contains("today") || p.contains("how are you") ->
                "We are thriving and radiating immaculate main character energy! Where are we exploring today?"
            else ->
                "Say less! I gotchu. Explore the feed or search up Ranchi's top spots — we're making memories today, fr fr!"
        }
    }
}
