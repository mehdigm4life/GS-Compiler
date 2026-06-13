package com.mehdigm.compiler.compiler

data class CompilationResult(
    val success: Boolean,
    val outputPath: String?,
    val errors: Int,
    val warnings: Int,
    val messages: List<String> = emptyList()
)
