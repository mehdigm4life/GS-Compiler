package com.mehdigm.compiler.ui.console

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehdigm.compiler.compiler.CompilationCallback
import com.mehdigm.compiler.compiler.NativeCompiler
import com.mehdigm.compiler.storage.FileManager
import com.mehdigm.compiler.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

private const val MAX_CONSOLE_ENTRIES = 500
private const val FILE_READ_TIMEOUT_MS = 15_000L

data class CompilerUiState(
    val editorText: String = "",
    val currentFile: File? = null,
    val consoleEntries: List<ConsoleEntry> = emptyList(),
    val isCompiling: Boolean = false,
    val isCompileSuccess: Boolean? = false,
    val detectedIncludes: List<String> = emptyList(),
    val consoleExpanded: Boolean = true,
    val isReadingFile: Boolean = false,
    val errorMessage: String? = null
)

class CompilerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CompilerUiState())
    val uiState: StateFlow<CompilerUiState> = _uiState.asStateFlow()

    private val compiler = NativeCompiler()
    private var currentContent: String = ""

    private val _consoleChannel = Channel<ConsoleEntry>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(Dispatchers.Main) {
            _consoleChannel.consumeAsFlow().collect { entry ->
                val current = _uiState.value.consoleEntries
                val updated = if (current.size >= MAX_CONSOLE_ENTRIES) {
                    current.drop(current.size - MAX_CONSOLE_ENTRIES + 1) + entry
                } else {
                    current + entry
                }
                _uiState.value = _uiState.value.copy(consoleEntries = updated)
            }
        }
    }

    fun setEditorText(text: String) {
        _uiState.value = _uiState.value.copy(editorText = text)
        currentContent = text
    }

    fun loadFromUri(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(
            isReadingFile = true,
            errorMessage = null
        )

        val deferred = viewModelScope.async(Dispatchers.Default) {
            withTimeout(FILE_READ_TIMEOUT_MS) {
                FileManager.readFromUri(context, uri)
            }
        }

        viewModelScope.launch(Dispatchers.Main) {
            try {
                val content = deferred.await()
                if (content != null) {
                    loadContent(content, uri)
                } else {
                    fail("Failed to read file from URI")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                fail("File read timed out after ${FILE_READ_TIMEOUT_MS / 1000}s")
                AppLogger.e("GSCompiler", "File read timeout for URI: $uri")
            } catch (e: SecurityException) {
                fail(e.message ?: "File too large")
                AppLogger.e("GSCompiler", "Security error: ${e.message}")
            } catch (e: Exception) {
                fail("Error: ${e.message}")
                AppLogger.e("GSCompiler", "Error reading URI: $uri - ${e.message}")
            }
        }
    }

    fun loadFile(file: File) {
        _uiState.value = _uiState.value.copy(
            isReadingFile = true,
            errorMessage = null
        )

        val deferred = viewModelScope.async(Dispatchers.Default) {
            withTimeout(FILE_READ_TIMEOUT_MS) {
                FileManager.readFileContent(file)
            }
        }

        viewModelScope.launch(Dispatchers.Main) {
            try {
                val content = deferred.await()
                loadContent(content, file)
            } catch (e: SecurityException) {
                fail(e.message ?: "File too large")
            } catch (e: Exception) {
                fail("Failed to load file: ${e.message}")
            }
        }
    }

    private fun loadContent(content: String, source: Any) {
        currentContent = content
        val file = source as? File
        _uiState.value = _uiState.value.copy(
            editorText = content,
            currentFile = file,
            consoleEntries = listOf(
                ConsoleEntry("=== File loaded ===", isError = false),
                ConsoleEntry("Size: ${content.length} chars", isError = false)
            ),
            isReadingFile = false
        )
        AppLogger.i("GSCompiler", "Loaded file (${content.length} chars) from: $source")
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(
            isReadingFile = false,
            errorMessage = message
        )
        addConsoleEntry(message, isError = true)
    }

    fun saveFile() {
        val file = _uiState.value.currentFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileManager.writeFileContent(file, currentContent)
                addConsoleEntry("=== Saved: ${file.name} ===", isError = false)
            } catch (e: Exception) {
                addConsoleEntry("Failed to save: ${e.message}", isError = true)
            }
        }
    }

    fun saveAs(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileManager.writeFileContent(file, currentContent)
                _uiState.value = _uiState.value.copy(currentFile = file)
                addConsoleEntry("=== Saved as: ${file.absolutePath} ===", isError = false)
            } catch (e: Exception) {
                addConsoleEntry("Failed to save: ${e.message}", isError = true)
            }
        }
    }

    fun compile() {
        val file = _uiState.value.currentFile ?: run {
            addConsoleEntry("No file selected. Save or open a .pwn file first.", isError = true)
            return
        }

        if (!file.name.endsWith(".pwn", ignoreCase = true)) {
            addConsoleEntry("Not a .pwn file: ${file.name}", isError = true)
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(isCompiling = true, isCompileSuccess = null)

            addConsoleEntry("\n=== Compilation started ===", isError = false)
            addConsoleEntry("Input: ${file.absolutePath}", isError = false)

            withContext(Dispatchers.IO) {
                FileManager.writeFileContent(file, currentContent)
            }

            val outputFile = FileManager.getSuggestedOutputFile(file)
            val includePaths = _uiState.value.detectedIncludes

            if (includePaths.isNotEmpty()) {
                addConsoleEntry("Include paths (${includePaths.size}):", isError = false)
                includePaths.forEach { path ->
                    addConsoleEntry("  -i $path", isError = false)
                }
            }

            val callback = object : CompilationCallback {
                override fun onOutput(text: String, isError: Boolean) {
                    addConsoleEntry(text.trimEnd(), isError)
                }
            }

            val result = withContext(Dispatchers.IO) {
                compiler.compile(
                    inputFile = file,
                    outputFile = outputFile,
                    includePaths = includePaths,
                    callback = callback
                )
            }

            if (result.success) {
                addConsoleEntry("\n=== Compilation successful! ===", isError = false)
                addConsoleEntry("Output: ${result.outputPath}", isError = false)
            } else {
                addConsoleEntry("\n=== Compilation failed ===", isError = true)
            }

            _uiState.value = _uiState.value.copy(
                isCompiling = false,
                isCompileSuccess = result.success
            )
        }
    }

    fun toggleConsole() {
        _uiState.value = _uiState.value.copy(
            consoleExpanded = !_uiState.value.consoleExpanded
        )
    }

    fun clearConsole() {
        _uiState.value = _uiState.value.copy(consoleEntries = emptyList())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun addConsoleEntry(text: String, isError: Boolean) {
        _consoleChannel.trySend(ConsoleEntry(text = text, isError = isError))
    }
}
