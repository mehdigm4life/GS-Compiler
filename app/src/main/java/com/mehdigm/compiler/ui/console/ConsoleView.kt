package com.mehdigm.compiler.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mehdigm.compiler.ui.theme.GSColors

data class ConsoleEntry(
    val text: String,
    val isError: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleView(
    entries: List<ConsoleEntry>,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GSColors.TerminalBackground)
    ) {
        /* Console header */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GSColors.DarkSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[Console]",
                style = MaterialTheme.typography.labelSmall,
                color = GSColors.TerminalGreen
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${entries.size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(end = 8.dp)
                )

                IconButton(
                    onClick = {
                        val allText = entries.joinToString("\n") { it.text }
                        clipboardManager.setText(AnnotatedString(allText))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy All Logs",
                        tint = GSColors.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (onToggleExpanded != null) {
                    TextButton(
                        onClick = onToggleExpanded,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = if (expanded) "▲" else "▼",
                            color = GSColors.AccentBlue,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        /* Console content */
        if (expanded) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 100.dp, max = 300.dp)
                    .verticalScroll(scrollState)
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                val annotatedLog = buildAnnotatedString {
                    entries.forEach { entry ->
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = if (entry.isError) GSColors.ErrorRed else GSColors.TerminalGreen,
                                fontWeight = if (entry.isError) FontWeight.Bold else FontWeight.Normal
                            )
                        ) {
                            append(entry.text)
                            append("\n")
                        }
                    }
                    if (entries.isEmpty()) {
                        withStyle(SpanStyle(color = Color.Gray, fontSize = 12.sp)) {
                            append("Ready to compile...")
                        }
                    }
                }

                Text(text = annotatedLog)
            }
        }
    }
}
