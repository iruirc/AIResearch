package com.researchai.cli.strategy

import java.io.File

/**
 * Result of file discovery containing the file and its relative path.
 */
data class DiscoveredFile(
    val file: File,
    val relativePath: String
)

/**
 * Strategy interface for discovering files to index in RAG.
 * Extensible for future strategies.
 */
interface FileDiscoveryStrategy {
    val name: String
    fun discover(rootDir: File): List<DiscoveredFile>
}

/**
 * Default discovery strategy:
 * - README.md in root
 * - All supported files from Documents/ folder
 */
class DefaultDiscoveryStrategy : FileDiscoveryStrategy {
    override val name = "default"

    private val supportedExtensions = listOf("md", "txt", "json", "xml", "log")

    override fun discover(rootDir: File): List<DiscoveredFile> {
        val files = mutableListOf<DiscoveredFile>()

        // 1. README.md in root
        val readme = File(rootDir, "README.md")
        if (readme.exists() && readme.isFile) {
            files.add(DiscoveredFile(readme, "README.md"))
        }

        // 2. Documents folder
        val documentsDir = File(rootDir, "Documents")
        if (documentsDir.exists() && documentsDir.isDirectory) {
            documentsDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootDir).path
                    files.add(DiscoveredFile(file, relativePath))
                }
        }

        return files
    }
}

/**
 * Factory for file discovery strategies.
 * Add new strategies here for future extensibility.
 */
object DiscoveryStrategyFactory {
    private val strategies: Map<String, FileDiscoveryStrategy> by lazy {
        mapOf(
            "default" to DefaultDiscoveryStrategy()
        )
    }

    fun get(name: String): FileDiscoveryStrategy {
        return strategies[name]
            ?: throw IllegalArgumentException("Unknown discovery strategy: $name. Available: ${available()}")
    }

    fun available(): List<String> = strategies.keys.toList()

    fun default(): FileDiscoveryStrategy = get("default")
}
