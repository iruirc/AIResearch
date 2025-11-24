package com.researchai.data.rag

import com.researchai.domain.rag.TextChunker

class RecursiveTextChunker(
    private val maxChunkSize: Int = 1000,
    private val minChunkSize: Int = 100
) : TextChunker {

    private val separators = listOf("\n\n", "\n", ". ", "! ", "? ", " ", "")

    override fun chunk(text: String): List<String> {
        return recursiveSplit(text, 0)
    }

    private fun recursiveSplit(text: String, separatorIndex: Int): List<String> {
        if (text.length <= maxChunkSize) {
            return if (text.trim().isNotEmpty()) listOf(text.trim()) else emptyList()
        }

        if (separatorIndex >= separators.size) {
            return splitBySize(text)
        }

        val separator = separators[separatorIndex]
        val parts = text.split(separator)

        val chunks = mutableListOf<String>()
        var currentChunk = ""

        for (part in parts) {
            val testChunk = if (currentChunk.isEmpty()) {
                part
            } else {
                currentChunk + separator + part
            }

            if (testChunk.length <= maxChunkSize) {
                currentChunk = testChunk
            } else {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.trim())
                }

                if (part.length > maxChunkSize) {
                    chunks.addAll(recursiveSplit(part, separatorIndex + 1))
                    currentChunk = ""
                } else {
                    currentChunk = part
                }
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.trim())
        }

        return chunks.filter { it.length >= minChunkSize }
    }

    private fun splitBySize(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var position = 0

        while (position < text.length) {
            val end = minOf(position + maxChunkSize, text.length)
            chunks.add(text.substring(position, end).trim())
            position = end
        }

        return chunks
    }
}
