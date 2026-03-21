package com.meta.wearable.dat.externalsampleapps.cameraaccess.data

import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WorldLabsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun prepareUpload(fileName: String): Pair<String, String> {
        val url = "https://api.worldlabs.ai/marble/v1/media-assets:prepare_upload"
        val json = JSONObject()
        json.put("file_name", fileName)
        json.put("kind", "video")
        json.put("extension", "mp4")

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("WLT-Api-Key", BuildConfig.WORLD_LABS_API_KEY)
            .post(body)
            .build()

        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw IOException("API Error (Code ${response.code}): $errBody")
        }

        val responseBody = response.body?.string() ?: throw IOException("Empty response body")
        
        try {
            val jsonResponse = JSONObject(responseBody)
            
            val mediaAsset = jsonResponse.optJSONObject("media_asset")
            val mediaAssetId = mediaAsset?.optString("media_asset_id") 
                ?: jsonResponse.optString("media_asset_id", "")
            
            val uploadInfo = jsonResponse.optJSONObject("upload_info")
            val uploadUrl = uploadInfo?.optString("upload_url") 
                ?: jsonResponse.optString("upload_url", "")

            if (mediaAssetId.isEmpty() || uploadUrl.isEmpty()) {
                throw IOException("Missing keys in response: $responseBody")
            }

            return Pair(mediaAssetId, uploadUrl)
        } catch (e: Exception) {
            throw IOException("Failed to parse API response: $responseBody", e)
        }
    }

    suspend fun uploadVideo(signedUrl: String, videoFile: File) {
        val body = videoFile.asRequestBody("video/mp4".toMediaType())
        val request = Request.Builder()
            .url(signedUrl)
            .addHeader("x-goog-content-length-range", "0,104857600") 
            .put(body)
            .build()

        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw IOException("Upload Error (Code ${response.code}): $errBody")
        }
    }

    suspend fun generateWorld(mediaAssetId: String): String {
        val url = "https://api.worldlabs.ai/marble/v1/worlds:generate"
        
        val videoPromptJson = JSONObject()
        videoPromptJson.put("source", "media_asset")
        videoPromptJson.put("media_asset_id", mediaAssetId)
        
        val worldPromptJson = JSONObject()
        worldPromptJson.put("type", "video")
        worldPromptJson.put("video_prompt", videoPromptJson)

        val json = JSONObject()
        json.put("model", "Marble 0.1-mini")
        json.put("world_prompt", worldPromptJson)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("WLT-Api-Key", BuildConfig.WORLD_LABS_API_KEY)
            .post(body)
            .build()

        val response = client.newCall(request).await()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw IOException("Generate Error (Code ${response.code}): $errBody")
        }

        val responseBody = response.body?.string() ?: throw IOException("Empty response body")
        
        try {
            val jsonResponse = JSONObject(responseBody)
            if (jsonResponse.has("operation_id")) {
                return jsonResponse.getString("operation_id")
            } else if (jsonResponse.has("name")) { 
                return jsonResponse.getString("name")
            } else if (jsonResponse.has("id")) {
                return jsonResponse.getString("id")
            } else {
                throw IOException("No operation ID found in response: $responseBody")
            }
        } catch (e: Exception) {
            throw IOException("Failed to parse API response: $responseBody", e)
        }
    }

    suspend fun pollUntilReady(
        operationId: String,
        onProgress: (String) -> Unit = {}
    ): String {
        val cleanOpId = operationId.removePrefix("operations/")
        val url = "https://api.worldlabs.ai/marble/v1/operations/$cleanOpId"
        
        val maxAttempts = 90
        var failStreak = 0
        val maxFailStreak = 5

        repeat(maxAttempts) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("WLT-Api-Key", BuildConfig.WORLD_LABS_API_KEY)
                    .get()
                    .build()

                val response = client.newCall(request).await()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    // Throw distinct exceptions for unrecoverable 4xx errors
                    if (response.code in 400..499 && response.code != 408 && response.code != 429) {
                        throw IllegalArgumentException("Client Error (Code ${response.code}): $errBody")
                    }
                    throw IOException("Server Error (Code ${response.code}): $errBody")
                }
                
                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val jsonResponse = JSONObject(responseBody)
                
                // Reset fail streak on successful API response
                failStreak = 0
                
                // Update progress using the callback if metadata exists
                val description = jsonResponse
                    .optJSONObject("metadata")
                    ?.optJSONObject("progress")
                    ?.optString("description", "Processing...") ?: "Processing..."
                onProgress(description)

                // Check for terminal error
                if (jsonResponse.has("error") && !jsonResponse.isNull("error")) {
                    throw IllegalStateException("Error generating world: ${jsonResponse.getJSONObject("error").optString("message", "Unknown error")}")
                }

                val done = jsonResponse.optBoolean("done", false)
                if (done) {
                    if (jsonResponse.has("response")) {
                        val responseObj = jsonResponse.getJSONObject("response")
                        return responseObj.getJSONObject("assets")
                            .getJSONObject("splats")
                            .getJSONObject("spz_urls")
                            .getString("500k")
                    } else {
                        throw IllegalStateException("No response object found when done is true. Raw: $responseBody")
                    }
                }
            } catch (e: Exception) {
                // Do not retry if the error is terminal (client error, semantic error)
                if (e is IllegalArgumentException || e is IllegalStateException) {
                    throw e
                }

                failStreak++
                Log.w("WorldLabs", "Poll attempt $attempt failed ($failStreak streak): ${e.message}")
                
                // Crash only after crossing consecutive failure limit
                if (failStreak >= maxFailStreak) {
                    throw Exception("Connection lost after $maxFailStreak failed attempts. Please check your connection. Details: ${e.message}")
                }
                
                // Otherwise update UI and retry
                onProgress("Retrying connection ($failStreak/$maxFailStreak)...")
            }

            delay(10_000L)
        }
        
        throw Exception("Timed out after ${maxAttempts * 10 / 60} minutes. Please try again.")
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
    
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (ex: Throwable) {
            // Ignore
        }
    }
}