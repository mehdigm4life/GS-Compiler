package com.mehdigm.compiler.compiler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class NativeCompiler {

    companion object {
        private var libraryLoaded = false

        fun ensureLoaded() {
            if (!libraryLoaded) {
                try {
                    System.loadLibrary("gs-compiler")
                    libraryLoaded = true
                } catch (e: UnsatisfiedLinkError) {
                    throw RuntimeException("Failed to load native compiler library", e)
                }
            }
        }
    }

    private external fun nativeCompile(
        inputPath: String,
        outputPath: String,
        includePaths: Array<String>?,
        callback: CompilationCallback?
    ): Boolean

    suspend fun compile(
        inputFile: File,
        outputFile: File,
        includePaths: List<String> = emptyList(),
        callback: CompilationCallback? = null
    ): CompilationResult = withContext(Dispatchers.IO) {
        ensureLoaded()

        val messages = mutableListOf<String>()
        val outputCallback = if (callback != null) callback else object : CompilationCallback {
            override fun onOutput(text: String, isError: Boolean) {
                messages.add(if (isError) "[ERR] $text" else text)
            }
        }

        val success = try {
            nativeCompile(
                inputFile.absolutePath,
                outputFile.absolutePath,
                if (includePaths.isEmpty()) null else includePaths.toTypedArray(),
                outputCallback
            )
        } catch (e: Exception) {
            messages.add("Compiler crash: ${e.message}")
            false
        }

        CompilationResult(
            success = success,
            outputPath = if (success) outputFile.absolutePath else null,
            errors = if (success) 0 else 1,
            warnings = 0,
            messages = messages
        )
    }
}
