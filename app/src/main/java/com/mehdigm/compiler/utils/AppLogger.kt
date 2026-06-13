package com.mehdigm.compiler.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object AppLogger {

    private const val LOG_FILE_NAME = "logcat.log"
    private const val MAX_LOG_SIZE = 2L * 1024 * 1024
    private const val TAG = "GSCompiler"

    private var logFile: File? = null
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var logJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val buffer = StringBuilder()

    private var _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun start(context: Context) {
        if (_isActive.value) return
        logFile = File(context.filesDir, LOG_FILE_NAME)
        scope.launch {
            startLogcat(context)
        }
    }

    fun stop() {
        _isActive.value = false
        logJob?.cancel()
        logJob = null
        flushBuffer()
        try {
            reader?.close()
            process?.destroy()
        } catch (_: Exception) {}
        reader = null
        process = null
        Log.i(TAG, "AppLogger stopped")
    }

    private suspend fun startLogcat(context: Context) {
        try {
            val pid = android.os.Process.myPid()
            val pb = ProcessBuilder(
                "logcat",
                "--pid", pid.toString(),
                "-v", "brief"
            )
            pb.redirectErrorStream(true)
            process = pb.start()
            reader = process?.inputStream?.bufferedReader()

            _isActive.value = true
            Log.i(TAG, "AppLogger started (pid=$pid)")

            logJob = scope.launch {
                var line: String?
                while (isActive) {
                    line = try {
                        reader?.readLine()
                    } catch (_: Exception) { null }
                    if (line != null) {
                        synchronized(buffer) {
                            buffer.append(line).append('\n')
                            if (buffer.length > 32 * 1024) {
                                flushBuffer()
                            }
                        }
                    } else {
                        delay(100)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start logcat", e)
            _isActive.value = false
        }
    }

    private fun flushBuffer() {
        val file = logFile ?: return
        synchronized(buffer) {
            if (buffer.isEmpty()) return
            try {
                file.appendText(buffer.toString())
                buffer.clear()
                trimLogFile(file)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log", e)
            }
        }
    }

    private fun trimLogFile(file: File) {
        if (!file.exists() || file.length() < MAX_LOG_SIZE) return
        try {
            val content = file.readText()
            val trimmed = content.takeLast((MAX_LOG_SIZE / 2).toInt())
            file.writeText(trimmed)
        } catch (_: Exception) {}
    }

    fun getLogPath(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    fun getLogContent(context: Context): String {
        return try {
            File(context.filesDir, LOG_FILE_NAME).readText()
        } catch (_: Exception) { "No logs available" }
    }

    fun clearLogs(context: Context) {
        try {
            File(context.filesDir, LOG_FILE_NAME).writeText("")
        } catch (_: Exception) {}
    }
}
