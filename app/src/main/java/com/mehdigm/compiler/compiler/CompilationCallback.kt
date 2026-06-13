package com.mehdigm.compiler.compiler

interface CompilationCallback {
    fun onOutput(text: String, isError: Boolean)
}
