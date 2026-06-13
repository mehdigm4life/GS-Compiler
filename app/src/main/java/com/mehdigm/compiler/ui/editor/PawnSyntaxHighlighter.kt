package com.mehdigm.compiler.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
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

    fun highlight(text: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val len = text.length
        var i = 0

        while (i < len) {
            val ch = text[i]

            when {
                ch == '/' && i + 1 < len && text[i + 1] == '/' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    builder.pushStyle(SpanStyle(color = GSColors.SyntaxComment))
                    builder.append(text.substring(i, end))
                    builder.popStyle()
                    i = end
                }

                ch == '/' && i + 1 < len && text[i + 1] == '*' -> {
                    val end = text.indexOf("*/", i + 2).let { if (it == -1) len - 2 else it + 2 }
                    builder.pushStyle(SpanStyle(color = GSColors.SyntaxComment))
                    builder.append(text.substring(i, end + 2))
                    builder.popStyle()
                    i = end + 2
                }

                ch == '"' || ch == '\'' -> {
                    val quote = ch
                    builder.append(ch)
                    i++
                    while (i < len && text[i] != quote) {
                        if (text[i] == '\\' && i + 1 < len) {
                            builder.append(text[i])
                            i++
                            builder.append(text[i])
                            i++
                        } else {
                            builder.append(text[i])
                            i++
                        }
                    }
                    if (i < len) {
                        builder.pushStyle(SpanStyle(color = GSColors.SyntaxString))
                        builder.append(text[i])
                        builder.popStyle()
                        i++
                    }
                }

                ch == '#' -> {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    builder.pushStyle(SpanStyle(color = GSColors.SyntaxPreproc))
                    builder.append(text.substring(i, end))
                    builder.popStyle()
                    i = end
                }

                ch == '{' || ch == '}' || ch == '(' || ch == ')' ||
                ch == '[' || ch == ']' || ch == ';' || ch == ',' -> {
                    builder.pushStyle(SpanStyle(color = GSColors.SyntaxOperator))
                    builder.append(ch)
                    builder.popStyle()
                    i++
                }

                ch.isDigit() || (ch == '-' && i + 1 < len && text[i + 1].isDigit()) -> {
                    val start = i
                    if (ch == '-') { builder.append(ch); i++ }
                    while (i < len && (text[i].isDigit() || text[i] == '.' ||
                        text[i] == 'x' || text[i] == 'X')) {
                        i++
                    }
                    builder.pushStyle(SpanStyle(color = GSColors.SyntaxNumber))
                    builder.append(text.substring(start, i))
                    builder.popStyle()
                }

                ch.isLetter() || ch == '_' || (ch == '@') -> {
                    val start = i
                    while (i < len && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '@')) {
                        i++
                    }
                    val word = text.substring(start, i)

                    when {
                        word in keywords -> {
                            builder.pushStyle(SpanStyle(color = GSColors.SyntaxKeyword, fontWeight = FontWeight.Bold))
                            builder.append(word)
                            builder.popStyle()
                        }

                        word in builtinFunctions -> {
                            builder.pushStyle(SpanStyle(color = GSColors.SyntaxFunction))
                            builder.append(word)
                            builder.popStyle()
                        }

                        word[0].isUpperCase() -> {
                            builder.pushStyle(SpanStyle(color = GSColors.SyntaxKeyword))
                            builder.append(word)
                            builder.popStyle()
                        }

                        else -> {
                            builder.pushStyle(SpanStyle(color = GSColors.SyntaxDefault))
                            builder.append(word)
                            builder.popStyle()
                        }
                    }
                }

                else -> {
                    builder.append(ch)
                    i++
                }
            }
        }

        return builder.toAnnotatedString()
    }
}
