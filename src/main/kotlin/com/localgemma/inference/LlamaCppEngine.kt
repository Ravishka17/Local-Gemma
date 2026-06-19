package com.localgemma.inference

import com.localgemma.model.InstalledModel
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class LlamaCppEngine : InferenceEngine {
    private val logger = LoggerFactory.getLogger(LlamaCppEngine::class.java)
    private val stopFlags = ConcurrentHashMap<Long, AtomicBoolean>()

    override fun load(model: InstalledModel): LoadedModel {
        val path = model.ggufPath
        logger.info("Loading model from $path")
        val nCtx = model.maxTokens.coerceIn(512, 32768)
        val modelPtr = LlamaCppNative.loadModel(path, nCtx)
        if (modelPtr == 0L) {
            throw IllegalStateException("Failed to load model from $path")
        }
        val ctxPtr = LlamaCppNative.newContext(modelPtr, Runtime.getRuntime().availableProcessors())
        if (ctxPtr == 0L) {
            LlamaCppNative.freeModel(modelPtr)
            throw IllegalStateException("Failed to create context for model")
        }
        stopFlags[ctxPtr] = AtomicBoolean(false)
        return LoadedModel(model, modelPtr, ctxPtr)
    }

    override fun generate(
        loadedModel: LoadedModel,
        prompt: String,
        params: GenerationParams,
        onToken: (String) -> Unit
    ): String {
        val ctxPtr = loadedModel.contextHandle
        val modelPtr = loadedModel.nativeHandle
        val stopFlag = stopFlags[ctxPtr] ?: AtomicBoolean(false)
        stopFlag.set(false)

        val tokens = LlamaCppNative.tokenize(modelPtr, prompt, true)
        for (token in tokens) {
            if (!LlamaCppNative.decode(ctxPtr, token)) {
                throw IllegalStateException("Decode failed")
            }
        }

        val eos = LlamaCppNative.eosToken(modelPtr)
        val result = StringBuilder()
        var generated = 0
        while (generated < params.maxTokens && !stopFlag.get()) {
            val nextToken = LlamaCppNative.sampleToken(ctxPtr, params.temperature, params.topK, params.topP)
            if (nextToken == eos) break
            val piece = LlamaCppNative.tokenToPiece(modelPtr, nextToken)
            result.append(piece)
            onToken(piece)
            generated++
        }
        return result.toString()
    }

    override fun unload(loadedModel: LoadedModel) {
        stopFlags.remove(loadedModel.contextHandle)
        LlamaCppNative.freeContext(loadedModel.contextHandle)
        LlamaCppNative.freeModel(loadedModel.nativeHandle)
    }

    override fun stop(loadedModel: LoadedModel) {
        stopFlags[loadedModel.contextHandle]?.set(true)
    }
}
