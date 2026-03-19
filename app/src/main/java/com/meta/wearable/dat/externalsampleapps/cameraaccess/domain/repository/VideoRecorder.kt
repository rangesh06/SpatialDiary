package com.meta.wearable.dat.externalsampleapps.cameraaccess.domain.repository

import java.io.File
import java.nio.ByteBuffer

interface VideoRecorder {
    suspend fun start(width: Int, height: Int, frameRate: Int, outputFile: File): Result<Unit>
    suspend fun recordFrame(buffer: ByteBuffer, presentationTimeUs: Long): Result<Unit>
    suspend fun stop(): Result<File>
}
