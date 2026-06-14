package com.mehdigm.compiler.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val LOG_FILE_NAME = "logcat.log"
    private const val MAX_LOG_SIZE = 2L * 1024 * 1024
    private const val TAG = "GSCompiler"
    private const val FREEZE_THRESHOLD_MS = 8000L

    private var logFile: File? = null
    private var logJob: Job? = null
    private var freezeDetectorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val channel = Channel<LogEntry>(Channel.UNLIMITED)
    private var enabled = false

    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

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

        installCrashHandler()
        startFreezeDetector()

        logJob = scope.launch {
            for (entry in channel) {
                writeEntry(entry)
            }
        }

        logSystemInfo(context)
        i(TAG, "AppLogger started")
    }

    fun stop() {
        if (!enabled) return
        enabled = false
        logJob?.cancel()
        logJob = null
        freezeDetectorJob?.cancel()
        freezeDetectorJob = null
        i(TAG, "AppLogger stopped")
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

    private fun startFreezeDetector() {
        freezeDetectorJob = scope.launch {
            val mainHandler = Handler(Looper.getMainLooper())
            while (enabled) {
                var responded = false
                mainHandler.post { responded = true }
                delay(FREEZE_THRESHOLD_MS)
                if (!responded && enabled) {
                    w(TAG, "!!! FREEZE DETECTED: main thread unresponsive for ${FREEZE_THRESHOLD_MS}ms")
                    logThreadDump()
                }
            }
        }
    }

    private fun logThreadDump() {
        w(TAG, "--- Thread dump ---")
        for ((thread, stack) in Thread.getAllStackTraces()) {
            w(TAG, "Thread: ${thread.name} (id=${thread.id}, state=${thread.state})")
            for (el in stack) {
                w(TAG, "  at ${el.className}.${el.methodName}(${el.fileName}:${el.lineNumber})")
            }
        }
        w(TAG, "--- End thread dump ---")
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
