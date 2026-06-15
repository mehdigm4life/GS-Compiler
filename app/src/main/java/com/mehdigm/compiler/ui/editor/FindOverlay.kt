package com.mehdigm.compiler.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.sp
import com.mehdigm.compiler.ui.theme.GSColors

@Composable
fun FindOverlay(
    editorHandle: SoraEditorHandle,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var gotoLineText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        color = GSColors.DarkSurface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        editorHandle.clearSearch()
                        onDismiss()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search",
                        tint = GSColors.TextGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = GSColors.AccentGold,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(GSColors.DarkBackground)
                        .border(1.dp, GSColors.AccentGold.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { newValue ->
                            searchQuery = newValue
                            editorHandle.search(newValue)
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = GSColors.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(GSColors.AccentGold),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search in file...",
                                        color = GSColors.TextGray,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                innerTextField()
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { editorHandle.searchNext() }
                        )
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { editorHandle.searchPrevious() },
                    modifier = Modifier.size(32.dp),
                    enabled = editorHandle.searchMatchCount > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous match",
                        tint = if (editorHandle.searchMatchCount > 0) GSColors.White else GSColors.TextGray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = { editorHandle.searchNext() },
                    modifier = Modifier.size(32.dp),
                    enabled = editorHandle.searchMatchCount > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next match",
                        tint = if (editorHandle.searchMatchCount > 0) GSColors.White else GSColors.TextGray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(GSColors.DarkBackground)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (editorHandle.searchMatchCount > 0)
                            "${editorHandle.searchCurrentIndex}/${editorHandle.searchMatchCount}"
                        else
                            "0/0",
                        color = if (editorHandle.searchMatchCount > 0) GSColors.AccentGold else GSColors.TextGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FindReplace,
                    contentDescription = null,
                    tint = GSColors.ErrorRed,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(GSColors.DarkBackground)
                        .border(1.dp, GSColors.ErrorRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                ) {
                    BasicTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = GSColors.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(GSColors.ErrorRed),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (replaceText.isEmpty()) {
                                    Text(
                                        text = "Replace with...",
                                        color = GSColors.TextGray,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                TextButton(
                    onClick = {
                        if (searchQuery.isNotEmpty() && replaceText.isNotEmpty() && editorHandle.searchMatchCount > 0) {
                            editorHandle.replaceCurrent(replaceText)
                        }
                    },
                    enabled = searchQuery.isNotEmpty() && editorHandle.searchMatchCount > 0,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = GSColors.ErrorRed
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        "Replace",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = {
                        if (searchQuery.isNotEmpty()) {
                            editorHandle.replaceAll(replaceText)
                        }
                    },
                    enabled = searchQuery.isNotEmpty() && editorHandle.searchMatchCount > 0,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = GSColors.ErrorRed
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        "Replace All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = GSColors.AccentBlue,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(GSColors.DarkBackground)
                        .border(1.dp, GSColors.AccentBlue.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                ) {
                    BasicTextField(
                        value = gotoLineText,
                        onValueChange = { gotoLineText = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = GSColors.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(GSColors.AccentBlue),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (gotoLineText.isEmpty()) {
                                    Text(
                                        text = "Line #",
                                        color = GSColors.TextGray,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                innerTextField()
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                keyboardController?.hide()
                                val line = gotoLineText.toIntOrNull()
                                if (line != null && line > 0) {
                                    editorHandle.gotoLine(line - 1)
                                }
                            }
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        keyboardController?.hide()
                        val line = gotoLineText.toIntOrNull()
                        if (line != null && line > 0) {
                            editorHandle.gotoLine(line - 1)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = GSColors.AccentBlue
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        "Go to Line",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${editorHandle.getLineCount()} lines",
                    color = GSColors.TextGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
