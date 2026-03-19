package com.meta.wearable.dat.externalsampleapps.cameraaccess.domain.usecase

import com.meta.wearable.dat.externalsampleapps.cameraaccess.domain.repository.VideoRecorder
import java.io.File

class StopRecordingUseCase(
    private val videoRecorder: VideoRecorder
) {
    suspend operator fun invoke(): Result<File> {
        return videoRecorder.stop()
    }
}
