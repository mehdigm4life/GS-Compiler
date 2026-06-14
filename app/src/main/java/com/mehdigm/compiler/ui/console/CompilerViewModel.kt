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
import java.io.File

data class CompilerUiState(
    val editorValue: TextFieldValue = TextFieldValue(""),
    val currentFile: File? = null,
    val consoleEntries: List<ConsoleEntry> = emptyList(),
    val isCompiling: Boolean = false,
    val isCompileSuccess: Boolean? = false,
    val detectedIncludes: List<String> = emptyList(),
    val consoleExpanded: Boolean = true
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
                _uiState.value = _uiState.value.copy(
                    consoleEntries = _uiState.value.consoleEntries + entry
                )
            }
        }
    }

    fun setEditorValue(value: TextFieldValue) {
        _uiState.value = _uiState.value.copy(editorValue = value)
        currentContent = value.text
    }

    fun loadFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                addConsoleEntry("Reading file...", isError = false)
                val content = withContext(Dispatchers.IO) {
                    FileManager.readFromDocument(context, uri)
                }
                if (content != null) {
                    currentContent = content
                    _uiState.value = _uiState.value.copy(
                        editorValue = TextFieldValue(content),
                        consoleEntries = listOf(
                            ConsoleEntry("=== File loaded from content URI ===", isError = false),
                            ConsoleEntry("Size: ${content.length} chars", isError = false)
                        )
                    )
                    AppLogger.i("GSCompiler", "Loaded file (${content.length} chars) from URI: $uri")
                } else {
                    addConsoleEntry("Failed to read file: content resolver returned null", isError = true)
                    AppLogger.e("GSCompiler", "readFromDocument returned null for URI: $uri")
                }
            } catch (e: Exception) {
                addConsoleEntry("Failed to open file: ${e.message}", isError = true)
                AppLogger.e("GSCompiler", "Error reading URI: $uri - ${e.message}")
            }
        }
    }

    fun loadFile(file: File) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    FileManager.readFileContent(file)
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
                addConsoleEntry("Failed to load file: ${e.message}", isError = true)
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

    private fun addConsoleEntry(text: String, isError: Boolean) {
        _consoleChannel.trySend(ConsoleEntry(text = text, isError = isError))
    }
}
