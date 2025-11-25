package com.researchai.data.rag

import com.researchai.domain.rag.TextChunker

class FixedSizeTextChunker(
    private val chunkSize: Int = 800,
    private val overlap: Int = 100,
    private val respectSentenceBoundaries: Boolean = true
) : TextChunker {

    override fun chunk(text: String): List<String> {
        if (text.length <= chunkSize) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var position = 0

        while (position < text.length) {
            val endPosition = minOf(position + chunkSize, text.length)
            var chunkEnd = endPosition

            if (respectSentenceBoundaries && endPosition < text.length) {
                chunkEnd = findSentenceBoundary(text, position, endPosition)
            }

            val chunk = text.substring(position, chunkEnd).trim()
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }

            // Ensure position always advances to prevent infinite loops
            val newPosition = chunkEnd - overlap
            position = if (newPosition <= position) chunkEnd else newPosition
            if (position >= text.length) break
        }

        return chunks
    }

    private fun findSentenceBoundary(text: String, start: Int, end: Int): Int {
        val sentenceEnders = listOf('.', '!', '?', '\n')

        for (i in end - 1 downTo start) {
            if (text[i] in sentenceEnders) {
                return i + 1
            }
        }

        return end
    }
}
