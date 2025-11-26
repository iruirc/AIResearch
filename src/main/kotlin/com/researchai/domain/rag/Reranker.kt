package com.researchai.domain.rag

import com.researchai.domain.models.RerankerConfig
import com.researchai.domain.models.RerankerResult
import com.researchai.domain.models.SearchResult

/**
 * Interface for second-stage reranking/filtering of search results
 */
interface Reranker {
    /**
     * Rerank/filter search results based on query and configuration
     *
     * @param query Original search query
     * @param results First-stage search results
     * @param config Reranker configuration
     * @return Reranked results with statistics
     */
    suspend fun rerank(
        query: String,
        results: List<SearchResult>,
        config: RerankerConfig
    ): RerankerResult
}
