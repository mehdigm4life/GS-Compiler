package com.mehdigm.compiler.ui.console

import android.content.Context
import android.content.SharedPreferences
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
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_CONSOLE_ENTRIES = 500
private const val FILE_READ_TIMEOUT_MS = 15_000L

data class EditorTab(
    val uri: Uri? = null,
    val file: File? = null,
    val content: String = "",
    val savedContent: String = "",
    val displayName: String = "untitled.pwn",
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
) {
    val isDirty: Boolean get() = content != savedContent
}

data class CompilerUiState(
    val tabs: List<EditorTab> = listOf(EditorTab()),
    val activeTabIndex: Int = 0,
    val consoleEntries: List<ConsoleEntry> = emptyList(),
    val isCompiling: Boolean = false,
    val isCompileSuccess: Boolean? = null,
    val detectedIncludes: List<String> = emptyList(),
    val consoleExpanded: Boolean = true,
    val isReadingFile: Boolean = false,
    val errorMessage: String? = null,
    val showFileSavedToast: Boolean = false,
    val fileSavedSuccess: Boolean = false,
    val showUnsavedDialog: Boolean = false,
    val unsavedDialogTabIndex: Int? = null,
    val requestSaveAs: Boolean = false
) {
    val activeTab: EditorTab? get() = tabs.getOrNull(activeTabIndex)
    val editorText: String get() = activeTab?.content ?: ""
    val currentFile: File? get() = activeTab?.file
    val isDirty: Boolean get() = activeTab?.isDirty ?: false
}

class CompilerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CompilerUiState())
    val uiState: StateFlow<CompilerUiState> = _uiState.asStateFlow()

    private val compiler = NativeCompiler()

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

    private fun getActiveContent(): String = _uiState.value.activeTab?.content ?: ""

    private fun withActiveTab(block: (EditorTab) -> EditorTab) {
        val s = _uiState.value
        val tabs = s.tabs.toMutableList()
        val idx = s.activeTabIndex
        if (idx in tabs.indices) {
            tabs[idx] = block(tabs[idx])
            _uiState.value = s.copy(tabs = tabs)
        }
    }

    fun setEditorText(text: String) {
        withActiveTab { it.copy(content = text) }
    }

    fun newFile() {
        val tab = EditorTab()
        val s = _uiState.value
        _uiState.value = s.copy(
            tabs = s.tabs + tab,
            activeTabIndex = s.tabs.size,
            isReadingFile = false,
            errorMessage = null
        )
    }

    fun switchTab(index: Int) {
        _uiState.value = _uiState.value.copy(activeTabIndex = index)
    }

    fun updateCursorPosition(index: Int, line: Int, column: Int) {
        val tabs = _uiState.value.tabs.toMutableList()
        if (index in tabs.indices) {
            tabs[index] = tabs[index].copy(cursorLine = line, cursorColumn = column)
            _uiState.value = _uiState.value.copy(tabs = tabs)
        }
    }

    fun updateActiveCursor(line: Int, column: Int) {
        updateCursorPosition(_uiState.value.activeTabIndex, line, column)
    }

    private val SESSION_PREFS = "session_prefs"
    private val KEY_TABS = "tabs"
    private val KEY_ACTIVE_INDEX = "active_index"

    fun saveSession(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val tabs = _uiState.value.tabs
        val arr = JSONArray()
        for (tab in tabs) {
            val obj = JSONObject()
            tab.uri?.toString()?.let { obj.put("uri", it) }
            tab.file?.absolutePath?.let { obj.put("filePath", it) }
            obj.put("displayName", tab.displayName)
            obj.put("cursorLine", tab.cursorLine)
            obj.put("cursorColumn", tab.cursorColumn)
            obj.put("content", tab.content)
            obj.put("savedContent", tab.savedContent)
            arr.put(obj)
        }
        prefs.edit()
            .putString(KEY_TABS, arr.toString())
            .putInt(KEY_ACTIVE_INDEX, _uiState.value.activeTabIndex)
            .apply()
        AppLogger.i("GSCompiler", "Session saved: ${tabs.size} tabs")
    }

    fun restoreSession(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val tabsJson = prefs.getString(KEY_TABS, null) ?: return
        val activeIndex = prefs.getInt(KEY_ACTIVE_INDEX, 0)
        try {
            val arr = JSONArray(tabsJson)
            val tabs = mutableListOf<EditorTab>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uri = if (obj.has("uri")) Uri.parse(obj.getString("uri")) else null
                val filePath = if (obj.has("filePath")) obj.getString("filePath") else null
                val file = if (filePath != null) File(filePath) else null
                val tab = EditorTab(
                    uri = uri,
                    file = file,
                    content = obj.getString("content"),
                    savedContent = obj.optString("savedContent", ""),
                    displayName = obj.getString("displayName"),
                    cursorLine = obj.optInt("cursorLine", 0),
                    cursorColumn = obj.optInt("cursorColumn", 0),
                )
                tabs.add(tab)
            }
            if (tabs.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    tabs = tabs,
                    activeTabIndex = activeIndex.coerceIn(0, tabs.lastIndex),
                )
                AppLogger.i("GSCompiler", "Session restored: ${tabs.size} tabs")
            }
        } catch (e: Exception) {
            AppLogger.e("GSCompiler", "Failed to restore session: ${e.message}")
        }
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
                    val displayName = FileManager.getFileNameFromUri(context, uri)
                    val file = if (uri.scheme == "file") File(uri.path!!) else null
                    addTab(content, uri = uri, file = file, displayName = displayName)
                } else {
                    fail("Failed to read file from URI")
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                fail("File read timed out after ${FILE_READ_TIMEOUT_MS / 1000}s")
                AppLogger.e("GSCompiler", "File read timeout for URI: $uri")
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
                addTab(content, uri = Uri.fromFile(file), file = file)
            } catch (e: Exception) {
                fail("Failed to load file: ${e.message}")
            }
        }
    }

    private fun addTab(content: String, uri: Uri? = null, file: File? = null, displayName: String? = null) {
        val existing = when {
            uri != null -> _uiState.value.tabs.indexOfFirst { uri == it.uri }
            file != null -> _uiState.value.tabs.indexOfFirst { file.absolutePath == it.file?.absolutePath }
            else -> -1
        }
        if (existing >= 0) {
            val tabs = _uiState.value.tabs.toMutableList()
            tabs[existing] = tabs[existing].copy(
                content = content,
                savedContent = content,
                uri = uri ?: tabs[existing].uri,
                file = file ?: tabs[existing].file,
                displayName = displayName ?: tabs[existing].displayName
            )
            _uiState.value = _uiState.value.copy(
                tabs = tabs,
                activeTabIndex = existing,
                isReadingFile = false
            )
            return
        }
        val name = displayName ?: file?.name ?: "untitled.pwn"
        val tab = EditorTab(uri = uri, file = file, content = content, savedContent = content, displayName = name)
        val s = _uiState.value
        _uiState.value = s.copy(
            tabs = s.tabs + tab,
            activeTabIndex = s.tabs.size,
            consoleEntries = listOf(
                ConsoleEntry("=== File loaded ===", isError = false),
                ConsoleEntry("Size: ${content.length} chars", isError = false)
            ),
            isReadingFile = false
        )
        AppLogger.i("GSCompiler", "Loaded file (${content.length} chars) from: ${uri ?: file}")
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(
            isReadingFile = false,
            errorMessage = message
        )
        addConsoleEntry(message, isError = true)
    }

    fun saveCurrentTab(context: Context) {
        val s = _uiState.value
        val tab = s.activeTab ?: return
        val uri = tab.uri
        if (uri == null) {
            _uiState.value = s.copy(requestSaveAs = true)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileManager.writeToUri(context, uri, tab.content)
                withActiveTab { it.copy(savedContent = it.content) }
                _uiState.value = _uiState.value.copy(
                    showFileSavedToast = true,
                    fileSavedSuccess = true
                )
                addConsoleEntry("=== Saved: ${tab.displayName} ===", isError = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showFileSavedToast = true,
                    fileSavedSuccess = false
                )
                addConsoleEntry("Failed to save: ${e.message}", isError = true)
            }
        }
    }

    fun clearRequestSaveAs() {
        _uiState.value = _uiState.value.copy(requestSaveAs = false)
    }

    fun saveToUri(context: Context, uri: Uri) {
        val s = _uiState.value
        val idx = s.activeTabIndex
        val tab = s.tabs.getOrNull(idx) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileManager.writeToUri(context, uri, tab.content)
                val displayName = FileManager.getFileNameFromUri(context, uri)
                withActiveTab {
                    it.copy(uri = uri, savedContent = it.content, displayName = displayName)
                }
                _uiState.value = _uiState.value.copy(
                    showFileSavedToast = true,
                    fileSavedSuccess = true
                )
                addConsoleEntry("=== Saved: $displayName ===", isError = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showFileSavedToast = true,
                    fileSavedSuccess = false
                )
                addConsoleEntry("Failed to save: ${e.message}", isError = true)
            }
        }
    }

    fun clearSavedToast() {
        _uiState.value = _uiState.value.copy(showFileSavedToast = false)
    }

    fun requestCloseTab(index: Int) {
        val tab = _uiState.value.tabs.getOrNull(index) ?: return
        if (tab.isDirty) {
            _uiState.value = _uiState.value.copy(
                showUnsavedDialog = true,
                unsavedDialogTabIndex = index
            )
        } else {
            doCloseTab(index)
        }
    }

    fun requestExit() {
        if (_uiState.value.isDirty) {
            _uiState.value = _uiState.value.copy(
                showUnsavedDialog = true,
                unsavedDialogTabIndex = null
            )
        } else {
            _uiState.value = _uiState.value.copy(showUnsavedDialog = false)
        }
    }

    fun handleUnsavedSave() {
        val s = _uiState.value
        val tabIdx = s.unsavedDialogTabIndex
        val tab = s.tabs.getOrNull(tabIdx ?: s.activeTabIndex) ?: return
        val file = tab.file
        if (file != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    FileManager.writeFileContent(file, tab.content)
                } catch (_: Exception) { }
            }
        }
        if (tabIdx != null) {
            withActiveTab { it.copy(savedContent = it.content) }
            doCloseTab(tabIdx)
        }
        _uiState.value = _uiState.value.copy(
            showUnsavedDialog = false,
            unsavedDialogTabIndex = null
        )
    }

    fun handleUnsavedDismiss() {
        val tabIdx = _uiState.value.unsavedDialogTabIndex
        if (tabIdx != null) {
            doCloseTab(tabIdx)
        }
        _uiState.value = _uiState.value.copy(
            showUnsavedDialog = false,
            unsavedDialogTabIndex = null
        )
    }

    fun handleUnsavedCancel() {
        _uiState.value = _uiState.value.copy(
            showUnsavedDialog = false,
            unsavedDialogTabIndex = null
        )
    }

    private fun doCloseTab(index: Int) {
        val s = _uiState.value
        if (s.tabs.size <= 1) {
            val newTab = EditorTab()
            _uiState.value = s.copy(
                tabs = listOf(newTab),
                activeTabIndex = 0,
                showUnsavedDialog = false
            )
            return
        }
        val tabs = s.tabs.toMutableList()
        tabs.removeAt(index)
        val newIdx = when {
            index < s.activeTabIndex -> s.activeTabIndex - 1
            index == s.activeTabIndex -> index.coerceAtMost(tabs.lastIndex)
            else -> s.activeTabIndex
        }
        _uiState.value = s.copy(
            tabs = tabs,
            activeTabIndex = newIdx,
            showUnsavedDialog = false
        )
    }

    fun compile(context: Context) {
        val s = _uiState.value
        val tab = s.activeTab ?: return

        if (!tab.displayName.endsWith(".pwn", ignoreCase = true)) {
            addConsoleEntry("Not a .pwn file: ${tab.displayName}", isError = true)
            return
        }

        viewModelScope.launch(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(isCompiling = true, isCompileSuccess = null)

            addConsoleEntry("\n=== Compilation started ===", isError = false)
            addConsoleEntry("Input: ${tab.displayName}", isError = false)

            val content = getActiveContent()
            val file = withContext(Dispatchers.IO) {
                val f = tab.file ?: File(context.cacheDir, tab.displayName)
                f.writeText(content, Charsets.UTF_8)
                f
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
