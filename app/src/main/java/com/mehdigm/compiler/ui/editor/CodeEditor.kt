package com.mehdigm.compiler.ui.editor

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.widget.CodeEditor as SoraCodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource

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

            try {
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
            } catch (_: Exception) {
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
        }
    }

    LaunchedEffect(Unit) {
        editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
            if (!skipNextEvent) {
                onTextChange(editor.getText().toString())
            }
        }
    }

    LaunchedEffect(text) {
        if (editor.getText().toString() != text) {
            skipNextEvent = true
            editor.setText(text)
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
