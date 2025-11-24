package com.researchai.domain.rag

interface TextChunker {
    fun chunk(text: String): List<String>
}
