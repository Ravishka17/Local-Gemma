package com.localgemma.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ConfigManager {
    private val homeDir = File(System.getProperty("user.home"), ".localgemma")
    private val configFile = File(homeDir, "config.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun configDir(): File = homeDir

    fun modelsDir(): File = File(homeDir, "models").also { it.mkdirs() }

    fun tmpDir(): File = File(homeDir, "tmp").also { it.mkdirs() }

    fun load(): AppConfig {
        if (!configFile.exists()) {
            save(AppConfig())
        }
        return json.decodeFromString(configFile.readText())
    }

    fun save(config: AppConfig) {
        homeDir.mkdirs()
        configFile.writeText(json.encodeToString(config))
    }

    fun update(transform: (AppConfig) -> AppConfig) {
        save(transform(load()))
    }
}
