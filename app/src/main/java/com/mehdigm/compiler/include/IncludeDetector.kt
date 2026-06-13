package com.mehdigm.compiler.include

import java.io.File

object IncludeDetector {

    private val INCLUDE_FOLDER_NAMES = listOf("pawno/include", "include", "includes")

    data class IncludeResult(
        val includePaths: List<String>,
        val rootFolder: File?,
        val detectedFrom: String
    )

    fun detect(sourceFile: File): IncludeResult {
        val foundPaths = mutableListOf<String>()
        var rootFolder: File? = null
        var detectedFrom = "none"

        // Strategy 1: Walk up from source file's directory
        var currentDir = sourceFile.parentFile
        val visited = mutableSetOf<String>()

        while (currentDir != null && currentDir.exists()) {
            val canonicalPath = currentDir.canonicalPath
            if (canonicalPath in visited) break
            visited.add(canonicalPath)

            for (includeName in INCLUDE_FOLDER_NAMES) {
                val includeDir = File(currentDir, includeName)
                if (includeDir.exists() && includeDir.isDirectory) {
                    foundPaths.add(includeDir.absolutePath)
                    if (rootFolder == null) {
                        rootFolder = includeDir
                        detectedFrom = "climbed from ${sourceFile.parent}"
                    }
                }
            }

            // Check for server.cfg or pawn.json which indicates root
            if (File(currentDir, "server.cfg").exists() ||
                File(currentDir, "gamemodes").exists() ||
                File(currentDir, "filterscripts").exists()) {
                // This is likely the server root — check for include dirs here too
                for (includeName in INCLUDE_FOLDER_NAMES) {
                    val includeDir = File(currentDir, includeName)
                    if (includeDir.exists() && includeDir.isDirectory) {
                        val path = includeDir.absolutePath
                        if (path !in foundPaths) {
                            foundPaths.add(path)
                            if (rootFolder == null) {
                                rootFolder = includeDir
                                detectedFrom = "server root ${currentDir.name}"
                            }
                        }
                    }
                }
            }

            currentDir = currentDir.parentFile
        }

        // Strategy 2: Check if source is in gamemodes/filterscripts, check sibling directories
        val parentName = sourceFile.parentFile?.name?.lowercase()
        if (parentName in listOf("gamemodes", "filterscripts")) {
            val serverRoot = sourceFile.parentFile?.parentFile
            if (serverRoot != null) {
                for (includeName in INCLUDE_FOLDER_NAMES) {
                    val includeDir = File(serverRoot, includeName)
                    if (includeDir.exists() && includeDir.isDirectory) {
                        val path = includeDir.absolutePath
                        if (path !in foundPaths) {
                            foundPaths.add(path)
                            if (rootFolder == null) {
                                rootFolder = includeDir
                                detectedFrom = "sibling to ${parentName}"
                            }
                        }
                    }
                }
            }
        }

        // Strategy 3: Check environment variables / well-known locations
        val homeInclude = File(System.getProperty("user.home"), "pawno/include")
        if (homeInclude.exists() && homeInclude.isDirectory) {
            val path = homeInclude.absolutePath
            if (path !in foundPaths) {
                foundPaths.add(path)
                if (rootFolder == null) {
                    rootFolder = homeInclude
                    detectedFrom = "home directory"
                }
            }
        }

        // Strategy 4: Check common Android storage paths
        val externalDirs = listOf(
            "/sdcard/pawno/include",
            "/storage/emulated/0/pawno/include",
            "/sdcard/gamemodes/pawno/include",
        )
        for (dirPath in externalDirs) {
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                val path = dir.absolutePath
                if (path !in foundPaths) {
                    foundPaths.add(path)
                    if (rootFolder == null) {
                        rootFolder = dir
                        detectedFrom = "known location"
                    }
                }
            }
        }

        return IncludeResult(
            includePaths = foundPaths.distinct(),
            rootFolder = rootFolder,
            detectedFrom = detectedFrom
        )
    }

    fun buildCompilerArgs(includePaths: List<String>): List<String> {
        val args = mutableListOf<String>()
        for (path in includePaths) {
            args.add("-i")
            args.add(path)
        }
        return args
    }
}
