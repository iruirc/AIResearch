/**
 * @fileoverview RAG Preview Renderer
 * Handles all rendering logic for RAG preview modal
 * @module ui/ragPreview/RagPreviewRenderer
 */

/**
 * RAG Preview Renderer class
 * Responsible for rendering preview data, results, and UI states
 */
export class RagPreviewRenderer {
    /**
     * Render loading state
     * @param {string} query - Current query text
     */
    showLoading(query) {
        const queryEl = document.getElementById('ragPreviewQuery');
        if (queryEl) {
            queryEl.textContent = query;
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
     * Render error message
     * @param {string} message - Error message
     */
    showError(message) {
        const withoutResults = document.getElementById('ragPreviewWithoutResults');
        const withResults = document.getElementById('ragPreviewWithResults');

        if (withoutResults) {
            withoutResults.innerHTML = `<div class="rag-preview-error">${this.escapeHtml(message)}</div>`;
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
        this.renderWithoutReranking(data);

        // With reranking
        this.renderWithReranking(data);

        // Statistics
        this.renderStatistics(data);

        // Update button states
        const sendWithBtn = document.getElementById('ragPreviewSendWith');
        if (sendWithBtn) {
            sendWithBtn.disabled = !data.rerankingEnabled;
        }
    }

    /**
     * Render results without reranking
     * @param {Object} data - Preview data
     */
    renderWithoutReranking(data) {
        const withoutResults = document.getElementById('ragPreviewWithoutResults');
        const withoutCount = document.getElementById('ragPreviewWithoutCount');

        if (withoutResults && data.withoutReranking) {
            withoutResults.innerHTML = this.renderResults(data.withoutReranking, data.filteredResults);
            if (withoutCount) {
                withoutCount.textContent = `${data.withoutReranking.length} чанков`;
            }
        }
    }

    /**
     * Render results with reranking
     * @param {Object} data - Preview data
     */
    renderWithReranking(data) {
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
    }

    /**
     * Render statistics section
     * @param {Object} data - Preview data
     */
    renderStatistics(data) {
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
}
