package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.cameraaccess.data.WorldLabsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ConvertUiState {
    object Idle : ConvertUiState()
    data class Loading(val message: String) : ConvertUiState()
    data class Success(val marbleUrl: String) : ConvertUiState()
    data class Error(val message: String) : ConvertUiState()
}

class ConvertViewModel : ViewModel() {
    private val repo = WorldLabsRepository()
    
    private val _uiState = MutableStateFlow<ConvertUiState>(ConvertUiState.Idle)
    val uiState: StateFlow<ConvertUiState> = _uiState.asStateFlow()

    fun convertToWorld(videoFile: File) {
        viewModelScope.launch {
            try {
                _uiState.value = ConvertUiState.Loading("Uploading video...")
                val (assetId, signedUrl) = repo.prepareUpload(videoFile.name)

                _uiState.value = ConvertUiState.Loading("Sending to World Labs...")
                repo.uploadVideo(signedUrl, videoFile)

                _uiState.value = ConvertUiState.Loading("Generating your 3D world...")
                val operationId = repo.generateWorld(assetId)

                _uiState.value = ConvertUiState.Loading("This takes ~5 mins, please wait...")
                val marbleUrl = repo.pollUntilReady(operationId)

                _uiState.value = ConvertUiState.Success(marbleUrl)
            } catch (e: Exception) {
                _uiState.value = ConvertUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetState() {
        _uiState.value = ConvertUiState.Idle
    }
}
