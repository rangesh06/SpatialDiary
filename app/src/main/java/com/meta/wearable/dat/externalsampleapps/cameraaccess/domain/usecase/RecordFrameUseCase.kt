package com.meta.wearable.dat.externalsampleapps.cameraaccess.domain.usecase

import com.meta.wearable.dat.externalsampleapps.cameraaccess.domain.repository.VideoRecorder
import java.nio.ByteBuffer

class RecordFrameUseCase(
    private val videoRecorder: VideoRecorder
) {
    suspend operator fun invoke(buffer: ByteBuffer, presentationTimeUs: Long): Result<Unit> {
        return videoRecorder.recordFrame(buffer, presentationTimeUs)
    }
}
