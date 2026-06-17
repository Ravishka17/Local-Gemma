package com.localgemma.model

import kotlinx.serialization.Serializable

@Serializable
data class Model(
    val name: String,
    val displayName: String = "",
    val ggufPath: String = "",
    val sizeInBytes: Long = 0L,
    val parameters: String = "",
    val chatTemplate: String = "",
    val quantization: String = "",
    val temperature: Float = 1.0f,
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val maxTokens: Int = 4096,
    val url: String = "",
    val ggufFile: String = "",
)

@Serializable
data class InstalledModel(
    val name: String,
    val displayName: String = "",
    val ggufPath: String = "",
    val sizeInBytes: Long = 0L,
    val parameters: String = "",
    val quantization: String = "",
    val temperature: Float = 1.0f,
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val maxTokens: Int = 4096,
)
