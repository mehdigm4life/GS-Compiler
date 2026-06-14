package com.mehdigm.compiler.ui.console

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehdigm.compiler.compiler.CompilationCallback
import com.mehdigm.compiler.compiler.NativeCompiler
import com.mehdigm.compiler.include.IncludeDetector
import com.mehdigm.compiler.storage.FileManager
import com.mehdigm.compiler.utils.AppLogger
import kotlinx.coroutines.Dispatchers
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
    val editorValue: TextFieldValue = TextFieldValue(""),
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
        viewModelScope.launch {
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

    fun setEditorValue(value: TextFieldValue) {
        _uiState.value = _uiState.value.copy(editorValue = value)
        currentContent = value.text
    }

    fun loadFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReadingFile = true, errorMessage = null)
            try {
                val content = withContext(Dispatchers.IO) {
                    withTimeout(FILE_READ_TIMEOUT_MS) {
                        FileManager.readFromDocument(context, uri)
                    }
                }
                if (content != null) {
                    currentContent = content
                    _uiState.value = _uiState.value.copy(
                        editorValue = TextFieldValue(content),
                        currentFile = null,
                        consoleEntries = listOf(
                            ConsoleEntry("=== File loaded ===", isError = false),
                            ConsoleEntry("Size: ${content.length} chars", isError = false)
                        ),
                        isReadingFile = false
                    )
                    AppLogger.i("GSCompiler", "Loaded file (${content.length} chars) from URI: $uri")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isReadingFile = false,
                        errorMessage = "Failed to read file: content resolver returned null"
                    )
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(
                    isReadingFile = false,
                    errorMessage = "File read timed out after ${FILE_READ_TIMEOUT_MS / 1000}s"
                )
                AppLogger.e("GSCompiler", "File read timeout for URI: $uri")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isReadingFile = false,
                    errorMessage = "Failed to open file: ${e.message}"
                )
                AppLogger.e("GSCompiler", "Error reading URI: $uri - ${e.message}")
            }
        }
    }

    fun loadFile(file: File) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    withTimeout(FILE_READ_TIMEOUT_MS) {
                        FileManager.readFileContent(file)
                    }
                }
                currentContent = content

                val includeResult = withContext(Dispatchers.IO) {
                    IncludeDetector.detect(file)
                }

                _uiState.value = _uiState.value.copy(
                    editorValue = TextFieldValue(content),
                    currentFile = file,
                    consoleEntries = listOf(
                        ConsoleEntry("=== File loaded: ${file.name} ===", isError = false),
                        ConsoleEntry("Path: ${file.absolutePath}", isError = false),
                        ConsoleEntry("Detected includes: ${includeResult.includePaths.size} paths", isError = false)
                    ) + includeResult.includePaths.map { path ->
                        ConsoleEntry("  Include: $path", isError = false)
                    },
                    detectedIncludes = includeResult.includePaths
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to load file: ${e.message}"
                )
            }
        }
    }

    fun saveFile() {
        val file = _uiState.value.currentFile ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FileManager.writeFileContent(file, currentContent)
                }
                addConsoleEntry("=== Saved: ${file.name} ===", isError = false)
            } catch (e: Exception) {
                addConsoleEntry("Failed to save: ${e.message}", isError = true)
            }
        }
    }

    fun saveAs(file: File) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FileManager.writeFileContent(file, currentContent)
                }
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

        viewModelScope.launch {
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
