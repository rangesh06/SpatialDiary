package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.cameraaccess.data.WorldLabsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ConvertUiState {
    object Idle : ConvertUiState()
    data class Loading(val message: String) : ConvertUiState()
    data class Success(val splatUrl: String) : ConvertUiState()
    data class Error(val message: String) : ConvertUiState()
}

class ConvertViewModel : ViewModel() {
    private val repo = WorldLabsRepository()
    
    private val _uiState = MutableStateFlow<ConvertUiState>(ConvertUiState.Idle)
    val uiState: StateFlow<ConvertUiState> = _uiState.asStateFlow()

    fun convertToWorld(videoFile: File) {
        viewModelScope.launch {
            try {
                _uiState.value = ConvertUiState.Loading("Uploading your video")
                val (assetId, signedUrl) = repo.prepareUpload(videoFile.name)

                _uiState.value = ConvertUiState.Loading("Sending to World Labs")
                repo.uploadVideo(signedUrl, videoFile)

                _uiState.value = ConvertUiState.Loading("Generating your 3D world")
                val operationId = repo.generateWorld(assetId)

                // Give World Labs a few seconds to register the operation before polling
                _uiState.value = ConvertUiState.Loading("Starting world creation")
                delay(5_000)

                val splatUrl = repo.pollUntilReady(operationId) { progress ->
                    _uiState.value = ConvertUiState.Loading(progress)
                }

                Log.d("WorldLabs", "SPZ URL: $splatUrl")

                _uiState.value = ConvertUiState.Success(splatUrl)
            } catch (e: Exception) {
                _uiState.value = ConvertUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetState() {
        _uiState.value = ConvertUiState.Idle
    }
}