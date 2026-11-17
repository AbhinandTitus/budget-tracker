package app.kanakku.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeminiException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NO_KEY, INVALID_KEY, QUOTA, MODEL_NOT_FOUND, BLOCKED, NETWORK, SERVER, EMPTY }

    /** A sentence fit to show the user. */
    val userMessage: String
        get() = when (kind) {
            Kind.NO_KEY -> "Add a Gemini API key in Settings to use the advisor."
            Kind.INVALID_KEY -> "Gemini rejected the API key. Check it in Settings."
            Kind.QUOTA -> "Gemini's usage limit for this key has been reached. Try again in a little while."
            Kind.MODEL_NOT_FOUND -> "That Gemini model is not available for this key. Choose another in Settings."
            Kind.BLOCKED -> "Gemini declined to answer that. Try rephrasing."
            Kind.NETWORK -> "Could not reach Gemini. Check the connection and try again."
            Kind.SERVER -> "Gemini is having trouble right now. Try again shortly."
            Kind.EMPTY -> "Gemini returned an empty answer. Try again."
        }
}

data class Turn(val fromUser: Boolean, val text: String)

/** Minimal client for the Gemini REST API (generateContent, models.list). */
class GeminiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        turns: List<Turn>,
        responseSchema: JsonObject? = null,
        temperature: Double = 0.6,
    ): String {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", system) }) } }
            put(
                "contents",
                buildJsonArray {
                    turns.forEach { turn ->
                        add(
                            buildJsonObject {
                                put("role", if (turn.fromUser) "user" else "model")
                                putJsonArray("parts") { add(buildJsonObject { put("text", turn.text) }) }
                            },
                        )
                    }
                },
            )
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", 8192)
                if (responseSchema != null) {
                    put("responseMimeType", "application/json")
                    put("responseSchema", responseSchema)
                }
            }
        }
        val request = Request.Builder()
            .url("$baseUrl/models/${model.removePrefix("models/")}:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val root = execute(request)
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
        if (candidate == null) {
            val reason = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
            throw GeminiException(if (reason != null) GeminiException.Kind.BLOCKED else GeminiException.Kind.EMPTY, reason ?: "no candidates")
        }
        val text = (candidate["content"]?.jsonObject?.get("parts") as? JsonArray).orEmpty()
            .map { it.jsonObject }
            .filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
            .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
            .trim()
        if (text.isEmpty()) {
            val finish = candidate["finishReason"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val blocked = finish in setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "RECITATION")
            throw GeminiException(if (blocked) GeminiException.Kind.BLOCKED else GeminiException.Kind.EMPTY, finish)
        }
        return text
    }

    /** Models this key can call with generateContent, e.g. "gemini-flash-latest". */
    suspend fun listModels(apiKey: String): List<String> {
        val request = Request.Builder().url("$baseUrl/models?pageSize=200").header("x-goog-api-key", apiKey).get().build()
        val root = execute(request)
        return (root["models"] as? JsonArray).orEmpty().map { it.jsonObject }
            .filter { m -> (m["supportedGenerationMethods"] as? JsonArray).orEmpty().any { it.jsonPrimitive.contentOrNull == "generateContent" } }
            .mapNotNull { it["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
            .filter { it.startsWith("gemini") && !it.contains("embedding") && !it.contains("image") && !it.contains("tts") && !it.contains("audio") }
            .sorted()
    }

    private suspend fun execute(request: Request): JsonObject = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(GeminiException(GeminiException.Kind.NETWORK, e.message ?: "network error"))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use { r ->
                        val text = r.body.string()
                        if (!r.isSuccessful) throw failure(r.code, text)
                        json.parseToJsonElement(text).jsonObject
                    }
                }
                if (!cont.isActive) return
                result.fold(
                    onSuccess = { cont.resume(it) },
                    onFailure = { e ->
                        cont.resumeWithException(e as? GeminiException ?: GeminiException(GeminiException.Kind.SERVER, e.message ?: "bad response"))
                    },
                )
            }
        })
    }

    private fun failure(code: Int, body: String): GeminiException {
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: "HTTP $code"
        val kind = when {
            code == 401 || code == 403 -> GeminiException.Kind.INVALID_KEY
            code == 400 && message.contains("API key", ignoreCase = true) -> GeminiException.Kind.INVALID_KEY
            code == 404 -> GeminiException.Kind.MODEL_NOT_FOUND
            code == 429 -> GeminiException.Kind.QUOTA
            code >= 500 -> GeminiException.Kind.SERVER
            else -> GeminiException.Kind.SERVER
        }
        return GeminiException(kind, message)
    }
}

/** Schema for the merchant-categorisation response. */
internal fun categorySchema(categoryIds: List<String>): JsonObject = buildJsonObject {
    put("type", "ARRAY")
    putJsonObject("items") {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("key") { put("type", "STRING") }
            putJsonObject("category") {
                put("type", "STRING")
                put("enum", buildJsonArray { categoryIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }
        }
        put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("key")); add(kotlinx.serialization.json.JsonPrimitive("category")) })
    }
}
