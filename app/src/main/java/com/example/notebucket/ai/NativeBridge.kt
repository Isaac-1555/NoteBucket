package com.example.notebucket.ai

object NativeBridge {

    fun loadNativeLib() {
        System.loadLibrary("bge")
    }

    external fun loadModel(path: String): Boolean

    external fun unloadModel()

    external fun embed(text: String): FloatArray

    external fun isLoaded(): Boolean
}
