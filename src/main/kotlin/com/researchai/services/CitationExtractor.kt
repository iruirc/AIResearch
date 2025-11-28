package com.researchai.services

import com.researchai.domain.models.SearchResult

/**
 * Service for extracting and formatting citations in AI responses.
 *
 * This service processes AI responses to:
 * 1. Extract citation references in format (источник N)
 * 2. Format responses with a sources footer
 * 3. Add warnings when citations are missing
 */
class CitationExtractor {

    companion object {
        // Regex to find (источник N) patterns (case-insensitive)
        private val CITATION_PATTERN = Regex("""\(источник\s+(\d+)\)""", RegexOption.IGNORE_CASE)

        // Warning message when RAG context was available but no citations found
        const val NO_CITATIONS_WARNING = "\n\n⚠️ Примечание: Ответ сформирован без явных ссылок на источники из базы знаний."

        // Message when no relevant context found in knowledge base
        const val NO_CONTEXT_MESSAGE = "Информация не найдена в базе знаний."
    }

    /**
     * Extract citation numbers from response text.
     *
     * @param response The AI response text
     * @return List of citation numbers found (e.g., [1, 2, 3]), sorted and deduplicated
     */
    fun extractCitationIds(response: String): List<Int> {
        return CITATION_PATTERN.findAll(response)
            .map { it.groupValues[1].toInt() }
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * Check if response contains any citations.
     *
     * @param response The AI response text
     * @return true if at least one citation pattern is found
     */
    fun hasCitations(response: String): Boolean {
        return CITATION_PATTERN.containsMatchIn(response)
    }

    /**
     * Format response with sources footer.
     *
     * This method:
     * - If RAG context was provided and model cited sources: adds a sources footer
     * - If RAG context was provided but model didn't cite: adds a warning
     * - If no RAG context was provided: returns response as-is
     *
     * @param response Original AI response
     * @param sourceMapping Map of source number to SearchResult
     * @param ragContextWasProvided Whether RAG context was included in the prompt
     * @return Formatted response with sources footer and/or warnings
     */
    fun formatWithSources(
        response: String,
        sourceMapping: Map<Int, SearchResult>,
        ragContextWasProvided: Boolean
    ): String {
        // If no RAG context was provided, return response as-is
        if (!ragContextWasProvided || sourceMapping.isEmpty()) {
            return response
        }

        val citedIds = extractCitationIds(response)

        return buildString {
            append(response)

            if (citedIds.isNotEmpty()) {
                // Add sources footer for cited sources
                appendLine()
                appendLine()
                appendLine("---")
                appendLine("**Источники:**")

                citedIds.forEach { id ->
                    sourceMapping[id]?.let { source ->
                        appendLine("[$id] **${source.documentName}** — релевантность: ${String.format("%.2f", source.score)}")

                        // Show source files with full paths
                        val sourceFilesInfo = getSourceFilesInfo(source)
                        if (sourceFilesInfo.isNotEmpty()) {
                            sourceFilesInfo.forEach { (fileName, fullPath) ->
                                appendLine("    📄 $fileName")
                                appendLine("       `$fullPath`")
                            }
                        }
                    }
                }
            } else {
                // RAG context was provided but model didn't cite any sources
                append(NO_CITATIONS_WARNING)
            }
        }
    }

    /**
     * Create a "no context found" response message.
     *
     * @return Standard message indicating no relevant information was found
     */
    fun createNoContextResponse(): String {
        return NO_CONTEXT_MESSAGE
    }

    /**
     * Data class to hold file name and full path
     */
    private data class SourceFileInfo(val fileName: String, val fullPath: String)

    /**
     * Get source file info from SearchResult.
     * Uses sourceFileName (from chunk metadata) and finds matching full path.
     *
     * @param source The search result
     * @return SourceFileInfo or null if no source file
     */
    private fun getSourceFilesInfo(source: SearchResult): List<SourceFileInfo> {
        val fileName = source.sourceFileName ?: return emptyList()

        // Find matching full path in sourceFilePaths
        val matchingPath = source.sourceFilePaths.find { path ->
            extractFileNameFromPath(path) == fileName
        }

        val fullPath = matchingPath ?: "data/rag/source_files/$fileName"
        return listOf(SourceFileInfo(fileName, fullPath))
    }

    /**
     * Extract file name from full path, removing UUID prefix if present.
     */
    private fun extractFileNameFromPath(path: String): String {
        val fileName = path.substringAfterLast("/").substringAfterLast("\\")

        // Check if file name has UUID prefix (format: {uuid}_{originalName})
        val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}_")
        val match = uuidPattern.find(fileName)

        return if (match != null) {
            // Remove UUID prefix, return original file name
            fileName.substring(match.range.last + 1)
        } else {
            fileName
        }
    }
}
