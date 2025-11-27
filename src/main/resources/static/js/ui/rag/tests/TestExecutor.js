/**
 * @fileoverview RAG Test Executor
 * Handles test execution with SSE and UI updates
 * @module ui/rag/tests/TestExecutor
 */

import { ragTestApi } from '../../../api/ragTestApi.js';
import { modalsUI } from '../../modalsUI.js';
import { escapeHtml, formatTime } from '../utils/RagUtils.js';
import { ChunksRenderer } from '../shared/ChunksRenderer.js';
import { Exporter } from '../shared/Exporter.js';

/**
 * Test Executor class for executing RAG tests
 */
export class TestExecutor {
    /**
     * Create a TestExecutor
     * @param {Object} options - Configuration options
     * @param {Function} options.onExecutionComplete - Callback when execution completes
     * @param {Function} options.onReturnToMain - Callback to return to main modal
     */
    constructor(options = {}) {
        this.onExecutionComplete = options.onExecutionComplete || null;
        this.onReturnToMain = options.onReturnToMain || null;

        this.currentExecution = null;
        this.executionResults = null;
        this.executionStartTime = null;
        this.executionTimer = null;

        this.chunksRenderer = new ChunksRenderer();
        this.exporter = new Exporter();
    }

    /**
     * Handle test execution
     * @param {Object} test - Test object to execute
     */
    async handleExecuteTest(test) {
        console.log(`Starting test execution: ${test.name}`);

        // Close RAG modal and open execution modal
        modalsUI.closeModal('ragModal');
        this.openExecutionModal(test);

        // Reset state
        this.executionResults = null;
        this.executionStartTime = Date.now();

        // Start timer
        this.startExecutionTimer();

        // Set up execution event handlers
        this.currentExecution = ragTestApi.executeTest(test.id, {
            onStarted: (data) => {
                console.log('Execution started:', data);
                this.updateExecutionStatus('⏳', 'Выполнение...');
            },
            onProcessing: (data) => {
                console.log('Processing query:', data);
                this.updateExecutionProgress(data.current, data.total, data.query);
            },
            onChunksReady: (data) => {
                console.log('Chunks ready:', data);
                this.showCurrentQueryChunks(data);
            },
            onCompleted: (data) => {
                console.log('Query completed:', data);
                this.updateExecutionProgress(data.current, data.total, data.result.query);
                this.hideCurrentQueryChunks();
                this.showPreviousRequestBlock(data.result);
            },
            onError: (data) => {
                console.error('Query error:', data);
                this.updateExecutionStatus('⚠️', `Ошибка: ${data.message || data.error}`);
            },
            onFinished: (data) => {
                console.log('Execution finished:', data);
                this.handleExecutionComplete(data.result);
            },
            onCancelled: (data) => {
                console.log('Execution cancelled:', data);
                this.handleExecutionCancel(data.result);
            }
        });

        this.setupExecutionButtons();
    }

    /**
     * Setup execution modal buttons
     */
    setupExecutionButtons() {
        // Setup cancel button
        const cancelButton = document.getElementById('ragExecutionCancelButton');
        if (cancelButton) {
            cancelButton.onclick = async () => {
                if (this.currentExecution && !this.currentExecution.isComplete) {
                    if (confirm('Вы уверены, что хотите отменить выполнение теста?')) {
                        await this.currentExecution.cancel();
                        this.handleExecutionCancel(null);
                    }
                }
            };
        }

        // Setup close button
        const closeButton = document.getElementById('closeRagExecutionModal');
        if (closeButton) {
            closeButton.onclick = async () => {
                if (this.currentExecution && !this.currentExecution.isComplete) {
                    if (confirm('Выполнение теста ещё не завершено. Вы уверены, что хотите закрыть окно и отменить выполнение?')) {
                        await this.currentExecution.cancel();
                        this.stopExecutionTimer();
                        modalsUI.closeModal('ragTestExecutionModal');
                        if (this.onReturnToMain) {
                            this.onReturnToMain();
                        }
                    }
                } else {
                    this.stopExecutionTimer();
                    modalsUI.closeModal('ragTestExecutionModal');
                    if (this.onReturnToMain) {
                        this.onReturnToMain();
                    }
                }
            };
        }
    }

    /**
     * Open the execution modal
     * @param {Object} test - Test object
     */
    openExecutionModal(test) {
        modalsUI.openModal('ragTestExecutionModal');

        // Set test name
        const testNameEl = document.getElementById('ragExecutionTestName');
        if (testNameEl) {
            testNameEl.textContent = test.name;
        }

        // Reset progress
        this.updateExecutionProgress(0, test.queries.length, '-');

        // Reset timer
        const timerEl = document.getElementById('ragExecutionTime');
        if (timerEl) {
            timerEl.textContent = '00:00';
        }

        // Reset status
        this.updateExecutionStatus('⏳', 'Подключение...');

        // Hide download button, show cancel button
        const downloadButton = document.getElementById('ragExecutionDownloadButton');
        const cancelButton = document.getElementById('ragExecutionCancelButton');

        if (downloadButton) downloadButton.classList.add('hidden');
        if (cancelButton) cancelButton.classList.remove('hidden');

        // Hide previous request block, current chunks, and reset modal width
        this.hidePreviousRequestBlock();
        this.hideCurrentQueryChunks();
        const modalContent = document.querySelector('.rag-execution-modal-content');
        if (modalContent) {
            modalContent.classList.remove('expanded');
        }

        // Setup toggle handler for previous request block header
        const prevHeader = document.getElementById('ragExecutionPreviousHeader');
        if (prevHeader) {
            prevHeader.onclick = () => this.togglePreviousRequestBlock();
        }
    }

    /**
     * Update execution progress display
     * @param {number} current - Current query index (1-based)
     * @param {number} total - Total number of queries
     * @param {string} query - Current query text
     */
    updateExecutionProgress(current, total, query) {
        const progressFill = document.getElementById('ragExecutionProgressFill');
        const progressText = document.getElementById('ragExecutionProgressText');
        const currentQuery = document.getElementById('ragExecutionCurrentQuery');

        const percentage = total > 0 ? (current / total) * 100 : 0;

        if (progressFill) {
            progressFill.style.width = `${percentage}%`;
        }

        if (progressText) {
            progressText.textContent = `${current} / ${total}`;
        }

        if (currentQuery) {
            currentQuery.textContent = query || '-';
        }
    }

    /**
     * Update execution status display
     * @param {string} icon - Status icon emoji
     * @param {string} text - Status text
     */
    updateExecutionStatus(icon, text) {
        const statusEl = document.getElementById('ragExecutionStatus');
        if (statusEl) {
            statusEl.innerHTML = `
                <span class="rag-execution-status-icon">${icon}</span>
                <span class="rag-execution-status-text">${text}</span>
            `;
        }
    }

    /**
     * Handle execution completion
     * @param {Object} result - Execution result object
     */
    handleExecutionComplete(result) {
        console.log('handleExecutionComplete called with:', result);

        this.stopExecutionTimer();
        this.executionResults = result;

        // Update status
        this.updateExecutionStatus('✅', 'Выполнено!');

        // Update progress to 100%
        const totalResults = result?.results?.length || 0;
        this.updateExecutionProgress(totalResults, totalResults, '-');

        // Show download button, hide cancel button
        const downloadButton = document.getElementById('ragExecutionDownloadButton');
        const cancelButton = document.getElementById('ragExecutionCancelButton');

        if (downloadButton) {
            downloadButton.classList.remove('hidden');
            downloadButton.onclick = () => this.downloadResults();
        }
        if (cancelButton) {
            cancelButton.classList.add('hidden');
        }

        if (this.onExecutionComplete) {
            this.onExecutionComplete(result);
        }
    }

    /**
     * Handle execution cancellation
     * @param {Object} result - Partial execution result
     */
    handleExecutionCancel(result) {
        this.stopExecutionTimer();
        this.executionResults = result;

        // Update status
        this.updateExecutionStatus('⚠️', 'Отменено');

        // Show download button if there are partial results
        const downloadButton = document.getElementById('ragExecutionDownloadButton');
        const cancelButton = document.getElementById('ragExecutionCancelButton');

        if (result && result.results && result.results.length > 0) {
            if (downloadButton) {
                downloadButton.classList.remove('hidden');
                downloadButton.textContent = 'Скачать частичные результаты';
                downloadButton.onclick = () => this.downloadResults();
            }
        }
        if (cancelButton) cancelButton.classList.add('hidden');
    }

    /**
     * Start the execution timer
     */
    startExecutionTimer() {
        this.executionStartTime = Date.now();

        this.executionTimer = setInterval(() => {
            const elapsed = Date.now() - this.executionStartTime;
            const timerEl = document.getElementById('ragExecutionTime');
            if (timerEl) {
                timerEl.textContent = formatTime(elapsed);
            }
        }, 1000);
    }

    /**
     * Stop the execution timer
     */
    stopExecutionTimer() {
        if (this.executionTimer) {
            clearInterval(this.executionTimer);
            this.executionTimer = null;
        }
    }

    // ===== Current Query Chunks Methods =====

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
     * Show current query chunks (before LLM response arrives)
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
            this.chunksRenderer.renderChunksForCurrentQuery(withoutChunksContainer, data.withoutRerankingChunks || []);
        }
        if (withChunksContainer) {
            this.chunksRenderer.renderChunksForCurrentQuery(withChunksContainer, data.withRerankingChunks || []);
        }
    }

    // ===== Previous Request Block Methods =====

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
     * Toggle the previous request block collapse/expand state
     */
    togglePreviousRequestBlock() {
        const block = document.getElementById('ragExecutionPreviousRequest');
        if (block) {
            block.classList.toggle('collapsed');
        }
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
            this.chunksRenderer.renderChunksForPreviousRequest(withoutChunksContainer, result.withoutReranking?.chunks || []);
        }
        if (withChunksContainer) {
            this.chunksRenderer.renderChunksForPreviousRequest(withChunksContainer, result.withReranking?.chunks || []);
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
     * Download execution results
     */
    async downloadResults() {
        if (!this.executionResults) {
            console.error('No results to download');
            return;
        }

        await this.exporter.downloadResults(this.executionResults);
    }
}
