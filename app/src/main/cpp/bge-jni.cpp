#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <cstring>
#include <android/log.h>

#include "llama.h"

static const char* TAG = "bge-jni";
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model*        g_model  = nullptr;
static llama_context*      g_ctx    = nullptr;
static const llama_vocab*  g_vocab  = nullptr;
static int32_t             g_n_embd = 0;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_notebucket_ai_NativeBridge_loadModel(
        JNIEnv* env, jobject /*thiz*/, jstring jpath) {

    const char* path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return JNI_FALSE;

    if (g_model != nullptr) {
        LOGI("Model already loaded, unloading first");
        if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (!g_model) {
        LOGE("Failed to load model from file");
        return JNI_FALSE;
    }

    g_vocab  = llama_model_get_vocab(g_model);
    g_n_embd = llama_model_n_embd(g_model);
    LOGI("Model loaded: n_embd=%d", g_n_embd);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx            = 512;
    cparams.n_batch          = 512;
    cparams.n_ubatch         = 512;
    cparams.n_seq_max        = 1;
    cparams.pooling_type     = LLAMA_POOLING_TYPE_CLS;
    cparams.embeddings       = true;
    cparams.n_threads        = 4;
    cparams.n_threads_batch  = 4;
    cparams.offload_kqv      = false;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Context created, pooling=%d", (int) llama_pooling_type(g_ctx));
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_notebucket_ai_NativeBridge_unloadModel(
        JNIEnv* /*env*/, jobject /*thiz*/) {

    if (g_ctx)   { llama_free(g_ctx);         g_ctx   = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab  = nullptr;
    g_n_embd = 0;
    llama_backend_free();
    LOGI("Model unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_example_notebucket_ai_NativeBridge_isLoaded(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return (g_ctx != nullptr && g_model != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_notebucket_ai_NativeBridge_embed(
        JNIEnv* env, jobject /*thiz*/, jstring jtext) {

    if (!g_ctx || !g_model || !g_vocab || g_n_embd <= 0) {
        LOGE("Model not loaded");
        return nullptr;
    }

    const char* text = env->GetStringUTFChars(jtext, nullptr);
    if (!text) return nullptr;
    int32_t text_len = (int32_t) strlen(text);

    int32_t needed = llama_tokenize(
        g_vocab, text, text_len,
        nullptr, 0,
        true, true);

    if (needed < 0) needed = -needed;
    if (needed <= 0) {
        LOGE("Tokenization returned %d tokens", needed);
        env->ReleaseStringUTFChars(jtext, text);
        return nullptr;
    }

    std::vector<llama_token> tokens(needed);
    int32_t n_tokens = llama_tokenize(
        g_vocab, text, text_len,
        tokens.data(), (int32_t) tokens.size(),
        true, true);

    env->ReleaseStringUTFChars(jtext, text);

    if (n_tokens <= 0) {
        LOGE("Second tokenization failed: %d", n_tokens);
        return nullptr;
    }
    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(std::max(n_tokens, 1), 0, 1);
    for (int32_t i = 0; i < n_tokens; i++) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i]  = 1;
        batch.logits[i]    = 1;
    }
    batch.n_tokens = n_tokens;

    llama_memory_clear(llama_get_memory(g_ctx), true);

    int32_t rc = llama_decode(g_ctx, batch);
    if (rc < 0) {
        LOGE("llama_decode failed: %d", rc);
        llama_batch_free(batch);
        return nullptr;
    }

    const float* embd = llama_get_embeddings_seq(g_ctx, 0);
    if (!embd) {
        embd = llama_get_embeddings_ith(g_ctx, 0);
        if (!embd) {
            LOGE("Failed to get embeddings (seq and ith both null)");
            llama_batch_free(batch);
            return nullptr;
        }
    }

    std::vector<float> normalized(g_n_embd);
    float norm = 0.0f;
    for (int32_t i = 0; i < g_n_embd; i++) {
        norm += embd[i] * embd[i];
    }
    norm = sqrtf(norm);
    if (norm > 0.0f) {
        for (int32_t i = 0; i < g_n_embd; i++) {
            normalized[i] = embd[i] / norm;
        }
    } else {
        for (int32_t i = 0; i < g_n_embd; i++) {
            normalized[i] = embd[i];
        }
    }

    llama_batch_free(batch);

    jfloatArray result = env->NewFloatArray(g_n_embd);
    if (!result) {
        LOGE("Failed to allocate float array");
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, g_n_embd, normalized.data());
    return result;
}

}  // extern "C"
