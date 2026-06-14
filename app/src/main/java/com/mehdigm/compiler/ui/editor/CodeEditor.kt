package com.mehdigm.compiler.ui.editor

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.widget.CodeEditor as SoraCodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.langs.textmate.registry.TextMateRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry

@Composable
fun CodeEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var skipNextEvent by remember { mutableStateOf(false) }

    val editor = remember {
        initTextMate(context)
        SoraCodeEditor(context).apply {
            isLineNumberEnabled = true
            isWordwrap = false

            colorScheme = EditorColorScheme().apply {
                setColor(EditorColorScheme.BACKGROUND, 0xFF1E1E2E.toInt())
                setColor(EditorColorScheme.TEXT, 0xFFA9B7C6.toInt())
                setColor(EditorColorScheme.LINE_NUMBER, 0xFF555555.toInt())
                setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF1E1E2E.toInt())
                setColor(EditorColorScheme.LINE_DIVIDER, 0x4D555555.toInt())
                setColor(EditorColorScheme.SELECTION_INSERT, 0xFF555555.toInt())
                setColor(EditorColorScheme.SELECTION_HANDLE, 0xFFD4AF37.toInt())
                setColor(EditorColorScheme.BLOCK_LINE, 0x33D4AF37.toInt())
                setColor(EditorColorScheme.BLOCK_LINE_CURRENT, 0x4DD4AF37.toInt())
            }

            try {
                val language = TextMateLanguage.create("source.pawn", true)
                setEditorLanguage(language)
                ThemeRegistry.getInstance().setTheme("dark")
            } catch (_: Exception) {
                // Fall back to plain text if TextMate fails
            }
        }
    }

    LaunchedEffect(Unit) {
        editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
            if (!skipNextEvent) {
                onTextChange(editor.text.toString())
            }
        }
    }

    LaunchedEffect(text) {
        if (editor.text.toString() != text) {
            skipNextEvent = true
            editor.setText(text, null)
            skipNextEvent = false
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { editor },
        onRelease = { it.release() }
    )
}

private var textMateInitialized = false

private fun initTextMate(context: Context) {
    if (textMateInitialized) return
    textMateInitialized = true

    try {
        TextMateRegistry.getInstance().loadGrammars(
            context.assets.open("textmate/grammars.json")
        )
        ThemeRegistry.getInstance().loadThemes(
            context.assets.open("textmate/themes.json")
        )
    } catch (_: Exception) {
        // TextMate init failed — editor will use plain text
    }
}
