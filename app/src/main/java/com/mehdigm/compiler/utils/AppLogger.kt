package com.mehdigm.compiler.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val LOG_FILE_NAME = "logcat.log"
    private const val MAX_LOG_SIZE = 2L * 1024 * 1024
    private const val TAG = "GSCompiler"

    private var logFile: File? = null
    private var logJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val channel = Channel<LogEntry>(Channel.UNLIMITED)
    private var enabled = false

    private data class LogEntry(
        val level: Char,
        val tag: String,
        val msg: String,
        val time: Long = System.currentTimeMillis()
    )

    fun start(context: Context) {
        if (enabled) return
        logFile = getLogFile(context)
        logFile?.parentFile?.mkdirs()
        enabled = true
        logJob = scope.launch {
            for (entry in channel) {
                writeEntry(entry)
            }
        }
        i(TAG, "AppLogger started")
    }

    fun stop() {
        if (!enabled) return
        enabled = false
        logJob?.cancel()
        logJob = null
        i(TAG, "AppLogger stopped")
    }

    private fun getLogFile(context: Context): File {
        val base = File(
            Environment.getExternalStorageDirectory(),
            "AndroidCSProjects"
        )
        if (base.exists() || base.mkdirs()) {
            return File(base, LOG_FILE_NAME)
        }
        return File(context.filesDir, LOG_FILE_NAME)
    }

    private fun writeEntry(entry: LogEntry) {
        val file = logFile ?: return
        val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            .format(Date(entry.time))
        val line = "$time ${entry.level}/${entry.tag}: ${entry.msg}\n"
        try {
            file.appendText(line)
            trimIfNeeded(file)
        } catch (_: Exception) {}
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_LOG_SIZE) return
        try {
            val content = file.readText()
            file.writeText(content.takeLast((MAX_LOG_SIZE / 2).toInt()))
        } catch (_: Exception) {}
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        if (enabled) channel.trySend(LogEntry('D', tag, msg))
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        if (enabled) channel.trySend(LogEntry('I', tag, msg))
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        if (enabled) channel.trySend(LogEntry('W', tag, msg))
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        if (enabled) channel.trySend(LogEntry('E', tag, msg))
    }

    fun getLogPath(context: Context): File = getLogFile(context)

    fun getLogContent(context: Context): String {
        return try {
            getLogFile(context).readText()
        } catch (_: Exception) { "No logs available" }
    }

    fun clearLogs(context: Context) {
        try {
            getLogFile(context).writeText("")
        } catch (_: Exception) {}
    }
}
