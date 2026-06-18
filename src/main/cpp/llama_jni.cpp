#include <jni.h>
#include <llama.h>
#include <vector>
#include <string>
#include <unordered_map>
#include <mutex>
#include <stdexcept>

static std::unordered_map<jlong, jint> modelCtxMap;
static std::mutex modelCtxMapMutex;

static void throwRuntimeException(JNIEnv *env, const char *msg) {
    jclass exClass = env->FindClass("java/lang/RuntimeException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, msg);
    }
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_localgemma_inference_LlamaCppNative_loadModel(JNIEnv *env, jclass clazz, jstring path, jint nCtx) {
    (void)clazz;
    try {
        const char *cPath = env->GetStringUTFChars(path, nullptr);
        if (cPath == nullptr) {
            throwRuntimeException(env, "Failed to get path string");
            return 0L;
        }

        llama_model_params params = llama_model_default_params();
        llama_model *model = llama_load_model_from_file(cPath, params);
        env->ReleaseStringUTFChars(path, cPath);

        if (model == nullptr) {
            throwRuntimeException(env, "Failed to load model");
            return 0L;
        }

        jlong handle = reinterpret_cast<jlong>(model);
        {
            std::lock_guard<std::mutex> lock(modelCtxMapMutex);
            modelCtxMap[handle] = nCtx;
        }
        return handle;
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return 0L;
    }
}

JNIEXPORT void JNICALL
Java_com_localgemma_inference_LlamaCppNative_freeModel(JNIEnv *env, jclass clazz, jlong modelPtr) {
    (void)clazz;
    try {
        {
            std::lock_guard<std::mutex> lock(modelCtxMapMutex);
            modelCtxMap.erase(modelPtr);
        }
        llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
        if (model != nullptr) {
            llama_free_model(model);
        }
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
    }
}

JNIEXPORT jlong JNICALL
Java_com_localgemma_inference_LlamaCppNative_newContext(JNIEnv *env, jclass clazz, jlong modelPtr, jint nThreads) {
    (void)clazz;
    try {
        llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
        if (model == nullptr) {
            throwRuntimeException(env, "Invalid model pointer");
            return 0L;
        }

        jint nCtx = 4096;
        {
            std::lock_guard<std::mutex> lock(modelCtxMapMutex);
            auto it = modelCtxMap.find(modelPtr);
            if (it != modelCtxMap.end()) {
                nCtx = it->second;
            }
        }

        llama_context_params ctxParams = llama_context_default_params();
        ctxParams.n_ctx = nCtx;
        ctxParams.n_threads = nThreads;
        ctxParams.n_threads_batch = nThreads;

        llama_context *ctx = llama_new_context_with_model(model, ctxParams);
        if (ctx == nullptr) {
            throwRuntimeException(env, "Failed to create context");
            return 0L;
        }
        return reinterpret_cast<jlong>(ctx);
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return 0L;
    }
}

JNIEXPORT void JNICALL
Java_com_localgemma_inference_LlamaCppNative_freeContext(JNIEnv *env, jclass clazz, jlong ctxPtr) {
    (void)clazz;
    try {
        llama_context *ctx = reinterpret_cast<llama_context *>(ctxPtr);
        if (ctx != nullptr) {
            llama_free(ctx);
        }
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
    }
}

JNIEXPORT jintArray JNICALL
Java_com_localgemma_inference_LlamaCppNative_tokenize(JNIEnv *env, jclass clazz, jlong modelPtr, jstring text, jboolean addSpecial) {
    (void)clazz;
    try {
        llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
        if (model == nullptr) {
            throwRuntimeException(env, "Invalid model pointer");
            return nullptr;
        }

        const llama_vocab *vocab = llama_model_get_vocab(model);
        const char *cText = env->GetStringUTFChars(text, nullptr);
        if (cText == nullptr) {
            throwRuntimeException(env, "Failed to get text string");
            return nullptr;
        }

        int32_t textLen = static_cast<int32_t>(env->GetStringUTFLength(text));

        // First call: get token count
        int32_t nTokens = llama_tokenize(vocab, cText, textLen, nullptr, 0, addSpecial, true);
        if (nTokens < 0) {
            env->ReleaseStringUTFChars(text, cText);
            throwRuntimeException(env, "Tokenization failed");
            return nullptr;
        }

        std::vector<llama_token> tokens(nTokens);
        int32_t actual = llama_tokenize(vocab, cText, textLen, tokens.data(), nTokens, addSpecial, true);
        env->ReleaseStringUTFChars(text, cText);

        if (actual < 0) {
            throwRuntimeException(env, "Tokenization failed");
            return nullptr;
        }

        jintArray result = env->NewIntArray(actual);
        if (result != nullptr) {
            env->SetIntArrayRegion(result, 0, actual, reinterpret_cast<const jint *>(tokens.data()));
        }
        return result;
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_localgemma_inference_LlamaCppNative_decode(JNIEnv *env, jclass clazz, jlong ctxPtr, jint tokenId) {
    (void)clazz;
    try {
        llama_context *ctx = reinterpret_cast<llama_context *>(ctxPtr);
        if (ctx == nullptr) {
            throwRuntimeException(env, "Invalid context pointer");
            return JNI_FALSE;
        }

        llama_token token = static_cast<llama_token>(tokenId);
        llama_batch batch = llama_batch_get_one(&token, 1);
        int32_t status = llama_decode(ctx, batch);
        return status == 0 ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return JNI_FALSE;
    }
}

JNIEXPORT jint JNICALL
Java_com_localgemma_inference_LlamaCppNative_sampleToken(JNIEnv *env, jclass clazz, jlong ctxPtr, jfloat temperature, jint topK, jfloat topP) {
    (void)clazz;
    try {
        llama_context *ctx = reinterpret_cast<llama_context *>(ctxPtr);
        if (ctx == nullptr) {
            throwRuntimeException(env, "Invalid context pointer");
            return -1;
        }

        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        llama_sampler *smpl = llama_sampler_chain_init(sparams);
        if (smpl == nullptr) {
            throwRuntimeException(env, "Failed to init sampler chain");
            return -1;
        }

        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

        llama_token token = llama_sampler_sample(smpl, ctx, -1);
        llama_sampler_free(smpl);
        return static_cast<jint>(token);
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return -1;
    }
}

JNIEXPORT jstring JNICALL
Java_com_localgemma_inference_LlamaCppNative_tokenToPiece(JNIEnv *env, jclass clazz, jlong modelPtr, jint tokenId) {
    (void)clazz;
    try {
        llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
        if (model == nullptr) {
            throwRuntimeException(env, "Invalid model pointer");
            return nullptr;
        }

        const llama_vocab *vocab = llama_model_get_vocab(model);
        std::vector<char> buf(256);
        int32_t nBytes = llama_token_to_piece(vocab, static_cast<llama_token>(tokenId), buf.data(), static_cast<int32_t>(buf.size()), 0, true);
        if (nBytes < 0) {
            buf.resize(static_cast<size_t>(-nBytes));
            nBytes = llama_token_to_piece(vocab, static_cast<llama_token>(tokenId), buf.data(), static_cast<int32_t>(buf.size()), 0, true);
        }
        if (nBytes < 0) {
            throwRuntimeException(env, "tokenToPiece failed");
            return nullptr;
        }

        return env->NewStringUTF(std::string(buf.data(), nBytes).c_str());
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return nullptr;
    }
}

JNIEXPORT jstring JNICALL
Java_com_localgemma_inference_LlamaCppNative_applyChatTemplate(JNIEnv *env, jclass clazz, jlong modelPtr, jobjectArray messages, jobjectArray roles) {
    (void)clazz;
    try {
        llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
        if (model == nullptr) {
            throwRuntimeException(env, "Invalid model pointer");
            return nullptr;
        }

        jsize msgLen = env->GetArrayLength(messages);
        jsize roleLen = env->GetArrayLength(roles);
        if (msgLen != roleLen) {
            throwRuntimeException(env, "messages and roles arrays must have same length");
            return nullptr;
        }

        std::vector<llama_chat_message> chat;
        std::vector<std::string> roleStorage;
        std::vector<std::string> contentStorage;
        chat.reserve(static_cast<size_t>(msgLen));
        roleStorage.reserve(static_cast<size_t>(msgLen));
        contentStorage.reserve(static_cast<size_t>(msgLen));

        for (jsize i = 0; i < msgLen; i++) {
            jstring jRole = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
            jstring jContent = static_cast<jstring>(env->GetObjectArrayElement(messages, i));

            const char *role = env->GetStringUTFChars(jRole, nullptr);
            const char *content = env->GetStringUTFChars(jContent, nullptr);

            roleStorage.emplace_back(role);
            contentStorage.emplace_back(content);

            env->ReleaseStringUTFChars(jRole, role);
            env->ReleaseStringUTFChars(jContent, content);
            env->DeleteLocalRef(jRole);
            env->DeleteLocalRef(jContent);

            chat.push_back({roleStorage.back().c_str(), contentStorage.back().c_str()});
        }

        std::vector<char> buf(4096);
        int32_t res = llama_chat_apply_template(nullptr, chat.data(), chat.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
        if (res < 0) {
            throwRuntimeException(env, "applyChatTemplate failed");
            return nullptr;
        }
        if (res > static_cast<int32_t>(buf.size())) {
            buf.resize(static_cast<size_t>(res));
            res = llama_chat_apply_template(nullptr, chat.data(), chat.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
            if (res < 0) {
                throwRuntimeException(env, "applyChatTemplate failed on retry");
                return nullptr;
            }
        }

        return env->NewStringUTF(std::string(buf.data(), res).c_str());
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return nullptr;
    }
}

JNIEXPORT jint JNICALL
Java_com_localgemma_inference_LlamaCppNative_eosToken(JNIEnv *env, jclass clazz, jlong modelPtr) {
    (void)clazz;
    try {
        llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
        if (model == nullptr) {
            throwRuntimeException(env, "Invalid model pointer");
            return -1;
        }
        const llama_vocab *vocab = llama_model_get_vocab(model);
        return static_cast<jint>(llama_vocab_eos(vocab));
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return -1;
    }
}

JNIEXPORT jint JNICALL
Java_com_localgemma_inference_LlamaCppNative_nCtxTrain(JNIEnv *env, jclass clazz, jlong modelPtr) {
    (void)clazz;
    try {
        llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
        if (model == nullptr) {
            throwRuntimeException(env, "Invalid model pointer");
            return 0;
        }
        return static_cast<jint>(llama_model_n_ctx_train(model));
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return 0;
    }
}

JNIEXPORT jint JNICALL
Java_com_localgemma_inference_LlamaCppNative_nVocab(JNIEnv *env, jclass clazz, jlong modelPtr) {
    (void)clazz;
    try {
        llama_model *model = reinterpret_cast<llama_model *>(modelPtr);
        if (model == nullptr) {
            throwRuntimeException(env, "Invalid model pointer");
            return 0;
        }
        const llama_vocab *vocab = llama_model_get_vocab(model);
        return static_cast<jint>(llama_vocab_n_tokens(vocab));
    } catch (const std::exception &e) {
        throwRuntimeException(env, e.what());
        return 0;
    }
}

} // extern "C"
