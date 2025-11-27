/**
 * @fileoverview RAG Chunks Renderer
 * Handles rendering of RAG chunks for previous and current queries
 * @module ui/rag/shared/ChunksRenderer
 */

import { escapeHtml } from '../utils/index.js';

/**
 * Chunks Renderer class for displaying RAG chunks
 */
export class ChunksRenderer {
    constructor() {
        // Nothing to initialize
    }

    /**
     * Hide the previous request block
     */
    hidePreviousRequestBlock() {
        const block = document.getElementById('ragExecutionPreviousRequest');
        if (block) {
            block.classList.add('hidden');
            block.classList.remove('collapsed');
        }
    }

    /**
     * Hide the current query chunks section
     */
    hideCurrentQueryChunks() {
        const block = document.getElementById('ragExecutionCurrentChunks');
        if (block) {
            block.classList.add('hidden');
        }
    }

    /**
     * Show current query chunks (before LLM response arrives).
     * Displays chunks immediately with "Waiting for LLM response..." indicator.
     * @param {Object} data - ChunksReady event data with chunks
     */
    showCurrentQueryChunks(data) {
        const block = document.getElementById('ragExecutionCurrentChunks');
        if (!block || !data) return;

        // Show block and expand modal
        block.classList.remove('hidden');
        const modalContent = document.querySelector('.rag-execution-modal-content');
        if (modalContent) {
            modalContent.classList.add('expanded');
        }

        // Populate chunks count
        const withoutCount = document.getElementById('ragExecutionCurrentWithoutCount');
        const withCount = document.getElementById('ragExecutionCurrentWithCount');

        const withoutChunksCount = data.withoutRerankingChunks?.length || 0;
        const withChunksCount = data.withRerankingChunks?.length || 0;

        if (withoutCount) {
            withoutCount.textContent = `${withoutChunksCount} чанков`;
        }
        if (withCount) {
            withCount.textContent = `${withChunksCount} чанков`;
        }

        // Populate chunks lists
        const withoutChunksContainer = document.getElementById('ragExecutionCurrentWithoutChunks');
        const withChunksContainer = document.getElementById('ragExecutionCurrentWithChunks');

        if (withoutChunksContainer) {
            this.renderChunksForCurrentQuery(withoutChunksContainer, data.withoutRerankingChunks || []);
        }
        if (withChunksContainer) {
            this.renderChunksForCurrentQuery(withChunksContainer, data.withRerankingChunks || []);
        }
    }

    /**
     * Render chunks into a container for the current query chunks section
     * @param {HTMLElement} container - Container element for chunks
     * @param {Array} chunks - Array of chunk objects
     */
    renderChunksForCurrentQuery(container, chunks) {
        container.innerHTML = '';

        if (!chunks || chunks.length === 0) {
            container.innerHTML = '<div class="rag-execution-current-chunk-empty">Нет чанков</div>';
            return;
        }

        chunks.forEach((chunk, index) => {
            const chunkEl = document.createElement('div');
            chunkEl.className = 'rag-execution-current-chunk';

            const scorePercent = (chunk.score * 100).toFixed(1);

            chunkEl.innerHTML = `
                <div class="rag-execution-current-chunk-header">
                    <span class="rag-execution-current-chunk-index">#${index + 1}</span>
                    <span class="rag-execution-current-chunk-score">${scorePercent}%</span>
                </div>
                <div class="rag-execution-current-chunk-text">${escapeHtml(chunk.text)}</div>
            `;

            container.appendChild(chunkEl);
        });
    }

    /**
     * Show and populate the previous request block with query result data
     * @param {Object} result - QueryExecutionResult object
     */
    showPreviousRequestBlock(result) {
        const block = document.getElementById('ragExecutionPreviousRequest');
        if (!block || !result) return;

        // Show block and expand modal
        block.classList.remove('hidden');
        block.classList.remove('collapsed');
        const modalContent = document.querySelector('.rag-execution-modal-content');
        if (modalContent) {
            modalContent.classList.add('expanded');
        }

        // Populate query text
        const queryText = document.getElementById('ragExecutionPreviousQueryText');
        if (queryText) {
            queryText.textContent = result.query;
        }

        // Populate chunks count
        const withoutCount = document.getElementById('ragExecutionPreviousWithoutCount');
        const withCount = document.getElementById('ragExecutionPreviousWithCount');

        const withoutChunksCount = result.withoutReranking?.chunksCount || 0;
        const withChunksCount = result.withReranking?.chunksCount || 0;

        if (withoutCount) {
            withoutCount.textContent = `${withoutChunksCount} чанков`;
        }
        if (withCount) {
            withCount.textContent = `${withChunksCount} чанков`;
        }

        // Populate chunks lists
        const withoutChunksContainer = document.getElementById('ragExecutionPreviousWithoutChunks');
        const withChunksContainer = document.getElementById('ragExecutionPreviousWithChunks');

        if (withoutChunksContainer) {
            this.renderChunksForPreviousRequest(withoutChunksContainer, result.withoutReranking?.chunks || []);
        }
        if (withChunksContainer) {
            this.renderChunksForPreviousRequest(withChunksContainer, result.withReranking?.chunks || []);
        }

        // Populate responses
        const withoutResponse = document.getElementById('ragExecutionPreviousWithoutResponse');
        const withResponse = document.getElementById('ragExecutionPreviousWithResponse');

        if (withoutResponse) {
            withoutResponse.textContent = result.withoutReranking?.response || 'Нет данных';
        }
        if (withResponse) {
            withResponse.textContent = result.withReranking?.response || 'Нет данных';
        }

        // Populate metadata
        const metadata = document.getElementById('ragExecutionPreviousMetadata');
        if (metadata) {
            const withoutTime = result.withoutReranking?.elapsedTimeMs || 0;
            const withTime = result.withReranking?.elapsedTimeMs || 0;
            const totalTime = withoutTime + withTime;

            metadata.innerHTML = `
                <div class="rag-execution-previous-metadata-item">
                    <span class="rag-execution-previous-metadata-label">Время без реранкинга:</span>
                    <span class="rag-execution-previous-metadata-value">${withoutTime} мс</span>
                </div>
                <div class="rag-execution-previous-metadata-item">
                    <span class="rag-execution-previous-metadata-label">Время с реранкингом:</span>
                    <span class="rag-execution-previous-metadata-value">${withTime} мс</span>
                </div>
                <div class="rag-execution-previous-metadata-item">
                    <span class="rag-execution-previous-metadata-label">Общее время:</span>
                    <span class="rag-execution-previous-metadata-value">${totalTime} мс</span>
                </div>
            `;
        }
    }

    /**
     * Show previous request block with only chunks (before LLM response arrives).
     * Shows chunks immediately and displays "Waiting for response..." instead of actual responses.
     * @param {Object} data - ChunksReady event data with chunks
     */
    showPreviousRequestBlockWithChunksOnly(data) {
        const block = document.getElementById('ragExecutionPreviousRequest');
        if (!block || !data) return;

        // Show block and expand modal
        block.classList.remove('hidden');
        block.classList.remove('collapsed');
        const modalContent = document.querySelector('.rag-execution-modal-content');
        if (modalContent) {
            modalContent.classList.add('expanded');
        }

        // Populate query text
        const queryText = document.getElementById('ragExecutionPreviousQueryText');
        if (queryText) {
            queryText.textContent = data.query;
        }

        // Populate chunks count
        const withoutCount = document.getElementById('ragExecutionPreviousWithoutCount');
        const withCount = document.getElementById('ragExecutionPreviousWithCount');

        const withoutChunksCount = data.withoutRerankingChunks?.length || 0;
        const withChunksCount = data.withRerankingChunks?.length || 0;

        if (withoutCount) {
            withoutCount.textContent = `${withoutChunksCount} чанков`;
        }
        if (withCount) {
            withCount.textContent = `${withChunksCount} чанков`;
        }

        // Populate chunks lists
        const withoutChunksContainer = document.getElementById('ragExecutionPreviousWithoutChunks');
        const withChunksContainer = document.getElementById('ragExecutionPreviousWithChunks');

        if (withoutChunksContainer) {
            this.renderChunksForPreviousRequest(withoutChunksContainer, data.withoutRerankingChunks || []);
        }
        if (withChunksContainer) {
            this.renderChunksForPreviousRequest(withChunksContainer, data.withRerankingChunks || []);
        }

        // Show "Waiting for response..." instead of actual responses
        const withoutResponse = document.getElementById('ragExecutionPreviousWithoutResponse');
        const withResponse = document.getElementById('ragExecutionPreviousWithResponse');

        const waitingHtml = '<span class="rag-execution-waiting">⏳ Ожидание ответа LLM...</span>';

        if (withoutResponse) {
            withoutResponse.innerHTML = waitingHtml;
        }
        if (withResponse) {
            withResponse.innerHTML = waitingHtml;
        }

        // Clear metadata (will be populated when completed event arrives)
        const metadata = document.getElementById('ragExecutionPreviousMetadata');
        if (metadata) {
            metadata.innerHTML = '';
        }
    }

    /**
     * Toggle the previous request block collapse/expand state
     */
    togglePreviousRequestBlock() {
        const block = document.getElementById('ragExecutionPreviousRequest');
        if (block) {
            block.classList.toggle('collapsed');
        }
    }

    /**
     * Render chunks into a container for the previous request block
     * @param {HTMLElement} container - Container element for chunks
     * @param {Array} chunks - Array of chunk objects
     */
    renderChunksForPreviousRequest(container, chunks) {
        container.innerHTML = '';

        if (!chunks || chunks.length === 0) {
            container.innerHTML = '<div class="rag-execution-previous-chunk-empty">Нет чанков</div>';
            return;
        }

        chunks.forEach((chunk, index) => {
            const chunkEl = document.createElement('div');
            chunkEl.className = 'rag-execution-previous-chunk';

            const scorePercent = (chunk.score * 100).toFixed(1);

            chunkEl.innerHTML = `
                <div class="rag-execution-previous-chunk-header">
                    <span class="rag-execution-previous-chunk-index">${index + 1}</span>
                    <span class="rag-execution-previous-chunk-doc">${chunk.documentName}</span>
                    <span class="rag-execution-previous-chunk-score">${scorePercent}%</span>
                </div>
                <div class="rag-execution-previous-chunk-text">${escapeHtml(chunk.text)}</div>
            `;

            container.appendChild(chunkEl);
        });
    }
}
