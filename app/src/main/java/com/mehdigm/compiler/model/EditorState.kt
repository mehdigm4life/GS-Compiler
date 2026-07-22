package com.mehdigm.compiler.model

data class EditorState(
    val content: String = "",
    val cursorPosition: Int = 0,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList(),
    val isCompiling: Boolean = false,
    val currentFile: PawnFile? = null
) {
    val lineCount: Int get() = if (content.isEmpty()) 1 else content.count { it == '\n' } + 1

    fun withContent(newContent: String): EditorState {
        return copy(
            content = newContent,
            undoStack = undoStack + content,
            redoStack = emptyList()
        )
    }

    fun undo(): EditorState {
        if (undoStack.isEmpty()) return this
        val previous = undoStack.last()
        return copy(
            content = previous,
            undoStack = undoStack.dropLast(1),
            redoStack = redoStack + content
        )
    }

    fun redo(): EditorState {
        if (redoStack.isEmpty()) return this
        val next = redoStack.last()
        return copy(
            content = next,
            redoStack = redoStack.dropLast(1),
            undoStack = undoStack + content
        )
    }
}
