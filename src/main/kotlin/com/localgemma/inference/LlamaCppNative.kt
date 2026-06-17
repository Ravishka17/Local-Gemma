package com.localgemma.inference

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object LlamaCppNative {
    init {
        loadNativeLibrary()
    }

    @JvmStatic
    external fun loadModel(path: String, nCtx: Int): Long

    @JvmStatic
    external fun freeModel(modelPtr: Long)

    @JvmStatic
    external fun newContext(modelPtr: Long, nThreads: Int): Long

    @JvmStatic
    external fun freeContext(ctxPtr: Long)

    @JvmStatic
    external fun tokenize(modelPtr: Long, text: String, addSpecial: Boolean): IntArray

    @JvmStatic
    external fun decode(ctxPtr: Long, tokenId: Int): Boolean

    @JvmStatic
    external fun sampleToken(ctxPtr: Long, temperature: Float, topK: Int, topP: Float): Int

    @JvmStatic
    external fun tokenToPiece(modelPtr: Long, tokenId: Int): String

    @JvmStatic
    external fun applyChatTemplate(modelPtr: Long, messages: Array<String>, roles: Array<String>): String

    @JvmStatic
    external fun eosToken(modelPtr: Long): Int

    @JvmStatic
    external fun nCtxTrain(modelPtr: Long): Int

    @JvmStatic
    external fun nVocab(modelPtr: Long): Int

    private fun loadNativeLibrary() {
        val osName = System.getProperty("os.name").lowercase()
        val libName = when {
            osName.contains("win") -> "llama.dll"
            osName.contains("mac") -> "libllama.dylib"
            else -> "libllama.so"
        }

        val resourcePath = "/native/$libName"
        val stream: InputStream? = LlamaCppNative::class.java.getResourceAsStream(resourcePath)
        if (stream != null) {
            val tmpDir = File(System.getProperty("java.io.tmpdir"), "localgemma-native")
            tmpDir.mkdirs()
            val libFile = File(tmpDir, libName)
            Files.copy(stream, libFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            System.load(libFile.absolutePath)
        } else {
            try {
                System.loadLibrary("llama")
            } catch (_: UnsatisfiedLinkError) {
                // Will throw at first native call with clearer context
            }
        }
    }
}
