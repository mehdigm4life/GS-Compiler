package com.mehdigm.compiler.ui.console

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mehdigm.compiler.compiler.CompilationCallback
import com.mehdigm.compiler.compiler.NativeCompiler
import com.mehdigm.compiler.storage.FileManager
import com.mehdigm.compiler.ui.editor.getEditorText
import com.mehdigm.compiler.ui.editor.removeEditor
import com.mehdigm.compiler.ui.editor.setEditorText
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
private const val MAX_CONTENT_SIZE_MB = 50

data class EditorTab(
    val id: Long = idCounter++,
    val uri: Uri? = null,
    val file: File? = null,
    val displayName: String = "untitled.pwn",
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val isDirty: Boolean = false,
    val contentLoaded: Boolean = false,
) {
    companion object {
        private var idCounter = System.nanoTime()
    }
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
    val requestSaveAs: Boolean = false,
    val sessionVersion: Int = 0,
) {
    val activeTab: EditorTab? get() = tabs.getOrNull(activeTabIndex)
    val currentFile: File? get() = activeTab?.file
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

    private fun withActiveTab(block: (EditorTab) -> EditorTab) {
        val s = _uiState.value
        val tabs = s.tabs.toMutableList()
        val idx = s.activeTabIndex
        if (idx in tabs.indices) {
            tabs[idx] = block(tabs[idx])
            _uiState.value = s.copy(tabs = tabs)
        }
    }

    fun onContentChanged() {
        withActiveTab { it.copy(isDirty = true) }
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

    fun switchTab(context: Context, index: Int) {
        val s = _uiState.value
        if (s.activeTabIndex == index) return

        val oldIdx = s.activeTabIndex
        updateCursorPosition(oldIdx, s.tabs.getOrNull(oldIdx)?.cursorLine ?: 0, s.tabs.getOrNull(oldIdx)?.cursorColumn ?: 0)

        val newTab = s.tabs.getOrNull(index) ?: return

        _uiState.value = s.copy(activeTabIndex = index)

        val editorExists = getEditorText(newTab.id) != null
        val needsLoad = !newTab.contentLoaded || !editorExists

        viewModelScope.launch(Dispatchers.IO) {
            if (needsLoad) {
                var content = ""
                if (newTab.file != null && newTab.file.exists()) {
                    try {
                        content = FileManager.readFileContent(newTab.file)
                    } catch (e: Exception) {
                        AppLogger.e("GSCompiler", "Failed to read ${newTab.file.path}: ${e.message}")
                    }
                } else if (newTab.uri != null) {
                    try {
                        val c = FileManager.readFromUri(context, newTab.uri)
                        if (c != null) content = c
                    } catch (e: Exception) {
                        AppLogger.e("GSCompiler", "Failed to read URI: ${e.message}")
                    }
                }
                if (content.isNotEmpty()) {
                    setEditorText(newTab.id, content)
                    val tabs = _uiState.value.tabs.toMutableList()
                    if (index in tabs.indices) {
                        tabs[index] = tabs[index].copy(contentLoaded = true, isDirty = false)
                    }
                    _uiState.value = _uiState.value.copy(tabs = tabs)
                }
            }
        }
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

    private val isSaving = java.util.concurrent.atomic.AtomicBoolean(false)

    fun saveSession(context: Context) {
        if (!isSaving.compareAndSet(false, true)) return
        val tabs = _uiState.value.tabs
        val activeIndex = _uiState.value.activeTabIndex
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                for (tab in tabs) {
                    val obj = JSONObject()
                    tab.uri?.toString()?.let { obj.put("uri", it) }
                    tab.file?.absolutePath?.let { obj.put("filePath", it) }
                    obj.put("displayName", tab.displayName)
                    obj.put("cursorLine", tab.cursorLine)
                    obj.put("cursorColumn", tab.cursorColumn)
                    arr.put(obj)
                }

                context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TABS, arr.toString())
                    .putInt(KEY_ACTIVE_INDEX, activeIndex)
                    .apply()
                AppLogger.i("GSCompiler", "Session saved: ${tabs.size} tabs")
            } catch (e: Exception) {
                AppLogger.e("GSCompiler", "Failed to save session: ${e.message}")
            } finally {
                isSaving.set(false)
            }
        }
    }

    fun restoreSession(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
                val tabsJson = prefs.getString(KEY_TABS, null) ?: return@launch
                val activeIndex = prefs.getInt(KEY_ACTIVE_INDEX, 0)
                val arr = JSONArray(tabsJson)

                val tabs = mutableListOf<EditorTab>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val uri = if (obj.has("uri")) Uri.parse(obj.getString("uri")) else null
                    val filePath = if (obj.has("filePath")) obj.getString("filePath") else null
                    val file = if (filePath != null) File(filePath) else null
                    val displayName = obj.optString("displayName", "untitled.pwn")
                    val cursorLine = obj.optInt("cursorLine", 0)
                    val cursorColumn = obj.optInt("cursorColumn", 0)

                    val isActive = i == activeIndex
                    var contentLoaded = false

                    if (isActive && file != null && file.exists()) {
                        try {
                            val content = FileManager.readFileContent(file)
                            if (content.isNotEmpty()) {
                                setEditorText(tabs.size.toLong(), content)
                                contentLoaded = true
                            }
                        } catch (e: Exception) {
                            AppLogger.e("GSCompiler", "Failed to read ${file.path}: ${e.message}")
                        }
                    } else if (isActive && uri != null) {
                        try {
                            val content = FileManager.readFromUri(context, uri)
                            if (!content.isNullOrEmpty()) {
                                setEditorText(tabs.size.toLong(), content)
                                contentLoaded = true
                            }
                        } catch (e: Exception) {
                            AppLogger.e("GSCompiler", "Failed to read URI: ${e.message}")
                        }
                    }

                    val tab = EditorTab(
                        id = tabs.size.toLong(),
                        uri = uri,
                        file = file,
                        displayName = displayName,
                        cursorLine = cursorLine,
                        cursorColumn = cursorColumn,
                        isDirty = false,
                        contentLoaded = contentLoaded,
                    )
                    tabs.add(tab)
                }

                if (tabs.isNotEmpty()) {
                    val s = _uiState.value
                    val activeIdx = activeIndex.coerceIn(0, tabs.lastIndex)
                    _uiState.value = s.copy(
                        tabs = tabs,
                        activeTabIndex = activeIdx,
                        sessionVersion = s.sessionVersion + 1,
                    )
                    AppLogger.i("GSCompiler", "Session restored: ${tabs.size} tabs")
                }
            } catch (e: Exception) {
                AppLogger.e("GSCompiler", "Failed to restore session: ${e.message}")
                context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            }
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
        val s = _uiState.value

        val existing = when {
            uri != null -> s.tabs.indexOfFirst { uri == it.uri }
            file != null -> s.tabs.indexOfFirst { file.absolutePath == it.file?.absolutePath }
            else -> -1
        }

        if (existing >= 0) {
            val tabs = s.tabs.toMutableList()
            tabs[existing] = tabs[existing].copy(
                uri = uri ?: tabs[existing].uri,
                file = file ?: tabs[existing].file,
                displayName = displayName ?: tabs[existing].displayName,
                contentLoaded = false,
                isDirty = false,
            )
            val newEntries = s.consoleEntries + listOf(
                ConsoleEntry("=== File reloaded ===", isError = false),
            )
            val trimmedEntries = if (newEntries.size > MAX_CONSOLE_ENTRIES) {
                newEntries.drop(newEntries.size - MAX_CONSOLE_ENTRIES)
            } else newEntries
            setEditorText(tabs[existing].id, content)
            tabs[existing] = tabs[existing].copy(contentLoaded = true)
            _uiState.value = s.copy(
                tabs = tabs,
                activeTabIndex = existing,
                consoleEntries = trimmedEntries,
                isReadingFile = false
            )
            return
        }

        val name = displayName ?: file?.name ?: "untitled.pwn"
        val tab = EditorTab(uri = uri, file = file, displayName = name, isDirty = false, contentLoaded = false)
        val tabId = tab.id
        val tabs = s.tabs.toMutableList()
        tabs.add(tab)
        setEditorText(tabId, content)
        tabs[tabs.lastIndex] = tabs.last().copy(contentLoaded = true)
        val newEntries = s.consoleEntries + listOf(
            ConsoleEntry("=== File loaded ===", isError = false),
        )
        val trimmedEntries = if (newEntries.size > MAX_CONSOLE_ENTRIES) {
            newEntries.drop(newEntries.size - MAX_CONSOLE_ENTRIES)
        } else newEntries
        _uiState.value = s.copy(
            tabs = tabs,
            activeTabIndex = tabs.lastIndex,
            consoleEntries = trimmedEntries,
            isReadingFile = false
        )
        AppLogger.i("GSCompiler", "Loaded file from: ${uri ?: file}")
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
                val content = getEditorText(tab.id) ?: return@launch
                FileManager.writeToUri(context, uri, content)
                val tabs = _uiState.value.tabs.toMutableList()
                val idx = _uiState.value.activeTabIndex
                if (idx in tabs.indices) {
                    tabs[idx] = tabs[idx].copy(isDirty = false)
                }
                _uiState.value = _uiState.value.copy(
                    tabs = tabs,
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
                val content = getEditorText(tab.id) ?: return@launch
                FileManager.writeToUri(context, uri, content)
                val displayName = FileManager.getFileNameFromUri(context, uri)
                val tabs = _uiState.value.tabs.toMutableList()
                if (idx in tabs.indices) {
                    tabs[idx] = tabs[idx].copy(uri = uri, isDirty = false, displayName = displayName)
                }
                _uiState.value = _uiState.value.copy(
                    tabs = tabs,
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
        if (_uiState.value.tabs.any { it.isDirty }) {
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
        val targetIdx = tabIdx ?: s.activeTabIndex
        val tab = s.tabs.getOrNull(targetIdx) ?: return
        val file = tab.file
        if (file != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val content = getEditorText(tab.id) ?: return@launch
                    FileManager.writeFileContent(file, content)
                } catch (_: Exception) { }
            }
        }
        val tabs = _uiState.value.tabs.toMutableList()
        if (targetIdx in tabs.indices) {
            tabs[targetIdx] = tabs[targetIdx].copy(isDirty = false)
        }
        if (tabIdx != null) {
            doCloseTab(tabIdx)
        }
        _uiState.value = _uiState.value.copy(
            tabs = tabs,
            showUnsavedDialog = false,
            unsavedDialogTabIndex = null
        )
    }

    fun handleUnsavedDismiss() {
        val tabIdx = _uiState.value.unsavedDialogTabIndex
        if (tabIdx != null) {
            doCloseTab(tabIdx)
        } else {
            _uiState.value = _uiState.value.copy(
                showUnsavedDialog = false,
                unsavedDialogTabIndex = null
            )
        }
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
            removeEditor(s.tabs.firstOrNull()?.id ?: return)
            val newTab = EditorTab()
            _uiState.value = s.copy(
                tabs = listOf(newTab),
                activeTabIndex = 0,
                showUnsavedDialog = false
            )
            return
        }
        val tabs = s.tabs.toMutableList()
        removeEditor(tabs[index].id)
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

            val content = withContext(Dispatchers.IO) {
                getEditorText(tab.id) ?: ""
            }
            if (content.isEmpty()) {
                addConsoleEntry("Error: No content to compile", isError = true)
                _uiState.value = _uiState.value.copy(isCompiling = false, isCompileSuccess = false)
                return@launch
            }

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
