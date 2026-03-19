package com.meta.wearable.dat.externalsampleapps.cameraaccess.data.repository

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.domain.repository.VideoRecorder
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRecorderImpl : VideoRecorder {
    companion object {
        private const val TAG = "VideoRecorderImpl"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val TIMEOUT_US = 10000L
    }

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var isMuxerStarted = false
    private var outputFile: File? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    
    private var videoWidth = 0
    private var videoHeight = 0

    override suspend fun start(width: Int, height: Int, frameRate: Int, outputFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            this@VideoRecorderImpl.outputFile = outputFile
            this@VideoRecorderImpl.videoWidth = width
            this@VideoRecorderImpl.videoHeight = height
            
            // MediaCodec may pad sizes, but typically accepts exact dimension inputs.
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 2000000) // 2 Mbps
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            trackIndex = -1
            isMuxerStarted = false
        }
    }

    override suspend fun recordFrame(buffer: ByteBuffer, presentationTimeUs: Long): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val codec = encoder ?: throw IllegalStateException("Encoder is not started")
            
            // Duplicate the buffer so we don't interfere with the caller's read position
            val inputBufferCopy = buffer.duplicate()
            inputBufferCopy.position(0)

            // 1. Feed the input buffer
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                // Get the input Image to properly align planar data to whatever format the hardware requires (NV12, etc)
                val inputImage = codec.getInputImage(inputBufferIndex)
                if (inputImage != null) {
                    copyI420ToImage(inputBufferCopy, inputImage, videoWidth, videoHeight)
                    
                    val size = codec.getInputBuffer(inputBufferIndex)?.capacity() ?: (videoWidth * videoHeight * 3 / 2)
                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        size,
                        presentationTimeUs,
                        0
                    )
                }
            } else {
                Log.w(TAG, "No input buffer available")
            }

            // 2. Drain output buffers
            drainEncoder(false)
        }
    }

    private fun copyI420ToImage(inputBuffer: ByteBuffer, image: android.media.Image, width: Int, height: Int) {
        val planes = image.planes
        inputBuffer.position(0)

        // Allocate a row buffer once per frame to minimize GC.
        // Size it to the max width needed (Y plane width).
        val rowData = ByteArray(width)

        // Y Plane
        copyPlane(inputBuffer, width, height, planes[0], rowData)
        // U Plane
        copyPlane(inputBuffer, width / 2, height / 2, planes[1], rowData)
        // V Plane
        copyPlane(inputBuffer, width / 2, height / 2, planes[2], rowData)
    }

    private fun copyPlane(
        inputBuffer: ByteBuffer,
        width: Int,
        height: Int,
        outputPlane: android.media.Image.Plane,
        rowData: ByteArray
    ) {
        val outputBuffer = outputPlane.buffer
        val outputRowStride = outputPlane.rowStride
        val outputPixelStride = outputPlane.pixelStride

        outputBuffer.position(0)

        for (row in 0 until height) {
            // Read a horizontal line from the planar I420 input
            inputBuffer.get(rowData, 0, width)

            if (outputPixelStride == 1) {
                // Planar format fast-path: we can put the entire row at once
                outputBuffer.position(row * outputRowStride)
                outputBuffer.put(rowData, 0, width)
            } else {
                // Semi-planar format (e.g. NV12): we must interleave the bytes
                var outPos = row * outputRowStride
                for (col in 0 until width) {
                    outputBuffer.put(outPos, rowData[col])
                    outPos += outputPixelStride
                }
            }
        }
    }

    override suspend fun stop(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val codec = encoder ?: throw IllegalStateException("Encoder is not started")
            
            // Signal End-of-Stream
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                codec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            } else {
                Log.w(TAG, "No input buffer available to signal EOS")
            }

            // Drain remaining output
            drainEncoder(true)

            // Release resources
            codec.stop()
            codec.release()
            encoder = null

            if (isMuxerStarted) {
                muxer?.stop()
                isMuxerStarted = false
            }
            muxer?.release()
            muxer = null

            outputFile ?: throw IllegalStateException("Output file is null")
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codec = encoder ?: return
        val mux = muxer ?: return
        var maxRetries = 100

        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break
                } else {
                    maxRetries--
                    if (maxRetries <= 0) break
                    continue
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isMuxerStarted) {
                    throw RuntimeException("Format changed twice")
                }
                val newFormat = codec.outputFormat
                trackIndex = mux.addTrack(newFormat)
                mux.start()
                isMuxerStarted = true
            } else if (outputBufferIndex >= 0) {
                val encodedData = codec.getOutputBuffer(outputBufferIndex)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0) {
                        if (!isMuxerStarted) {
                            throw RuntimeException("Muxer hasn't started")
                        }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        mux.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }
}
