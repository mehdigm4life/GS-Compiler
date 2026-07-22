package com.mehdigm.compiler.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val EditorFontFamily = FontFamily.Monospace

val GSCompilerTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = EditorFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = EditorFontFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = EditorFontFamily,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)
