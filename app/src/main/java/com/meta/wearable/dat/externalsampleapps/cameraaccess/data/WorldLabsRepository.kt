package com.meta.wearable.dat.externalsampleapps.cameraaccess.data

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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WorldLabsRepository {

    private val client = OkHttpClient()

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
            
            // Looking at the screenshot, the exact keys are:
            // {"media_asset":{"media_asset_id":"..."}, ... "upload_info":{"upload_url":"..."}}
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
            // Looking at the "required_headers" in the screenshot, WorldLabs expects x-goog-content-length-range: 0,104857600
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

    suspend fun pollUntilReady(operationId: String): String {
        val cleanOpId = operationId.removePrefix("operations/")
        
        val url = "https://api.worldlabs.ai/marble/v1/operations/$cleanOpId"
        val request = Request.Builder()
            .url(url)
            .addHeader("WLT-Api-Key", BuildConfig.WORLD_LABS_API_KEY)
            .get()
            .build()

        var attempts = 0
        while (attempts < 60) {
            val response = client.newCall(request).await()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("Polling Error (Code ${response.code}): $errBody")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            val jsonResponse = JSONObject(responseBody)
            
            if (jsonResponse.has("error") && !jsonResponse.isNull("error")) {
                throw IOException("Error generating world: ${jsonResponse.getJSONObject("error").optString("message", "Unknown error")}")
            }

            val done = jsonResponse.optBoolean("done", false)
            if (done) {
                if (jsonResponse.has("response")) {
                    val responseObj = jsonResponse.getJSONObject("response")
                    return responseObj.optString("world_marble_url", responseObj.optString("marbleUrl", ""))
                } else {
                    throw IOException("No response object found when done is true. Raw: $responseBody")
                }
            }

            delay(10000L)
            attempts++
        }
        
        throw IOException("Timeout polling for world generation")
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
