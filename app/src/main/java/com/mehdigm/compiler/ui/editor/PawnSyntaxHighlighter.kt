package com.mehdigm.compiler.ui.editor

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.mehdigm.compiler.ui.theme.GSColors

object PawnSyntaxHighlighter {

    private val keywords = setOf(
        "assert", "break", "case", "const", "continue", "default", "do",
        "else", "enum", "for", "forward", "goto", "if", "native", "new",
        "operator", "public", "return", "sizeof", "state", "static",
        "stock", "switch", "tagof", "while", "defined", "elseif",
        "endif", "ifdef", "ifndef", "include", "tryinclude", "pragma",
        "emit", "exit", "sleep", "main", "true", "false"
    )

    private val builtinFunctions = setOf(
        "printf", "format", "print", "SendClientMessage",
        "SetTimer", "KillTimer", "SetTimerEx",
        "GetTickCount", "random", "strcmp", "strfind",
        "strlen", "strmid", "strcat", "strval",
        "valstr", "strins", "strdel",
        "CreateObject", "DestroyObject",
        "CreatePlayerObject", "DestroyPlayerObject",
        "SetPlayerPos", "GetPlayerPos",
        "SetPlayerHealth", "GetPlayerHealth",
        "SetPlayerArmour", "GetPlayerArmour",
        "SetPlayerScore", "GetPlayerScore",
        "GivePlayerMoney", "GetPlayerMoney",
        "GetPlayerName", "SetPlayerName",
        "SendPlayerMessageToAll", "SendClientMessageToAll",
        "SetWorldTime", "SetWeather",
        "TogglePlayerControllable", "SpawnPlayer",
        "SetTimerEx", "CallRemoteFunction",
        "CallLocalFunction"
    )

    fun highlight(text: String) = buildAnnotatedString {
        val len = text.length
        var i = 0

        while (i < len) {
            val ch = text[i]

            when {
                ch == '/' && i + 1 < len && text[i + 1] == '/' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    withStyle(SpanStyle(color = GSColors.SyntaxComment)) {
                        append(text.substring(i, end))
                    }
                    i = end
                }

                ch == '/' && i + 1 < len && text[i + 1] == '*' -> {
                    val end = text.indexOf("*/", i + 2).let { if (it == -1) len - 2 else it + 2 }
                    withStyle(SpanStyle(color = GSColors.SyntaxComment)) {
                        append(text.substring(i, end + 2))
                    }
                    i = end + 2
                }

                ch == '"' || ch == '\'' -> {
                    val quote = ch
                    append(ch)
                    i++
                    while (i < len && text[i] != quote) {
                        if (text[i] == '\\' && i + 1 < len) {
                            append(text[i])
                            i++
                            append(text[i])
                            i++
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                    if (i < len) {
                        withStyle(SpanStyle(color = GSColors.SyntaxString)) {
                            append(text[i])
                        }
                        i++
                    }
                }

                ch == '#' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    withStyle(SpanStyle(color = GSColors.SyntaxPreproc)) {
                        append(text.substring(i, end))
                    }
                    i = end
                }

                ch == '{' || ch == '}' || ch == '(' || ch == ')' ||
                ch == '[' || ch == ']' || ch == ';' || ch == ',' -> {
                    withStyle(SpanStyle(color = GSColors.SyntaxOperator)) {
                        append(ch)
                    }
                    i++
                }

                ch.isDigit() || (ch == '-' && i + 1 < len && text[i + 1].isDigit()) -> {
                    val start = i
                    if (ch == '-') { append(ch); i++ }
                    while (i < len && (text[i].isDigit() || text[i] == '.' ||
                        text[i] == 'x' || text[i] == 'X')) {
                        i++
                    }
                    withStyle(SpanStyle(color = GSColors.SyntaxNumber)) {
                        append(text.substring(start, i))
                    }
                }

                ch.isLetter() || ch == '_' || (ch == '@') -> {
                    val start = i
                    while (i < len && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '@')) {
                        i++
                    }
                    val word = text.substring(start, i)

                    when {
                        word in keywords -> withStyle(
                            SpanStyle(color = GSColors.SyntaxKeyword, fontWeight = FontWeight.Bold)
                        ) { append(word) }

                        word in builtinFunctions -> withStyle(
                            SpanStyle(color = GSColors.SyntaxFunction)
                        ) { append(word) }

                        word[0].isUpperCase() -> withStyle(
                            SpanStyle(color = GSColors.SyntaxKeyword)
                        ) { append(word) }

                        else -> withStyle(
                            SpanStyle(color = GSColors.SyntaxDefault)
                        ) { append(word) }
                    }
                }

                else -> {
                    append(ch)
                    i++
                }
            }
        }
    }
}
