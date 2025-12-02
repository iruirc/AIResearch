package com.researchai.cli.util

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Utility class for Git operations.
 * Provides methods to check if directory is a git repository and get tracked files.
 */
object GitUtils {

    /**
     * Check if the given directory is inside a git repository.
     *
     * @param dir Directory to check
     * @return true if directory is in a git repository, false otherwise
     */
    fun isGitRepository(dir: File): Boolean {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--show-toplevel")
                .directory(dir)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor(5, TimeUnit.SECONDS)
            exitCode && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the root directory of the git repository.
     *
     * @param dir Directory inside git repository
     * @return Git root directory, or null if not in a git repository
     */
    fun getGitRoot(dir: File): File? {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--show-toplevel")
                .directory(dir)
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null
            }

            val output = process.inputStream.bufferedReader().readText().trim()
            if (output.isNotEmpty()) File(output) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get all git-tracked files in the repository.
     *
     * @param gitRoot Root directory of the git repository
     * @return List of git-tracked files (absolute paths)
     */
    fun getTrackedFiles(gitRoot: File): List<File> {
        return try {
            val process = ProcessBuilder("git", "ls-files")
                .directory(gitRoot)
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return emptyList()
            }

            val output = process.inputStream.bufferedReader().readText()
            output.lines()
                .filter { it.isNotBlank() }
                .map { File(gitRoot, it) }
                .filter { it.exists() && it.isFile }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if git command is available on the system.
     *
     * @return true if git is installed and available, false otherwise
     */
    fun isGitInstalled(): Boolean {
        return try {
            val process = ProcessBuilder("git", "--version")
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor(5, TimeUnit.SECONDS)
            exitCode && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
}
