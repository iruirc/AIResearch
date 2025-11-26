/**
 * @fileoverview RAG Context Preview Modal
 * Allows users to preview and compare RAG search results with and without reranking
 * @module ui/ragPreviewModal
 */

import { ragApi } from '../api/ragApi.js';

/**
 * RAG Preview Modal manager
 */
class RagPreviewModal {
    constructor() {
        this.modal = null;
        this.currentQuery = '';
        this.previewData = null;
        this.onSendCallback = null;
        this.initialized = false;
    }

    /**
     * Initialize modal elements and event handlers
     * Should be called after DOM is loaded
     */
    init() {
        if (this.initialized) return;

        this.modal = document.getElementById('ragPreviewModal');
        if (!this.modal) {
            console.warn('RAG Preview Modal not found in DOM');
            return;
        }

        this.initialized = true;

        // Close button
        const closeBtn = document.getElementById('closeRagPreviewModal');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.hide());
        }

        // Close on backdrop click
        // Track where mousedown started to prevent accidental closes during text selection
        let mouseDownTarget = null;

        this.modal.addEventListener('mousedown', (e) => {
            mouseDownTarget = e.target;
        });

        this.modal.addEventListener('click', (e) => {
            // Only close if both mousedown and click happened on the modal backdrop
            if (e.target === this.modal && mouseDownTarget === this.modal) {
                this.hide();
            }
            mouseDownTarget = null;
        });

        // Preview button in input area
        const previewBtn = document.getElementById('previewContextButton');
        if (previewBtn) {
            previewBtn.addEventListener('click', () => this.showPreview());
        }

        // Send buttons
        const sendWithoutBtn = document.getElementById('ragPreviewSendWithout');
        const sendWithBtn = document.getElementById('ragPreviewSendWith');

        if (sendWithoutBtn) {
            sendWithoutBtn.addEventListener('click', () => this.sendMessage(false));
        }
        if (sendWithBtn) {
            sendWithBtn.addEventListener('click', () => this.sendMessage(true));
        }
    }

    /**
     * Set callback for sending messages
     * @param {Function} callback - Callback function(query, useReranking)
     */
    setOnSendCallback(callback) {
        this.onSendCallback = callback;
    }

    /**
     * Show preview modal with current input text
     */
    async showPreview() {
        const messageInput = document.getElementById('messageInput');
        if (!messageInput) return;

        const query = messageInput.value.trim();
        if (!query) {
            // Show alert if query is empty
            alert('Введите текст запроса для предпросмотра RAG контекста');
            messageInput.focus();
            return;
        }

        this.currentQuery = query;
        this.show();
        this.showLoading();

        try {
            const data = await ragApi.previewContext(query);
            this.previewData = data;
            this.renderPreview(data);
        } catch (error) {
            console.error('Failed to load preview:', error);
            this.showError(error.message || 'Ошибка загрузки предпросмотра');
        }
    }

    /**
     * Show the modal
     */
    show() {
        if (this.modal) {
            this.modal.classList.add('active');
            document.body.style.overflow = 'hidden';
        }
    }

    /**
     * Hide the modal
     */
    hide() {
        if (this.modal) {
            this.modal.classList.remove('active');
            document.body.style.overflow = '';
        }
    }

    /**
     * Show loading state
     */
    showLoading() {
        const queryEl = document.getElementById('ragPreviewQuery');
        if (queryEl) {
            queryEl.textContent = this.currentQuery;
        }

        const withoutResults = document.getElementById('ragPreviewWithoutResults');
        const withResults = document.getElementById('ragPreviewWithResults');
        const stats = document.getElementById('ragPreviewStats');

        if (withoutResults) {
            withoutResults.innerHTML = '<div class="rag-preview-loading">Загрузка...</div>';
        }
        if (withResults) {
            withResults.innerHTML = '<div class="rag-preview-loading">Загрузка...</div>';
        }
        if (stats) {
            stats.classList.add('hidden');
        }
    }

    /**
     * Show error message
     * @param {string} message - Error message
     */
    showError(message) {
        const withoutResults = document.getElementById('ragPreviewWithoutResults');
        const withResults = document.getElementById('ragPreviewWithResults');

        if (withoutResults) {
            withoutResults.innerHTML = `<div class="rag-preview-error">${message}</div>`;
        }
        if (withResults) {
            withResults.innerHTML = '';
        }
    }

    /**
     * Render preview data
     * @param {Object} data - Preview data from API
     */
    renderPreview(data) {
        // Query
        const queryEl = document.getElementById('ragPreviewQuery');
        if (queryEl) {
            queryEl.textContent = data.query;
        }

        // Without reranking
        const withoutResults = document.getElementById('ragPreviewWithoutResults');
        const withoutCount = document.getElementById('ragPreviewWithoutCount');

        if (withoutResults && data.withoutReranking) {
            withoutResults.innerHTML = this.renderResults(data.withoutReranking, data.filteredResults);
            if (withoutCount) {
                withoutCount.textContent = `${data.withoutReranking.length} чанков`;
            }
        }

        // With reranking
        const withResults = document.getElementById('ragPreviewWithResults');
        const withCount = document.getElementById('ragPreviewWithCount');

        if (withResults) {
            if (data.rerankingEnabled && data.withReranking) {
                withResults.innerHTML = this.renderResults(data.withReranking);
                if (withCount) {
                    withCount.textContent = `${data.withReranking.length} чанков`;
                }
            } else {
                withResults.innerHTML = '<div class="rag-preview-disabled">Реранкинг отключен в настройках</div>';
                if (withCount) {
                    withCount.textContent = '';
                }
            }
        }

        // Statistics
        const statsEl = document.getElementById('ragPreviewStats');
        if (statsEl && data.rerankingEnabled && data.statistics) {
            statsEl.classList.remove('hidden');

            const strategyEl = document.getElementById('ragPreviewStrategy');
            const filteredEl = document.getElementById('ragPreviewFiltered');
            const timeEl = document.getElementById('ragPreviewTime');

            if (strategyEl) {
                strategyEl.textContent = this.getStrategyDisplayName(data.rerankingStrategy);
            }
            if (filteredEl) {
                filteredEl.textContent = `${data.statistics.filteredCount} чанков`;
            }
            if (timeEl) {
                timeEl.textContent = `${data.statistics.processingTimeMs} мс`;
            }
        } else if (statsEl) {
            statsEl.classList.add('hidden');
        }

        // Update button states
        const sendWithBtn = document.getElementById('ragPreviewSendWith');
        if (sendWithBtn) {
            sendWithBtn.disabled = !data.rerankingEnabled;
        }
    }

    /**
     * Render search results as HTML
     * @param {Array} results - Search results
     * @param {Array} filteredResults - Filtered out results (optional)
     * @returns {string} HTML string
     */
    renderResults(results, filteredResults = []) {
        if (!results || results.length === 0) {
            return '<div class="rag-preview-empty">Релевантные результаты не найдены</div>';
        }

        let html = '';

        // Active results
        results.forEach((result, index) => {
            const score = (result.score * 100).toFixed(1);

            html += `
                <div class="rag-preview-result">
                    <div class="rag-preview-result-header">
                        <span class="rag-preview-result-index">${index + 1}</span>
                        <span class="rag-preview-result-doc">${this.escapeHtml(result.documentName)}</span>
                        <span class="rag-preview-result-score">${score}%</span>
                    </div>
                    <div class="rag-preview-result-text">${this.escapeHtml(result.text)}</div>
                </div>
            `;
        });

        // Filtered results (crossed out)
        if (filteredResults && filteredResults.length > 0) {
            html += '<div class="rag-preview-filtered-divider">Отфильтровано:</div>';
            filteredResults.forEach((result) => {
                const score = (result.score * 100).toFixed(1);

                html += `
                    <div class="rag-preview-result filtered">
                        <div class="rag-preview-result-header">
                            <span class="rag-preview-result-doc">${this.escapeHtml(result.documentName)}</span>
                            <span class="rag-preview-result-score">${score}%</span>
                        </div>
                        <div class="rag-preview-result-text">${this.escapeHtml(result.text)}</div>
                    </div>
                `;
            });
        }

        return html;
    }

    /**
     * Get display name for reranking strategy
     * @param {string} strategy - Strategy name
     * @returns {string} Display name
     */
    getStrategyDisplayName(strategy) {
        const names = {
            'SCORE_THRESHOLD': 'Порог релевантности',
            'STATISTICAL': 'Статистический',
            'CROSS_ENCODER': 'Cross-Encoder (LLM)',
            'NONE': 'Без реранкинга'
        };
        return names[strategy] || strategy;
    }

    /**
     * Truncate text to specified length
     * @param {string} text - Text to truncate
     * @param {number} maxLength - Maximum length
     * @returns {string} Truncated text
     */
    truncateText(text, maxLength) {
        if (!text || text.length <= maxLength) return text;
        return text.substring(0, maxLength) + '...';
    }

    /**
     * Escape HTML special characters
     * @param {string} text - Text to escape
     * @returns {string} Escaped text
     */
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Send message with selected reranking option
     * @param {boolean} useReranking - Whether to use reranking
     */
    sendMessage(useReranking) {
        this.hide();

        if (this.onSendCallback) {
            this.onSendCallback(this.currentQuery, useReranking);
        }
    }
}

// Export singleton instance
export const ragPreviewModal = new RagPreviewModal();
