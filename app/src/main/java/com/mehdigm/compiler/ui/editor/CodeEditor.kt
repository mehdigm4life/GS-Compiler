package com.mehdigm.compiler.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 400L
private const val MAX_HIGHLIGHT_SIZE = 500_000

// ── Custom EditText with line-number gutter ────────────────────

class LineNumberEditText(context: Context, attrs: AttributeSet? = null)
    : AppCompatEditText(context, attrs) {

    private val gutterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555555.toInt()
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics
        )
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }

    private val dividerPaint = Paint().apply {
        color = 0x4D555555.toInt()
        strokeWidth = 1f
    }

    private val gutterWidth = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics
    ).toInt()

    init {
        setPadding(gutterWidth, paddingTop, paddingRight, paddingBottom)
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.TOP or Gravity.START
        setTextColor(0xFFA9B7C6.toInt())
        highlightColor = 0x4DD4AF37.toInt()
        setHorizontallyScrolling(true)
        isVerticalScrollBarEnabled = true
    }

    override fun onDraw(canvas: Canvas) {
        val lineH = lineHeight.toFloat()
        val total = lineCount
        val viewH = height.toFloat()
        val scrollY = scrollY.toFloat()
        val base = baseline.toFloat()
        val rightX = gutterWidth - dpToPx(8f)

        val first = (scrollY / lineH).toInt().coerceAtLeast(0)
        val last = ((scrollY + viewH) / lineH + 1).toInt()

        var i = first
        while (i < last.coerceAtMost(total)) {
            canvas.drawText(
                (i + 1).toString(),
                rightX,
                base + i * lineH - scrollY,
                gutterPaint
            )
            i++
        }

        canvas.drawLine(
            (gutterWidth - 1).toFloat(), 0f,
            (gutterWidth - 1).toFloat(), height.toFloat(),
            dividerPaint
        )

        super.onDraw(canvas)
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}

// ── Pawn syntax highlighting (produces SpanSpec list) ──────────

private data class SpanSpec(
    val start: Int,
    val end: Int,
    val color: Int,
    val bold: Boolean = false
)

private val KEYWORDS = setOf(
    "assert", "break", "case", "const", "continue", "default", "do",
    "else", "enum", "for", "forward", "goto", "if", "native", "new",
    "operator", "public", "return", "sizeof", "state", "static",
    "stock", "switch", "tagof", "while", "defined", "elseif",
    "endif", "ifdef", "ifndef", "include", "tryinclude", "pragma",
    "emit", "exit", "sleep", "main", "true", "false"
)

private val BUILTIN_FUNCTIONS = setOf(
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
    "CallRemoteFunction", "CallLocalFunction"
)

private const val C_KEYWORD = 0xFFCC7832.toInt()
private const val C_STRING = 0xFF6A8759.toInt()
private const val C_COMMENT = 0xFF808080.toInt()
private const val C_NUMBER = 0xFF6897BB.toInt()
private const val C_FUNCTION = 0xFFFFC66D.toInt()
private const val C_PREPROC = 0xFF9876AA.toInt()
private const val C_OPERATOR = 0xFFCCCCCC.toInt()

private fun buildHighlightSpans(text: String): List<SpanSpec> {
    val spans = mutableListOf<SpanSpec>()
    val len = text.length
    var i = 0

    while (i < len) {
        val ch = text[i]

        when {
            ch == '/' && i + 1 < len && text[i + 1] == '/' -> {
                val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                spans.add(SpanSpec(i, end, C_COMMENT))
                i = end
            }

            ch == '/' && i + 1 < len && text[i + 1] == '*' -> {
                var end = text.indexOf("*/", i + 2)
                if (end == -1) end = len - 2
                spans.add(SpanSpec(i, end + 2, C_COMMENT))
                i = end + 2
            }

            ch == '"' || ch == '\'' -> {
                val start = i
                val quote = ch
                i++
                while (i < len) {
                    if (text[i] == '\\' && i + 1 < len) {
                        i += 2
                    } else if (text[i] == quote) {
                        i++
                        break
                    } else {
                        i++
                    }
                }
                spans.add(SpanSpec(start, i, C_STRING))
            }

            ch == '#' -> {
                val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                spans.add(SpanSpec(i, end, C_PREPROC))
                i = end
            }

            ch == '{' || ch == '}' || ch == '(' || ch == ')' ||
            ch == '[' || ch == ']' || ch == ';' || ch == ',' -> {
                spans.add(SpanSpec(i, i + 1, C_OPERATOR))
                i++
            }

            ch.isDigit() || (ch == '-' && i + 1 < len && text[i + 1].isDigit()) -> {
                val start = i
                if (ch == '-') i++
                while (i < len && (text[i].isDigit() || text[i] == '.' ||
                    text[i] == 'x' || text[i] == 'X')) {
                    i++
                }
                spans.add(SpanSpec(start, i, C_NUMBER))
            }

            ch.isLetter() || ch == '_' || ch == '@' -> {
                val start = i
                while (i < len && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '@')) {
                    i++
                }
                val word = text.substring(start, i)
                when {
                    word in KEYWORDS -> spans.add(SpanSpec(start, i, C_KEYWORD, bold = true))
                    word in BUILTIN_FUNCTIONS -> spans.add(SpanSpec(start, i, C_FUNCTION))
                    word.isNotEmpty() && word[0].isUpperCase() -> spans.add(SpanSpec(start, i, C_KEYWORD))
                }
            }

            else -> i++
        }
    }

    return spans
}

// ── Compose wrapper ────────────────────────────────────────────

@Composable
fun CodeEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var highlightJob by remember { mutableStateOf<Job?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineNumberEditText(ctx).apply {
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?, start: Int, count: Int, after: Int
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?, start: Int, before: Int, count: Int
                    ) {}

                    override fun afterTextChanged(s: Editable?) {
                        val editable = s ?: return
                        val newText = editable.toString()
                        onTextChange(newText)

                        highlightJob?.cancel()
                        if (newText.length <= MAX_HIGHLIGHT_SIZE) {
                            highlightJob = scope.launch(Dispatchers.Default) {
                                delay(DEBOUNCE_MS)
                                val specs = buildHighlightSpans(newText)
                                launch(Dispatchers.Main) {
                                    removeOldSpans(editable)
                                    for (spec in specs) {
                                        editable.setSpan(
                                            ForegroundColorSpan(spec.color),
                                            spec.start, spec.end,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                        if (spec.bold) {
                                            editable.setSpan(
                                                StyleSpan(Typeface.BOLD),
                                                spec.start, spec.end,
                                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                })
            }
        },
        update = { et ->
            if (!TextUtils.equals(et.text, text)) {
                et.setText(text)
            }
        }
    )
}

private fun removeOldSpans(editable: Editable) {
    for (span in editable.getSpans(0, editable.length, Any::class.java)) {
        if (span is ForegroundColorSpan || span is StyleSpan) {
            editable.removeSpan(span)
        }
    }
}
