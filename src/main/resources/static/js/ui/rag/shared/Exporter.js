/**
 * @fileoverview RAG Results Exporter
 * Handles exporting test execution results to various formats
 * @module ui/rag/shared/Exporter
 */

/**
 * Results Exporter class for downloading test results
 */
export class Exporter {
    constructor() {
        this.executionResults = null;
    }

    /**
     * Set execution results
     * @param {Object} results - Execution results object
     */
    setResults(results) {
        this.executionResults = results;
    }

    /**
     * Get execution results
     * @returns {Object|null} Execution results
     */
    getResults() {
        return this.executionResults;
    }

    /**
     * Clear execution results
     */
    clearResults() {
        this.executionResults = null;
    }

    /**
     * Format execution results as Markdown
     * Results include both without reranking and with reranking responses
     * @returns {string} Markdown formatted results
     */
    formatResultsAsMarkdown() {
        if (!this.executionResults) {
            return '';
        }

        const result = this.executionResults;
        const lines = [];

        // Header
        lines.push(`# RAG Test Results: ${result.testName}`);
        lines.push('');

        // Metadata
        lines.push('## Metadata');
        lines.push('');
        lines.push(`- **Test ID:** ${result.testId}`);
        lines.push(`- **Session ID:** ${result.sessionId}`);
        lines.push(`- **Provider:** ${result.provider}`);
        lines.push(`- **Model:** ${result.model}`);
        lines.push(`- **Executed At:** ${result.executedAt}`);
        lines.push(`- **Total Time:** ${result.totalTimeMs} ms`);
        lines.push(`- **Status:** ${result.cancelled ? 'Cancelled' : 'Completed'}`);
        lines.push('');

        // Summary statistics (aggregated for both modes)
        let totalTokensNoRerank = 0;
        let totalTokensRerank = 0;
        let totalChunksNoRerank = 0;
        let totalChunksRerank = 0;

        result.results.forEach(r => {
            if (r.withoutReranking) {
                totalTokensNoRerank += r.withoutReranking.tokensUsed || 0;
                totalChunksNoRerank += r.withoutReranking.chunksCount || 0;
            }
            if (r.withReranking) {
                totalTokensRerank += r.withReranking.tokensUsed || 0;
                totalChunksRerank += r.withReranking.chunksCount || 0;
            }
        });

        lines.push('## Summary');
        lines.push('');
        lines.push(`- **Total Queries:** ${result.results.length}`);
        lines.push('');
        lines.push('### Without Reranking');
        lines.push(`- **Total Tokens Used:** ${totalTokensNoRerank}`);
        lines.push(`- **Total Chunks Used:** ${totalChunksNoRerank}`);
        lines.push('');
        lines.push('### With Reranking');
        lines.push(`- **Total Tokens Used:** ${totalTokensRerank}`);
        lines.push(`- **Total Chunks Used:** ${totalChunksRerank}`);
        lines.push('');

        // Query Results
        lines.push('## Query Results');
        lines.push('');

        result.results.forEach((queryResult, index) => {
            lines.push(`### ${index + 1}. ${queryResult.queryId}`);
            lines.push('');
            lines.push('**Query:**');
            lines.push('');
            lines.push(`> ${queryResult.query}`);
            lines.push('');
            lines.push('**Explanation:**');
            lines.push('');
            lines.push(`> ${queryResult.explanation}`);
            lines.push('');

            if (queryResult.model) {
                lines.push(`**Model:** ${queryResult.model}`);
                lines.push('');
            }

            // Response WITHOUT Reranking
            lines.push('#### Response WITHOUT Reranking');
            lines.push('');
            if (queryResult.withoutReranking) {
                const noRerank = queryResult.withoutReranking;
                lines.push(`**Chunks Used:** ${noRerank.chunksCount}`);
                lines.push('');

                // Show chunk details if available
                if (noRerank.chunks && noRerank.chunks.length > 0) {
                    lines.push('<details>');
                    lines.push('<summary>Chunks Details</summary>');
                    lines.push('');
                    noRerank.chunks.forEach((chunk, i) => {
                        lines.push(`${i + 1}. **${chunk.documentName}** (chunk #${chunk.chunkIndex}, score: ${chunk.score.toFixed(3)})`);
                        lines.push(`   > ${chunk.text.substring(0, 200)}${chunk.text.length > 200 ? '...' : ''}`);
                        lines.push('');
                    });
                    lines.push('</details>');
                    lines.push('');
                }

                lines.push('**Response:**');
                lines.push('');
                lines.push('```');
                lines.push(noRerank.response);
                lines.push('```');
                lines.push('');
                lines.push('**Metrics:**');
                lines.push(`- Time: ${noRerank.elapsedTimeMs} ms`);
                if (noRerank.tokensUsed) lines.push(`- Tokens Used: ${noRerank.tokensUsed}`);
                if (noRerank.inputTokens) lines.push(`- Input Tokens: ${noRerank.inputTokens}`);
                if (noRerank.outputTokens) lines.push(`- Output Tokens: ${noRerank.outputTokens}`);
            } else {
                lines.push('*No data available*');
            }
            lines.push('');

            // Response WITH Reranking
            lines.push('#### Response WITH Reranking');
            lines.push('');
            if (queryResult.withReranking) {
                const rerank = queryResult.withReranking;
                lines.push(`**Chunks Used:** ${rerank.chunksCount}`);
                lines.push('');

                // Show chunk details if available
                if (rerank.chunks && rerank.chunks.length > 0) {
                    lines.push('<details>');
                    lines.push('<summary>Chunks Details</summary>');
                    lines.push('');
                    rerank.chunks.forEach((chunk, i) => {
                        lines.push(`${i + 1}. **${chunk.documentName}** (chunk #${chunk.chunkIndex}, score: ${chunk.score.toFixed(3)})`);
                        lines.push(`   > ${chunk.text.substring(0, 200)}${chunk.text.length > 200 ? '...' : ''}`);
                        lines.push('');
                    });
                    lines.push('</details>');
                    lines.push('');
                }

                lines.push('**Response:**');
                lines.push('');
                lines.push('```');
                lines.push(rerank.response);
                lines.push('```');
                lines.push('');
                lines.push('**Metrics:**');
                lines.push(`- Time: ${rerank.elapsedTimeMs} ms`);
                if (rerank.tokensUsed) lines.push(`- Tokens Used: ${rerank.tokensUsed}`);
                if (rerank.inputTokens) lines.push(`- Input Tokens: ${rerank.inputTokens}`);
                if (rerank.outputTokens) lines.push(`- Output Tokens: ${rerank.outputTokens}`);
            } else {
                lines.push('*No data available*');
            }
            lines.push('');
            lines.push('---');
            lines.push('');
        });

        return lines.join('\n');
    }

    /**
     * Format execution results as simplified Markdown (only Query, Explanation, Responses)
     * @returns {string} Simplified Markdown formatted results
     */
    formatResultsAsSimpleMarkdown() {
        if (!this.executionResults) {
            return '';
        }

        const result = this.executionResults;
        const lines = [];

        result.results.forEach((queryResult, index) => {
            if (index > 0) {
                lines.push('---');
                lines.push('');
            }

            // Query
            lines.push('## Query');
            lines.push('');
            lines.push(queryResult.query);
            lines.push('');

            // Explanation
            lines.push('## Explanation');
            lines.push('');
            lines.push(queryResult.explanation);
            lines.push('');

            // Response WITHOUT Reranking
            lines.push('## Response WITHOUT Reranking');
            lines.push('');
            lines.push(queryResult.withoutReranking?.response || '*No data available*');
            lines.push('');

            // Response WITH Reranking
            lines.push('## Response WITH Reranking');
            lines.push('');
            lines.push(queryResult.withReranking?.response || '*No data available*');
            lines.push('');
        });

        return lines.join('\n');
    }

    /**
     * Download a single file
     * @param {string|Blob} content - File content (string or Blob)
     * @param {string} filename - File name
     * @param {string} mimeType - MIME type (ignored if content is Blob)
     */
    downloadFile(content, filename, mimeType) {
        const blob = content instanceof Blob ? content : new Blob([content], { type: mimeType });
        const url = URL.createObjectURL(blob);

        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        console.log(`Downloaded: ${filename}`);
    }

    /**
     * Download execution results as ZIP archive containing JSON and Markdown files
     * @param {Object} [results] - Optional results object. If not provided, uses stored results.
     */
    async downloadResults(results) {
        // Use provided results or fall back to stored results
        if (results) {
            this.executionResults = results;
        }

        if (!this.executionResults) {
            console.error('No results to download');
            return;
        }

        // Use testId (timestamp format: yyyy-MM-dd_HH:mm:ss), replace : with - for filename safety
        const baseFilename = this.executionResults.testId.replace(/:/g, '-');

        // Prepare file contents
        const jsonContent = JSON.stringify(this.executionResults, null, 2);
        const mdContent = this.formatResultsAsMarkdown();
        const mdSimpleContent = this.formatResultsAsSimpleMarkdown();

        // Create ZIP archive using JSZip
        if (typeof JSZip === 'undefined') {
            console.error('JSZip library not loaded, falling back to separate downloads');
            // Fallback to separate downloads
            this.downloadFile(jsonContent, `${baseFilename}.json`, 'application/json');
            setTimeout(() => {
                this.downloadFile(mdContent, `${baseFilename}.md`, 'text/markdown');
            }, 100);
            setTimeout(() => {
                this.downloadFile(mdSimpleContent, `${baseFilename}-comparison.md`, 'text/markdown');
            }, 200);
            return;
        }

        try {
            const zip = new JSZip();

            // Add files to archive with simple names
            zip.file('results.json', jsonContent);
            zip.file('report.md', mdContent);
            zip.file('comparison.md', mdSimpleContent);

            // Generate ZIP and download
            const zipBlob = await zip.generateAsync({ type: 'blob' });
            const zipFilename = `${baseFilename}.zip`;

            this.downloadFile(zipBlob, zipFilename, 'application/zip');
            console.log(`Downloaded results archive: ${zipFilename}`);

        } catch (error) {
            console.error('Error creating ZIP archive:', error);
            // Fallback to separate downloads
            this.downloadFile(jsonContent, `${baseFilename}.json`, 'application/json');
            setTimeout(() => {
                this.downloadFile(mdContent, `${baseFilename}.md`, 'text/markdown');
            }, 100);
            setTimeout(() => {
                this.downloadFile(mdSimpleContent, `${baseFilename}-comparison.md`, 'text/markdown');
            }, 200);
        }
    }
}
