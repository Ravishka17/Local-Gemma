package com.localgemma.model

import com.localgemma.config.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.io.File
import kotlin.coroutines.coroutineContext

object ModelDownloader {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    suspend fun download(
        modelName: String,
        url: String,
        hfToken: String? = null,
        onProgress: (received: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val modelDir = File(ConfigManager.modelsDir(), modelName)
        modelDir.mkdirs()
        val outputFile = File(modelDir, "model.gguf")
        val tmpFile = File(ConfigManager.tmpDir(), "$modelName.partial")

        val requestBuilder = Request.Builder().url(url)
        if (!hfToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $hfToken")
        }

        val resumeFrom = if (tmpFile.exists()) tmpFile.length() else 0L
        if (resumeFrom > 0) {
            requestBuilder.header("Range", "bytes=$resumeFrom-")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 206) {
            throw IOException("Download failed: ${response.code} ${response.message}")
        }

        val body = response.body ?: throw IOException("Empty response body")
        val totalBytes = if (response.code == 206 && resumeFrom > 0) {
            resumeFrom + (body.contentLength().takeIf { it > 0 } ?: 0L)
        } else {
            body.contentLength().takeIf { it > 0 } ?: 0L
        }

        tmpFile.parentFile?.mkdirs()
        val sink = if (response.code == 206 && resumeFrom > 0) {
            tmpFile.outputStream().apply { channel.position(resumeFrom) }
        } else {
            tmpFile.outputStream()
        }

        sink.use { out ->
            val source = body.byteStream()
            val buffer = ByteArray(8192)
            var received = if (response.code == 206) resumeFrom else 0L
            var lastReport = 0L
            while (coroutineContext.isActive) {
                val read = source.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                received += read
                if (received - lastReport > 256 * 1024 || (totalBytes > 0 && received == totalBytes)) {
                    onProgress(received, totalBytes)
                    lastReport = received
                }
            }
            onProgress(received, totalBytes)
        }

        tmpFile.renameTo(outputFile)
        outputFile
    }
}
