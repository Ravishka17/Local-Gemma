package com.localgemma.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AllowlistModel(
    val name: String,
    val displayName: String = "",
    val repo: String,
    val ggufFile: String,
    val description: String = "",
    val sizeInBytes: Long = 0L,
    val parameters: String = "",
    val quantization: String = "",
    val chatTemplate: String = "",
    val defaultConfig: DefaultConfig = DefaultConfig(),
)

@Serializable
data class DefaultConfig(
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val temperature: Float = 1.0f,
    val maxTokens: Int = 4096,
)

@Serializable
data class AllowlistRoot(
    val models: List<AllowlistModel> = emptyList()
)

object ModelAllowlist {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AllowlistRoot {
        val stream = javaClass.getResourceAsStream("/model_allowlist.json")
            ?: return AllowlistRoot()
        return json.decodeFromString(stream.bufferedReader().use { it.readText() })
    }

    fun find(name: String): AllowlistModel? {
        return load().models.find { it.name.equals(name, ignoreCase = true) }
    }

    fun search(query: String): List<AllowlistModel> {
        return load().models.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.repo.contains(query, ignoreCase = true)
        }
    }

    fun AllowlistModel.toHuggingFaceUrl(): String {
        return "https://huggingface.co/$repo/resolve/main/$ggufFile"
    }
}
