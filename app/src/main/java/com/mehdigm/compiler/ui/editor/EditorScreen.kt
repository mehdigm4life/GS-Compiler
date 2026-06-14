package com.mehdigm.compiler.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mehdigm.compiler.ui.theme.GSColors
import com.mehdigm.compiler.ui.theme.EditorFontFamily

@Composable

fun PawnEditor(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val lineNumbersText = remember(textFieldValue.text) {
        val count = textFieldValue.text.count { it == '\n' } + 1
        (1..count).joinToString("\n") { it.toString().padStart(4) }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(GSColors.EditorBackground)
    ) {
        /* Line numbers gutter — single Text for all lines */
        Text(
            text = lineNumbersText,
            style = TextStyle(
                fontFamily = EditorFontFamily,
                fontSize = 13.sp,
                color = GSColors.LineNumberColor
            ),
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .verticalScroll(verticalScroll)
                .background(GSColors.EditorBackground)
                .padding(start = 4.dp, top = 8.dp, end = 4.dp)
        )

        /* Divider */
        Divider(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(),
            color = GSColors.LineNumberColor.copy(alpha = 0.3f)
        )

        /* Editor area */
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
                .padding(start = 8.dp, top = 8.dp)
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontFamily = EditorFontFamily,
                    fontSize = 13.sp,
                    color = GSColors.SyntaxDefault
                ),
                cursorBrush = SolidColor(GSColors.AccentGold),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
