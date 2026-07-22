package com.mehdigm.compiler.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

object FileManager {

    private const val BUFFER_SIZE = 8192

    fun hasManageStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun requestManageStorageIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:com.mehdigm.compiler")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun findPwnFiles(directory: File): List<File> {
        val result = mutableListOf<File>()
        if (!directory.exists() || !directory.isDirectory) return result
        val files = directory.listFiles() ?: return result
        for (file in files) {
            if (file.isDirectory) {
                if (file.name != "." && file.name != "..") {
                    result.addAll(findPwnFiles(file))
                }
            } else if (file.name.endsWith(".pwn", ignoreCase = true)) {
                result.add(file)
            }
        }
        return result
    }

    fun readFileContent(file: File): String {
        return file.readText(Charsets.UTF_8)
    }

    fun writeFileContent(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }

    fun readFromUri(context: Context, uri: Uri): String? {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            return pfd.use { fd ->
                FileInputStream(fd.fileDescriptor).use { input ->
                    val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), BUFFER_SIZE)
                    val sb = StringBuilder()
                    val buffer = CharArray(BUFFER_SIZE)
                    var read: Int
                    while (reader.read(buffer).also { read = it } != -1) {
                        sb.append(buffer, 0, read)
                    }
                    sb.toString()
                }
            }
        } catch (e: Exception) {
            return null
        }
    }

    fun writeToUri(context: Context, uri: Uri, content: String) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "w")
            ?: throw IllegalStateException("Cannot open URI for writing: $uri")
        pfd.use { fd ->
            FileOutputStream(fd.fileDescriptor).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex) ?: "untitled.pwn"
                }
            }
        } catch (_: Exception) { }
        return uri.lastPathSegment ?: "untitled.pwn"
    }

    fun getSuggestedOutputFile(inputFile: File): File {
        val parent = inputFile.parentFile ?: File(".")
        val name = inputFile.nameWithoutExtension
        return File(parent, "$name.amx")
    }
}
