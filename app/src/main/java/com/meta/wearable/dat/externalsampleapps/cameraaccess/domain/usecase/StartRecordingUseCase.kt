package com.meta.wearable.dat.externalsampleapps.cameraaccess.domain.usecase

import com.meta.wearable.dat.externalsampleapps.cameraaccess.domain.repository.VideoRecorder
import java.io.File

class StartRecordingUseCase(
    private val videoRecorder: VideoRecorder
) {
    suspend operator fun invoke(width: Int, height: Int, frameRate: Int, outputFile: File): Result<Unit> {
        return videoRecorder.start(width, height, frameRate, outputFile)
    }
}
