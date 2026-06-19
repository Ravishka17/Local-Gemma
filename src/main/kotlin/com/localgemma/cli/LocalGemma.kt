package com.localgemma.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.localgemma.api.OpenAiServer
import com.localgemma.config.ConfigManager
import com.localgemma.inference.GenerationParams
import com.localgemma.inference.LlamaCppEngine
import com.localgemma.model.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

class LocalGemma : CliktCommand(name = "localgemma") {
    private val verbose by option("-v", "--verbose", help = "Enable verbose logging").flag(default = false)

    override fun run() {
        ConfigManager.update { it.copy(verbose = verbose) }
    }
}

class Serve : CliktCommand(name = "serve") {
    private val host by option("--host", help = "Host to bind to").default("0.0.0.0")
    private val port by option("--port", help = "Port to listen on").int().default(8080)
    private val model by option("--model", help = "Default model to load on startup")

    override fun run() {
        val logger = LoggerFactory.getLogger(Serve::class.java)
        val server = OpenAiServer()

        model?.let { modelName ->
            val m = ModelRegistry.get(modelName)
            if (m == null) {
                logger.error("Model '$modelName' not found. Run 'localgemma list' to see installed models.")
                exitProcess(1)
            }
            logger.info("Pre-loading model: $modelName")
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutting down server...")
            server.stop()
        })

        server.start(host, port, model)
        logger.info("Server running at http://$host:$port")
        logger.info("Press Ctrl+C to stop")

        Thread.currentThread().join()
    }
}

class Run : CliktCommand(name = "run") {
    private val modelName by argument(name = "MODEL", help = "Name of the model to chat with")

    override fun run() {
        val logger = LoggerFactory.getLogger(Run::class.java)
        val model = ModelRegistry.get(modelName)
        if (model == null) {
            echo("Model '$modelName' not found. Run 'localgemma list' to see installed models.", err = true)
            exitProcess(1)
        }

        val engine = LlamaCppEngine()
        echo("Loading model: ${model.displayName.ifBlank { model.name }}")
        val loaded = engine.load(model)
        echo("Model loaded. Type your message and press Enter. Type /exit to quit.\n")

        val history = mutableListOf<Pair<String, String>>()

        while (true) {
            print("> ")
            val input = readlnOrNull()?.trim() ?: break
            if (input == "/exit") break
            if (input.isBlank()) continue

            val prompt = buildString {
                for ((role, content) in history) {
                    append("$role: $content\n")
                }
                append("user: $input\n")
                append("assistant: ")
            }

            echo("", trailingNewline = false)
            val response = engine.generate(loaded, prompt, GenerationParams(
                temperature = model.temperature,
                topK = model.topK,
                topP = model.topP,
                maxTokens = model.maxTokens,
            )) { token ->
                print(token)
                System.out.flush()
            }
            echo("\n")
            history.add("user" to input)
            history.add("assistant" to response)
        }

        engine.unload(loaded)
        echo("Goodbye!")
    }
}

class Pull : CliktCommand(name = "pull") {
    private val modelName by argument(name = "MODEL", help = "Model name from allowlist or a HuggingFace URL")

    override fun run() {
        val logger = LoggerFactory.getLogger(Pull::class.java)
        val entry = ModelAllowlist.find(modelName)

        val url: String
        val name: String
        val displayName: String
        val sizeBytes: Long
        val params: String
        val quant: String

        if (entry != null) {
            url = "https://huggingface.co/${entry.repo}/resolve/main/${entry.ggufFile}"
            name = entry.name
            displayName = entry.displayName
            sizeBytes = entry.sizeInBytes
            params = entry.parameters
            quant = entry.quantization
        } else if (modelName.startsWith("http")) {
            url = modelName
            name = File(modelName).nameWithoutExtension
            displayName = ""
            sizeBytes = 0L
            params = ""
            quant = ""
        } else {
            echo("Model '$modelName' not found in allowlist and is not a valid URL.", err = true)
            exitProcess(1)
        }

        if (ModelRegistry.exists(name)) {
            echo("Model '$name' is already installed.")
            return
        }

        echo("Downloading $name...")
        val config = ConfigManager.load()
        val outputFile = runBlocking {
            ModelDownloader.download(name, url, config.huggingFaceToken.ifBlank { null }) { received, total ->
                val pct = if (total > 0) "%.1f".format(received * 100.0 / total) else "?"
                val mb = received / (1024 * 1024)
                val totalMb = if (total > 0) total / (1024 * 1024) else 0
                print("\rProgress: $pct% ($mb MB / $totalMb MB)")
                System.out.flush()
            }
        }
        echo("")

        val installed = InstalledModel(
            name = name,
            displayName = displayName.ifBlank { name },
            ggufPath = outputFile.absolutePath,
            sizeInBytes = outputFile.length(),
            parameters = params,
            quantization = quant,
            temperature = entry?.defaultConfig?.temperature ?: 1.0f,
            topK = entry?.defaultConfig?.topK ?: 64,
            topP = entry?.defaultConfig?.topP ?: 0.95f,
            maxTokens = entry?.defaultConfig?.maxTokens ?: 4096,
        )
        ModelRegistry.add(installed)
        echo("Model '$name' installed successfully at ${outputFile.absolutePath}")
    }
}

class ListModels : CliktCommand(name = "list") {
    override fun run() {
        val models = ModelRegistry.list()
        if (models.isEmpty()) {
            echo("No models installed. Run 'localgemma pull <model>' to download one.")
            return
        }
        echo(String.format("%-20s %-12s %-10s %-12s %s", "NAME", "SIZE", "PARAMS", "QUANT", "PATH"))
        echo("-".repeat(80))
        for (m in models) {
            val size = if (m.sizeInBytes > 0) "%.1f GB".format(m.sizeInBytes / (1024.0 * 1024 * 1024)) else "?"
            echo(String.format("%-20s %-12s %-10s %-12s %s", m.name, size, m.parameters, m.quantization, m.ggufPath))
        }
    }
}

class Rm : CliktCommand(name = "rm") {
    private val modelName by argument(name = "MODEL", help = "Name of the model to remove")

    override fun run() {
        if (!ModelRegistry.exists(modelName)) {
            echo("Model '$modelName' not found.", err = true)
            exitProcess(1)
        }
        ModelRegistry.remove(modelName)
        echo("Model '$modelName' removed.")
    }
}

class Ps : CliktCommand(name = "ps") {
    override fun run() {
        val models = ModelRegistry.list()
        if (models.isEmpty()) {
            echo("No models installed.")
            return
        }
        echo(String.format("%-20s %-10s %-10s", "NAME", "STATUS", "SIZE"))
        echo("-".repeat(50))
        for (m in models) {
            val size = if (m.sizeInBytes > 0) "%.1f GB".format(m.sizeInBytes / (1024.0 * 1024 * 1024)) else "?"
            echo(String.format("%-20s %-10s %-10s", m.name, "installed", size))
        }
    }
}

fun main(args: Array<String>) = LocalGemma()
    .subcommands(Serve(), Run(), Pull(), ListModels(), Rm(), Ps())
    .main(args)
