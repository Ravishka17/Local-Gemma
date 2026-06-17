package com.localgemma.model

import com.localgemma.config.ConfigManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class ModelsManifest(
    val models: List<InstalledModel> = emptyList()
)

object ModelRegistry {
    private val manifestFile = File(ConfigManager.configDir(), "models.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun list(): List<InstalledModel> {
        if (!manifestFile.exists()) return emptyList()
        return json.decodeFromString<ModelsManifest>(manifestFile.readText()).models
    }

    fun get(name: String): InstalledModel? {
        return list().find { it.name == name }
    }

    fun add(model: InstalledModel) {
        val current = list().toMutableList()
        current.removeAll { it.name == model.name }
        current.add(model)
        save(current)
    }

    fun remove(name: String) {
        val model = get(name) ?: return
        File(model.ggufPath).parentFile?.deleteRecursively()
        save(list().filter { it.name != name })
    }

    fun exists(name: String): Boolean {
        return list().any { it.name == name }
    }

    private fun save(models: List<InstalledModel>) {
        ConfigManager.configDir().mkdirs()
        manifestFile.writeText(json.encodeToString(ModelsManifest(models)))
    }
}
