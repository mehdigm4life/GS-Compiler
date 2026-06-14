package com.mehdigm.compiler.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import com.mehdigm.compiler.utils.AppLogger
import java.io.File
import java.io.FileOutputStream

object FileManager {

    fun hasManageStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readGranted = android.Manifest.permission.READ_EXTERNAL_STORAGE
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
        return file.readText()
    }

    fun writeFileContent(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun saveToDocument(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private const val MAX_FILE_SIZE = 5 * 1024 * 1024

    fun readFromDocument(context: Context, uri: Uri): String? {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) return null
            val bytes = stream.use { it.readBytes() }
            if (bytes.size > MAX_FILE_SIZE) {
                throw SecurityException("File too large (${bytes.size} bytes, max $MAX_FILE_SIZE)")
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("FileManager", "readFromDocument error: ${e.message}")
            null
        }
    }

    fun getSuggestedOutputFile(inputFile: File): File {
        val parent = inputFile.parentFile ?: File(".")
        val name = inputFile.nameWithoutExtension
        return File(parent, "$name.amx")
    }

    fun getCommonPawnDirectories(): List<File> {
        val dirs = mutableListOf<File>()
        val base = Environment.getExternalStorageDirectory()

        val candidates = listOf(
            base,
            File(base, "gamemodes"),
            File(base, "filterscripts"),
            File(base, "pawno"),
            File(base, "pawno/include"),
            File(base, "SA-MP"),
            File(base, "samp"),
        )

        for (dir in candidates) {
            if (dir.exists() && dir.isDirectory) {
                dirs.add(dir)
            }
        }

        return dirs
    }
}
