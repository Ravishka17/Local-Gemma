package com.localgemma.inference

data class GenerationParams(
    val temperature: Float = 1.0f,
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val maxTokens: Int = 4096,
)
