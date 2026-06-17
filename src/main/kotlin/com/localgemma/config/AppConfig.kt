package com.localgemma.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val defaultModel: String = "",
    val temperature: Float = 1.0f,
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val maxTokens: Int = 4096,
    val huggingFaceToken: String = "",
    val verbose: Boolean = false,
)
