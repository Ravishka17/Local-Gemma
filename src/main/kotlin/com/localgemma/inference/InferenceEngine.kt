package com.localgemma.inference

import com.localgemma.model.InstalledModel

data class LoadedModel(
    val model: InstalledModel,
    val nativeHandle: Long,
    val contextHandle: Long,
)

interface InferenceEngine {
    fun load(model: InstalledModel): LoadedModel
    fun generate(
        loadedModel: LoadedModel,
        prompt: String,
        params: GenerationParams,
        onToken: (String) -> Unit
    ): String

    fun unload(loadedModel: LoadedModel)
    fun stop(loadedModel: LoadedModel)
}
