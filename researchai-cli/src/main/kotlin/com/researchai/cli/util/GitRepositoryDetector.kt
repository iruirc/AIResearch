package com.researchai.cli.util

import java.io.File

data class GitRepository(
    val owner: String,
    val repo: String
)

object GitRepositoryDetector {

    /**
     * Detect GitHub repository from .git/config in current or parent directories
     */
    fun detect(startDir: File = File(System.getProperty("user.dir"))): GitRepository? {
        var dir: File? = startDir

        while (dir != null) {
            val gitConfig = File(dir, ".git/config")
            if (gitConfig.exists()) {
                return parseGitConfig(gitConfig)
            }
            dir = dir.parentFile
        }

        return null
    }

    private fun parseGitConfig(configFile: File): GitRepository? {
        val content = configFile.readText()

        // Match HTTPS: https://github.com/owner/repo.git
        // Match SSH: git@github.com:owner/repo.git
        val patterns = listOf(
            """url\s*=\s*https://github\.com/([^/]+)/([^/.\s]+)""".toRegex(),
            """url\s*=\s*git@github\.com:([^/]+)/([^/.\s]+)""".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(content)
            if (match != null) {
                val owner = match.groupValues[1]
                val repo = match.groupValues[2].removeSuffix(".git")
                return GitRepository(owner, repo)
            }
        }

        return null
    }
}
