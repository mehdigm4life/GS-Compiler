package com.mehdigm.compiler.ui.editor

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.widget.CodeEditor as SoraCodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource

class SoraEditorHandle {
    internal var editor: SoraCodeEditor? = null
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var searchMatchCount by mutableStateOf(0)
        private set
    var searchCurrentIndex by mutableStateOf(0)
        private set

    fun undo() {
        editor?.undo()
        syncState()
    }

    fun redo() {
        editor?.redo()
        syncState()
    }

    fun search(query: String) {
        val searcher = editor?.getSearcher() ?: return
        if (query.isEmpty()) {
            searcher.stopSearch()
            searchMatchCount = 0
            searchCurrentIndex = 0
            return
        }
        searcher.stopSearch()
        searcher.setCyclicJumping(true)
        searcher.search(query, EditorSearcher.SearchOptions(true, false))
        syncSearchState()
    }

    fun searchNext() {
        val searcher = editor?.getSearcher() ?: return
        searcher.gotoNext()
        syncSearchState()
    }

    fun searchPrevious() {
        val searcher = editor?.getSearcher() ?: return
        searcher.gotoPrevious()
        syncSearchState()
    }

    fun clearSearch() {
        editor?.getSearcher()?.stopSearch()
        searchMatchCount = 0
        searchCurrentIndex = 0
    }

    fun gotoLine(line: Int) {
        val e = editor ?: return
        val lineCount = e.lineCount
        if (line in 0 until lineCount) {
            e.setSelection(line, 0)
            e.ensurePositionVisible(line, 0)
        }
    }

    fun getCursorLine(): Int = editor?.cursor?.leftLine ?: 0
    fun getCursorColumn(): Int = editor?.cursor?.leftColumn ?: 0

    fun getLineCount(): Int = editor?.lineCount ?: 0

    internal fun syncSearchState() {
        val searcher = editor?.getSearcher() ?: return
        if (!searcher.hasQuery()) {
            searchMatchCount = 0
            searchCurrentIndex = 0
            return
        }
        searchMatchCount = searcher.matchedPositionCount
        searchCurrentIndex = if (searcher.isMatchedPositionSelected()) {
            searcher.currentMatchedPositionIndex + 1
        } else {
            0
        }
    }

    internal fun syncState() {
        canUndo = editor?.canUndo() ?: false
        canRedo = editor?.canRedo() ?: false
    }
}

@Composable
fun CodeEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editorHandle: SoraEditorHandle = remember { SoraEditorHandle() },
    tabId: Long = 0L,
    onCursorChange: ((Int, Int) -> Unit)? = null,
    initialCursorLine: Int = 0,
    initialCursorColumn: Int = 0,
    resetCounter: Int = 0,
    activeTabIds: List<Long> = emptyList(),
) {
    val context = LocalContext.current
    val editors = remember { mutableMapOf<Long, SoraCodeEditor>() }
    val editorLRU = remember { mutableListOf<Long>() }
    val MAX_EDITORS = 5

    val prevReset = remember { mutableStateOf(resetCounter) }
    if (resetCounter != prevReset.value) {
        editors.values.forEach { it.release() }
        editors.clear()
        editorLRU.clear()
        prevReset.value = resetCounter
    }

    // Clean up editors for tabs that no longer exist
    val staleIds = remember(activeTabIds) {
        editors.keys.filter { it !in activeTabIds && it != tabId }
    }
    staleIds.forEach { id ->
        editors.remove(id)?.release()
        editorLRU.remove(id)
    }

    val editor = remember(tabId, resetCounter) {
        // Update LRU: evict least recently used if at capacity
        editorLRU.remove(tabId)
        if (tabId !in editors && editors.size >= MAX_EDITORS) {
            val lruId = editorLRU.firstOrNull()
            if (lruId != null) {
                editorLRU.remove(lruId)
                editors.remove(lruId)?.release()
            }
        }
        editorLRU.add(tabId)

        editors.getOrPut(tabId) {
            initTextMate(context)
            SoraCodeEditor(context).apply {
                isLineNumberEnabled = true
                setPinLineNumber(true)
                isWordwrap = false
                setTextSize(12f)
                setLineInfoTextSize(10f)

                try {
                    val useTextMate = text.length < 500_000
                    if (useTextMate) {
                        val scheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                        scheme.setColor(EditorColorScheme.LINE_NUMBER, 0xFF555555.toInt())
                        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF1E1E2E.toInt())
                        scheme.setColor(EditorColorScheme.LINE_DIVIDER, 0x4D555555.toInt())
                        scheme.setColor(EditorColorScheme.SELECTION_INSERT, 0xFF555555.toInt())
                        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, 0xFFD4AF37.toInt())
                        scheme.setColor(EditorColorScheme.BLOCK_LINE, 0x33D4AF37.toInt())
                        scheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, 0x4DD4AF37.toInt())
                        colorScheme = scheme

                        val language = TextMateLanguage.create(
                            "source.pawn",
                            GrammarRegistry.getInstance(),
                            ThemeRegistry.getInstance(),
                            true
                        )
                        setEditorLanguage(language)
                    } else {
                        setThemeColors()
                    }
                } catch (_: Exception) {
                    setThemeColors()
                }

                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    onTextChange(getText().toString())
                    onCursorChange?.invoke(cursor.leftLine, cursor.leftColumn)
                    editorHandle.syncState()
                }
                subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
                    editorHandle.syncSearchState()
                }

                if (text.isNotEmpty()) {
                    setText(text)
                }
                post {
                    val currentText = getText().toString()
                    if (currentText != text && text.isNotEmpty()) {
                        setText(text)
                    }
                    val targetLine = initialCursorLine
                    val targetCol = if (targetLine in 0 until lineCount) {
                        initialCursorColumn.coerceIn(0, getText().getColumnCount(targetLine))
                    } else {
                        initialCursorColumn
                    }
                    if (targetLine in 0 until lineCount) {
                        setSelection(targetLine, targetCol)
                        ensurePositionVisible(targetLine, targetCol)
                        onCursorChange?.invoke(targetLine, targetCol)
                    }
                }
            }
        }
    }

    LaunchedEffect(editor) {
        editorHandle.editor = editor
        editorHandle.syncState()
    }

    key(tabId, resetCounter) {
        AndroidView(
            modifier = modifier,
            factory = { editor },
            onRelease = { }
        )
    }
}

private var textMateInitialized = false

private fun initTextMate(context: Context) {
    if (textMateInitialized) return
    textMateInitialized = true

    try {
        val registry = GrammarRegistry.getInstance()
        val grammarIs = context.assets.open("textmate/pawn.tmLanguage.json")
        val grammarSource = IGrammarSource.fromInputStream(
            grammarIs, "textmate/pawn.tmLanguage.json", Charsets.UTF_8
        )
        registry.loadGrammar(
            DefaultGrammarDefinition.withGrammarSource(grammarSource, "source.pawn", null)
        )

        val themeIs = context.assets.open("textmate/dark.tmTheme.json")
        ThemeRegistry.getInstance().loadTheme(
            IThemeSource.fromInputStream(themeIs, "dark.tmTheme.json", Charsets.UTF_8)
        )
    } catch (_: Exception) {
        // TextMate init failed — editor will use plain text
    }
}

private fun SoraCodeEditor.setThemeColors() {
    colorScheme = EditorColorScheme().apply {
        setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E2E.toInt())
        setColor(EditorColorScheme.TEXT_NORMAL, 0xFFA9B7C6.toInt())
        setColor(EditorColorScheme.LINE_NUMBER, 0xFF555555.toInt())
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF1E1E2E.toInt())
        setColor(EditorColorScheme.LINE_DIVIDER, 0x4D555555.toInt())
        setColor(EditorColorScheme.SELECTION_INSERT, 0xFF555555.toInt())
        setColor(EditorColorScheme.SELECTION_HANDLE, 0xFFD4AF37.toInt())
        setColor(EditorColorScheme.BLOCK_LINE, 0x33D4AF37.toInt())
        setColor(EditorColorScheme.BLOCK_LINE_CURRENT, 0x4DD4AF37.toInt())
    }
}
