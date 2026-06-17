package com.localgemma.api

import com.localgemma.config.ConfigManager
import com.localgemma.inference.GenerationParams
import com.localgemma.inference.InferenceEngine
import com.localgemma.inference.LlamaCppEngine
import com.localgemma.inference.LlamaCppNative
import com.localgemma.model.InstalledModel
import com.localgemma.model.ModelRegistry
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class OpenAiServer : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private val logger = LoggerFactory.getLogger(OpenAiServer::class.java)
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val modelMutexes = ConcurrentHashMap<String, Mutex>()
    private val loadedModels = ConcurrentHashMap<String, Pair<InstalledModel, com.localgemma.inference.LoadedModel>>()
    private val engine: InferenceEngine = LlamaCppEngine()

    fun start(host: String = "0.0.0.0", port: Int = 8080) {
        if (server != null) return

        server = embeddedServer(Netty, port = port, host = host) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader("ngrok-skip-browser-warning")
            }

            routing {
                get("/health") {
                    call.respond(mapOf("status" to "ok"))
                }

                get("/v1/models") {
                    val models = ModelRegistry.list().map {
                        ModelData(id = it.name, created = System.currentTimeMillis() / 1000)
                    }
                    call.respond(ModelsListResponse(data = models))
                }

                get("/v1/models/{modelId}") {
                    val modelId = call.parameters["modelId"]
                    val model = ModelRegistry.get(modelId ?: "")
                    if (model == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Model not found"))
                    } else {
                        call.respond(ModelData(id = model.name, created = System.currentTimeMillis() / 1000))
                    }
                }

                post("/v1/chat/completions") {
                    val request = call.receive<ChatCompletionRequest>()
                    handleChatCompletion(call, request)
                }

                post("/v1/completions") {
                    val request = call.receive<CompletionRequest>()
                    handleCompletion(call, request)
                }
            }
        }.start(wait = false)
        logger.info("OpenAI API Server started on $host:$port")
    }

    fun stop() {
        loadedModels.values.forEach { engine.unload(it.second) }
        loadedModels.clear()
        server?.stop(1000, 2000)
        server = null
    }

    private suspend fun handleChatCompletion(call: ApplicationCall, request: ChatCompletionRequest) {
        val model = ModelRegistry.get(request.model)
        if (model == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Model not found"))
            return
        }

        val mutex = modelMutexes.getOrPut(model.name) { Mutex() }
        if (mutex.isLocked) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Model is busy"))
            return
        }

        mutex.withLock {
            val params = GenerationParams(
                temperature = request.temperature ?: model.temperature,
                topK = request.top_k ?: model.topK,
                topP = request.top_p ?: model.topP,
                maxTokens = request.max_tokens ?: model.maxTokens,
            )

            if (!request.tools.isNullOrEmpty()) {
                logger.warn("Tools provided in API request but not yet supported. Ignoring.")
            }

            val systemMessages = request.messages.filter { it.role == "system" }
            val systemText = systemMessages.joinToString("\n") { it.content }
            val conversationMessages = request.messages.filter { it.role != "system" }

            if (conversationMessages.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No user/assistant messages provided"))
                return@withLock
            }

            val loaded = getOrLoadModel(model)
            val prompt = buildPrompt(loaded, systemText, conversationMessages)

            if (request.stream) {
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    streamChatResponse(this, loaded, prompt, params, model.name)
                }
            } else {
                val responseText = runBlockingInference(loaded, prompt, params)
                call.respond(ChatCompletionResponse(
                    id = "chatcmpl-" + UUID.randomUUID().toString(),
                    created = System.currentTimeMillis() / 1000,
                    model = model.name,
                    choices = listOf(
                        ChatChoice(
                            index = 0,
                            message = ChatMessage(role = "assistant", content = responseText),
                            finish_reason = "stop"
                        )
                    ),
                    usage = Usage(
                        prompt_tokens = prompt.length / 4,
                        completion_tokens = responseText.length / 4,
                        total_tokens = (prompt.length + responseText.length) / 4
                    )
                ))
            }
        }
    }

    private suspend fun handleCompletion(call: ApplicationCall, request: CompletionRequest) {
        val model = ModelRegistry.get(request.model)
        if (model == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Model not found"))
            return
        }

        val mutex = modelMutexes.getOrPut(model.name) { Mutex() }
        if (mutex.isLocked) {
            call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Model is busy"))
            return
        }

        mutex.withLock {
            val params = GenerationParams(
                temperature = request.temperature ?: model.temperature,
                topK = request.top_k ?: model.topK,
                topP = request.top_p ?: model.topP,
                maxTokens = request.max_tokens ?: model.maxTokens,
            )

            val loaded = getOrLoadModel(model)

            if (request.stream) {
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    streamCompletionResponse(this, loaded, request.prompt, params, model.name)
                }
            } else {
                val responseText = runBlockingInference(loaded, request.prompt, params)
                call.respond(CompletionResponse(
                    id = "cmpl-" + UUID.randomUUID().toString(),
                    created = System.currentTimeMillis() / 1000,
                    model = model.name,
                    choices = listOf(
                        CompletionChoice(
                            index = 0,
                            text = responseText,
                            finish_reason = "stop"
                        )
                    ),
                    usage = Usage(
                        prompt_tokens = request.prompt.length / 4,
                        completion_tokens = responseText.length / 4,
                        total_tokens = (request.prompt.length + responseText.length) / 4
                    )
                ))
            }
        }
    }

    private fun getOrLoadModel(model: InstalledModel): com.localgemma.inference.LoadedModel {
        return loadedModels.computeIfAbsent(model.name) {
            logger.info("Loading model ${model.name}")
            model to engine.load(model)
        }.second
    }

    private fun buildPrompt(
        loaded: com.localgemma.inference.LoadedModel,
        systemText: String,
        messages: List<ChatMessage>
    ): String {
        val formatted = messages.map { it.role to it.content }
        val roles = mutableListOf<String>()
        val contents = mutableListOf<String>()
        if (systemText.isNotBlank()) {
            roles.add("system")
            contents.add(systemText)
        }
        for ((role, content) in formatted) {
            roles.add(role)
            contents.add(content)
        }
        return try {
            LlamaCppNative.applyChatTemplate(loaded.nativeHandle, contents.toTypedArray(), roles.toTypedArray())
        } catch (_: Exception) {
            val sb = StringBuilder()
            if (systemText.isNotBlank()) {
                sb.append("system: ").append(systemText).append("\n")
            }
            for ((role, content) in formatted) {
                sb.append(role).append(": ").append(content).append("\n")
            }
            sb.toString()
        }
    }

    private suspend fun runBlockingInference(
        loaded: com.localgemma.inference.LoadedModel,
        prompt: String,
        params: GenerationParams
    ): String {
        val deferred = CompletableDeferred<String>()
        val full = StringBuilder()
        launch {
            try {
                engine.generate(loaded, prompt, params) { token ->
                    full.append(token)
                }
                deferred.complete(full.toString())
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        return deferred.await()
    }

    private suspend fun streamChatResponse(
        writer: ByteWriteChannel,
        loaded: com.localgemma.inference.LoadedModel,
        prompt: String,
        params: GenerationParams,
        modelName: String
    ) {
        val id = "chatcmpl-" + UUID.randomUUID().toString()
        val created = System.currentTimeMillis() / 1000
        val deferred = CompletableDeferred<Unit>()

        launch {
            try {
                engine.generate(loaded, prompt, params) { token ->
                    runBlocking {
                        val chunk = ChatCompletionChunk(
                            id = id,
                            created = created,
                            model = modelName,
                            choices = listOf(
                                ChatChunkChoice(
                                    index = 0,
                                    delta = ChatDelta(content = token)
                                )
                            )
                        )
                        writer.writeStringUtf8("data: ${Json.encodeToString(chunk)}\n\n")
                        writer.flush()
                    }
                }
                writer.writeStringUtf8("data: [DONE]\n\n")
                writer.flush()
                deferred.complete(Unit)
            } catch (e: Exception) {
                runBlocking {
                    writer.writeStringUtf8("data: {\"error\": \"${e.message}\"}\n\n")
                    writer.writeStringUtf8("data: [DONE]\n\n")
                    writer.flush()
                }
                deferred.completeExceptionally(e)
            }
        }
        deferred.await()
    }

    private suspend fun streamCompletionResponse(
        writer: ByteWriteChannel,
        loaded: com.localgemma.inference.LoadedModel,
        prompt: String,
        params: GenerationParams,
        modelName: String
    ) {
        val id = "cmpl-" + UUID.randomUUID().toString()
        val created = System.currentTimeMillis() / 1000
        val deferred = CompletableDeferred<Unit>()

        launch {
            try {
                engine.generate(loaded, prompt, params) { token ->
                    runBlocking {
                        val chunk = CompletionChunk(
                            id = id,
                            created = created,
                            model = modelName,
                            choices = listOf(
                                CompletionChunkChoice(
                                    index = 0,
                                    text = token
                                )
                            )
                        )
                        writer.writeStringUtf8("data: ${Json.encodeToString(chunk)}\n\n")
                        writer.flush()
                    }
                }
                writer.writeStringUtf8("data: [DONE]\n\n")
                writer.flush()
                deferred.complete(Unit)
            } catch (e: Exception) {
                runBlocking {
                    writer.writeStringUtf8("data: {\"error\": \"${e.message}\"}\n\n")
                    writer.writeStringUtf8("data: [DONE]\n\n")
                    writer.flush()
                }
                deferred.completeExceptionally(e)
            }
        }
        deferred.await()
    }
}
