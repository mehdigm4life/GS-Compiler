package com.mehdigm.compiler.model

data class PawnFile(
    val name: String,
    val path: String,
    val content: String = "",
    val isModified: Boolean = false,
    val lastSaved: Long = 0L
) {
    val fileExtension: String get() = name.substringAfterLast('.', "")
    val isPwnFile: Boolean get() = fileExtension.equals("pwn", ignoreCase = true)
}
