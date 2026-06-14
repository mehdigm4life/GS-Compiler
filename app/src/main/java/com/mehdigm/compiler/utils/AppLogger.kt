package com.mehdigm.compiler.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.channels.Channel

object AppLogger {

    private const val LOG_FILE_NAME = "logcat.log"
    private const val MAX_LOG_SIZE = 2L * 1024 * 1024
    private const val TAG = "GSCompiler"

    private var logFile: File? = null
    private var logJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val channel = Channel<LogEntry>(Channel.UNLIMITED)
    private var enabled = false
    private var started = false

    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    private data class LogEntry(
        val level: Char,
        val tag: String,
        val msg: String,
        val time: Long = System.currentTimeMillis()
    )

    fun start(context: Context) {
        if (started) return
        started = true
        enabled = true

        logFile = getLogFile(context)

        if (defaultExceptionHandler == null) {
            installCrashHandler()
        }

        logJob = scope.launch {
            var lastEntry: LogEntry? = null
            var repeatCount = 0
            try {
                for (entry in channel) {
                    if (lastEntry != null
                        && entry.level == lastEntry.level
                        && entry.tag == lastEntry.tag
                        && entry.msg == lastEntry.msg
                    ) {
                        repeatCount++
                        continue
                    }
                    if (repeatCount > 0 && lastEntry != null) {
                        val summary = "${lastEntry.msg} (repeated ${repeatCount + 1} times)"
                        writeEntry(lastEntry.copy(msg = summary))
                    }
                    writeEntry(entry)
                    lastEntry = entry
                    repeatCount = 0
                }
            } finally {
                if (repeatCount > 0 && lastEntry != null) {
                    val summary = "${lastEntry.msg} (repeated ${repeatCount + 1} times)"
                    writeEntry(lastEntry.copy(msg = summary))
                }
            }
        }

        scope.launch {
            logSystemInfo(context)
            i(TAG, "AppLogger started")
        }
    }

    fun stop() {
        if (!started) return
        enabled = false
        started = false
        logJob?.cancel()
        logJob = null
    }

    private fun installCrashHandler() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = stackTraceToString(throwable)
            val msg = "UNCAUGHT EXCEPTION on ${thread.name} (${thread.id})\n$stackTrace"
            Log.e("CRASH", msg)
            writeEntryImmediate(LogEntry('C', "CRASH", msg))
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun logSystemInfo(context: Context) {
        i(TAG, "=== System Info ===")
        i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        i(TAG, "Board: ${Build.BOARD}, Hardware: ${Build.HARDWARE}")
        i(TAG, "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        i(TAG, "ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        i(TAG, "Memory class: ${getMemoryClass(context)}MB")
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            i(TAG, "App version: ${pkg.versionName ?: "unknown"} (${pkg.longVersionCode})")
        } catch (_: Exception) {}
        Runtime.getRuntime().let {
            i(TAG, "JVM max memory: ${it.maxMemory() / 1024 / 1024}MB")
            i(TAG, "JVM total memory: ${it.totalMemory() / 1024 / 1024}MB")
            i(TAG, "JVM free memory: ${it.freeMemory() / 1024 / 1024}MB")
        }
        i(TAG, "=== End System Info ===")
    }

    private fun getMemoryClass(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.memoryClass
        } catch (_: Exception) { 0 }
    }

    private fun getLogFile(context: Context): File {
        try {
            val base = File(
                Environment.getExternalStorageDirectory(),
                "AndroidCSProjects"
            )
            if (base.exists() || base.mkdirs()) {
                return File(base, LOG_FILE_NAME)
            }
        } catch (_: Exception) {}
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

    private fun writeEntryImmediate(entry: LogEntry) {
        val file = logFile ?: return
        val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            .format(Date(entry.time))
        val line = "$time ${entry.level}/${entry.tag}: ${entry.msg}\n"
        try {
            file.appendText(line)
        } catch (_: Exception) {}
    }

    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_LOG_SIZE) return
        try {
            val content = file.readText()
            file.writeText(content.takeLast((MAX_LOG_SIZE / 2).toInt()))
        } catch (_: Exception) {}
    }

    private fun stackTraceToString(t: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
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
