package com.researchai.cli.strategy

import com.researchai.cli.util.GitUtils
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
 * Documents discovery strategy:
 * - README.md in root
 * - All supported files from Documents/ folder
 */
class DocumentsDiscoveryStrategy : FileDiscoveryStrategy {
    override val name = "documents"

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
 * Git-based discovery strategy:
 * - All git-tracked files in the repository
 * - No filtering by extension (index everything)
 * - Attempts to read all files as text
 * - Skips files that can't be read (binary, permission errors, etc.)
 */
class GitDiscoveryStrategy : FileDiscoveryStrategy {
    override val name = "git"

    override fun discover(rootDir: File): List<DiscoveredFile> {
        // 1. Check if git is installed
        if (!GitUtils.isGitInstalled()) {
            throw IllegalStateException(
                "Git is not installed or not found in PATH. " +
                "Please install git or use --strategy documents"
            )
        }

        // 2. Check if directory is a git repository
        if (!GitUtils.isGitRepository(rootDir)) {
            throw IllegalStateException(
                "Current directory is not a git repository. " +
                "Use --strategy documents or initialize git with: git init"
            )
        }

        // 3. Get git root
        val gitRoot = GitUtils.getGitRoot(rootDir)
            ?: throw IllegalStateException("Failed to get git root directory")

        // 4. Get all tracked files
        val trackedFiles = GitUtils.getTrackedFiles(gitRoot)

        // 5. Convert to DiscoveredFile, filtering out unreadable files
        return trackedFiles.mapNotNull { file ->
            try {
                // Try to read file to check if it's readable
                file.readText()

                // Calculate relative path from rootDir (not gitRoot)
                val relativePath = file.relativeTo(rootDir).path
                DiscoveredFile(file, relativePath)
            } catch (e: Exception) {
                // Skip files that can't be read (binary, permission errors, etc.)
                null
            }
        }
    }
}

/**
 * Auto-detection discovery strategy:
 * - Automatically chooses between git and documents strategy
 * - If directory is a git repository → uses GitDiscoveryStrategy
 * - Otherwise → uses DocumentsDiscoveryStrategy
 */
class AutoDiscoveryStrategy : FileDiscoveryStrategy {
    override val name = "auto"

    override fun discover(rootDir: File): List<DiscoveredFile> {
        // Check if git is installed and directory is a git repository
        val isGitRepo = GitUtils.isGitInstalled() && GitUtils.isGitRepository(rootDir)

        return if (isGitRepo) {
            // Use git strategy
            GitDiscoveryStrategy().discover(rootDir)
        } else {
            // Fall back to documents strategy
            DocumentsDiscoveryStrategy().discover(rootDir)
        }
    }
}

/**
 * Factory for file discovery strategies.
 * Add new strategies here for future extensibility.
 */
object DiscoveryStrategyFactory {
    private val strategies: Map<String, FileDiscoveryStrategy> by lazy {
        mapOf(
            "documents" to DocumentsDiscoveryStrategy(),
            "git" to GitDiscoveryStrategy(),
            "auto" to AutoDiscoveryStrategy()
        )
    }

    fun get(name: String): FileDiscoveryStrategy {
        return strategies[name]
            ?: throw IllegalArgumentException(
                "Unknown discovery strategy: $name. Available: ${available().joinToString(", ")}"
            )
    }

    fun available(): List<String> = strategies.keys.toList()

    // Default strategy is now "auto"
    fun default(): FileDiscoveryStrategy = get("auto")
}
