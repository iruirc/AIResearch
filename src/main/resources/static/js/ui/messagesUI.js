// Messages UI module - manages message display and interactions
import { scrollToBottom } from '../utils/helpers.js';
import { UI_CONFIG } from '../config.js';

// DOM element references
let messagesContainer = null;
let requestStartTime = null;
let timerInterval = null;

/**
 * Render markdown text to HTML with links opening in new tab
 * @param {string} text - Markdown text
 * @returns {string} HTML string
 */
function renderMarkdown(text) {
    if (typeof marked === 'undefined') {
        // Fallback if marked.js not loaded
        console.warn('marked.js not loaded, falling back to plain text');
        return escapeHtmlForFallback(text);
    }

    // Configure marked to open links in new tab
    const renderer = new marked.Renderer();
    const originalLinkRenderer = renderer.link.bind(renderer);

    renderer.link = (href, title, text) => {
        const html = originalLinkRenderer(href, title, text);
        // Add target="_blank" and rel="noopener noreferrer" for security
        return html.replace('<a ', '<a target="_blank" rel="noopener noreferrer" ');
    };

    // Parse markdown with custom renderer
    return marked.parse(text, { renderer });
}

/**
 * Escape HTML special characters for fallback when marked.js is not available
 * @param {string} text - Plain text
 * @returns {string} Escaped text with line breaks converted to <br>
 */
function escapeHtmlForFallback(text) {
    const div = document.createElement('div');
    div.textContent = text;
    // Convert newlines to <br> for proper display
    return div.innerHTML.replace(/\n/g, '<br>');
}

/**
 * Initialize messages UI with DOM element
 * @param {HTMLElement} container - Messages container element
 */
export function initMessagesUI(container) {
    messagesContainer = container;
}

/**
 * Format timestamp to "DD.MM HH:mm" format
 * @param {number} timestamp - Unix timestamp in milliseconds
 * @returns {string} Formatted time string
 */
function formatMessageTime(timestamp) {
    const date = new Date(timestamp);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${day}.${month} ${hours}:${minutes}`;
}

/**
 * Messages UI module
 */
export const messagesUI = {
    /**
     * Add a message to the chat
     * @param {string} text - Message text
     * @param {string} type - Message type ('user' or 'assistant')
     * @param {Object} metadata - Optional metadata for assistant messages
     * @param {number} timestamp - Optional timestamp in milliseconds
     */
    addMessage(text, type, metadata = null, timestamp = null) {
        console.log('addMessage called with:', { text: text?.substring(0, 100), type, metadata, timestamp });

        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        // Add timestamp if provided
        if (timestamp) {
            const timeDiv = document.createElement('div');
            timeDiv.className = 'message-time';
            timeDiv.textContent = formatMessageTime(timestamp);
            messageDiv.appendChild(timeDiv);
        }

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.innerHTML = renderMarkdown(text);

        // Add metadata for assistant messages
        if (metadata && type === 'assistant') {
            const metadataDiv = this._createMetadataDiv(metadata);
            contentDiv.appendChild(metadataDiv);

            // Add RAG debug info if present
            if (metadata.ragDebugInfo) {
                const debugDiv = this._createRagDebugDiv(metadata.ragDebugInfo);
                contentDiv.appendChild(debugDiv);
            }
        }

        messageDiv.appendChild(contentDiv);
        messagesContainer.appendChild(messageDiv);

        scrollToBottom(messagesContainer);
    },

    /**
     * Add loading indicator message
     * @returns {string} Loading message ID
     */
    addLoadingMessage() {
        const loadingId = `loading-${Date.now()}`;
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message assistant loading';
        messageDiv.id = loadingId;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.innerHTML = `
            <div class="loading-indicator-wrapper">
                <div class="typing-indicator">
                    <span></span><span></span><span></span>
                </div>
                <span class="loading-text">Генерирую ответ... <span class="loading-timer">0.0с</span></span>
            </div>
        `;

        messageDiv.appendChild(contentDiv);
        messagesContainer.appendChild(messageDiv);

        scrollToBottom(messagesContainer);

        // Start timer
        requestStartTime = Date.now();
        this._startTimer(loadingId);

        return loadingId;
    },

    /**
     * Remove loading indicator message
     * @param {string} loadingId - Loading message ID
     */
    removeLoadingMessage(loadingId) {
        console.log('🗑️ removeLoadingMessage called with ID:', loadingId);

        // Stop timer
        if (timerInterval) {
            console.log('⏹️ Clearing timer interval');
            clearInterval(timerInterval);
            timerInterval = null;
        }

        const loadingMessage = document.getElementById(loadingId);
        console.log('📍 Found loading message element:', loadingMessage);
        if (loadingMessage) {
            loadingMessage.remove();
            console.log('✅ Loading message removed from DOM');
        } else {
            console.warn('⚠️ Loading message element not found in DOM');
        }
    },

    /**
     * Show welcome message
     */
    showWelcomeMessage() {
        const welcomeDiv = document.createElement('div');
        welcomeDiv.className = 'welcome-message';
        welcomeDiv.innerHTML = `
            <h2>👋 Добро пожаловать в чат!</h2>
            <p>Напишите ваш вопрос, и я постараюсь помочь.</p>
            <div class="welcome-features">
                <div class="feature">💬 Поддержка разных AI-моделей</div>
                <div class="feature">🔧 Настройка параметров генерации</div>
                <div class="feature">📊 Отслеживание использования токенов</div>
            </div>
        `;
        messagesContainer.appendChild(welcomeDiv);
    },

    /**
     * Clear all messages
     */
    clearMessages() {
        messagesContainer.innerHTML = '';
    },

    /**
     * Render a list of messages
     * @param {Array} messages - Array of message objects with content, role, and metadata
     */
    renderMessages(messages) {
        this.clearMessages();

        if (!messages || messages.length === 0) {
            return;
        }

        messages.forEach(message => {
            // Backend returns 'content' field, not 'text'
            this.addMessage(message.content, message.role, message.metadata, message.timestamp);
        });
    },

    /**
     * Remove welcome message if present
     */
    removeWelcomeMessage() {
        const welcomeMessage = document.querySelector('.welcome-message');
        if (welcomeMessage) {
            welcomeMessage.remove();
        }
    },

    /**
     * Create metadata div for assistant messages
     * @private
     * @param {Object} metadata - Message metadata
     * @returns {HTMLElement} Metadata div element
     */
    _createMetadataDiv(metadata) {
        const metadataDiv = document.createElement('div');
        metadataDiv.className = 'message-metadata';

        // Check for new format with separate tokens
        if (metadata.inputTokens !== undefined && metadata.outputTokens !== undefined) {
            let tokensHtml = '';

            // Line 1: Model and response time
            tokensHtml += `
                <div class="metadata-line">
                    <span class="metadata-model">🤖 ${metadata.model}</span>
                    <span class="metadata-separator">│</span>
                    <span class="metadata-time">⏱ ${metadata.time}с</span>
                </div>
            `;

            // Line 2: API tokens (actual)
            tokensHtml += `
                <div class="metadata-line">
                    <span class="metadata-section-title">API:</span>
                    <span class="metadata-tokens-input">📥 ${metadata.inputTokens}</span>
                    <span class="metadata-separator">│</span>
                    <span class="metadata-tokens-output">📤 ${metadata.outputTokens}</span>
                    <span class="metadata-separator">│</span>
                    <span class="metadata-tokens-total">🎫 ${metadata.totalTokens}</span>
                </div>
            `;

            // Line 3: Local tokens (estimated), if available
            if (metadata.estimatedInputTokens !== undefined && metadata.estimatedInputTokens > 0) {
                tokensHtml += `<div class="metadata-line"><span class="metadata-section-title">Local:</span>`;
                tokensHtml += `<span class="metadata-tokens-estimated">📥 ${metadata.estimatedInputTokens}</span>`;

                if (metadata.estimatedOutputTokens !== undefined && metadata.estimatedOutputTokens > 0) {
                    tokensHtml += `<span class="metadata-separator">│</span>`;
                    tokensHtml += `<span class="metadata-tokens-estimated">📤 ${metadata.estimatedOutputTokens}</span>`;
                }

                if (metadata.estimatedTotalTokens !== undefined && metadata.estimatedTotalTokens > 0) {
                    tokensHtml += `<span class="metadata-separator">│</span>`;
                    tokensHtml += `<span class="metadata-tokens-estimated">🎫 ${metadata.estimatedTotalTokens}</span>`;
                }

                tokensHtml += `</div>`;
            }

            // Line 4: Context window progress
            if (metadata.sessionTotalTokens !== undefined && metadata.contextWindow !== undefined) {
                const percentage = ((metadata.sessionTotalTokens / metadata.contextWindow) * 100).toFixed(1);
                const formattedTotal = metadata.sessionTotalTokens.toLocaleString('ru-RU');
                const formattedWindow = metadata.contextWindow.toLocaleString('ru-RU');

                tokensHtml += `
                    <div class="metadata-line">
                        <span class="metadata-section-title">Context:</span>
                        <span class="metadata-context-progress">📊 ${formattedTotal} / ${formattedWindow} (${percentage}%)</span>
                    </div>
                `;
            }

            metadataDiv.innerHTML = tokensHtml;
        } else {
            // Old format (backward compatibility)
            metadataDiv.innerHTML = `
                <span class="metadata-time">⏱ ${metadata.time}с</span>
                <span class="metadata-separator">│</span>
                <span class="metadata-model">🤖 ${metadata.model}</span>
                <span class="metadata-separator">│</span>
                <span class="metadata-tokens">🎫 ${metadata.tokens} токенов</span>
            `;
        }

        return metadataDiv;
    },

    /**
     * Create RAG debug info div
     * @private
     * @param {Object} debugInfo - RAG debug info from response
     * @returns {HTMLElement} Debug info div element
     */
    _createRagDebugDiv(debugInfo) {
        const debugDiv = document.createElement('div');
        debugDiv.className = 'rag-debug-info';

        // Header (clickable to expand/collapse)
        const header = document.createElement('div');
        header.className = 'rag-debug-header';
        header.innerHTML = `<h5>RAG контекст (${debugInfo.usedResults?.length || 0} чанков)</h5>`;
        header.addEventListener('click', () => {
            debugDiv.classList.toggle('expanded');
        });
        debugDiv.appendChild(header);

        // Content (hidden by default)
        const content = document.createElement('div');
        content.className = 'rag-debug-content';

        // Summary
        const summary = document.createElement('div');
        summary.className = 'rag-debug-summary';

        const reranking = debugInfo.rerankingEnabled ? debugInfo.rerankingStrategy : 'Отключен';
        const filtered = debugInfo.filteredResults?.length || 0;
        const time = debugInfo.processingTimeMs || 0;
        const tokens = debugInfo.estimatedTokens || 0;

        summary.innerHTML = `
            <div class="rag-debug-summary-item">
                <span class="rag-debug-summary-label">Реранкинг:</span>
                <span class="rag-debug-summary-value">${this._getStrategyDisplayName(reranking)}</span>
            </div>
            <div class="rag-debug-summary-item">
                <span class="rag-debug-summary-label">Отфильтровано:</span>
                <span class="rag-debug-summary-value">${filtered}</span>
            </div>
            <div class="rag-debug-summary-item">
                <span class="rag-debug-summary-label">Время:</span>
                <span class="rag-debug-summary-value">${time}мс</span>
            </div>
            <div class="rag-debug-summary-item">
                <span class="rag-debug-summary-label">Токены:</span>
                <span class="rag-debug-summary-value">~${tokens}</span>
            </div>
        `;
        content.appendChild(summary);

        // Chunks
        const chunks = document.createElement('div');
        chunks.className = 'rag-debug-chunks';

        // Used results
        if (debugInfo.usedResults && debugInfo.usedResults.length > 0) {
            debugInfo.usedResults.forEach(result => {
                const chunk = this._createDebugChunk(result, false);
                chunks.appendChild(chunk);
            });
        }

        // Filtered results
        if (debugInfo.filteredResults && debugInfo.filteredResults.length > 0) {
            debugInfo.filteredResults.forEach(result => {
                const chunk = this._createDebugChunk(result, true);
                chunks.appendChild(chunk);
            });
        }

        content.appendChild(chunks);
        debugDiv.appendChild(content);

        return debugDiv;
    },

    /**
     * Create a debug chunk element
     * @private
     */
    _createDebugChunk(result, isFiltered) {
        const chunk = document.createElement('div');
        chunk.className = `rag-debug-chunk${isFiltered ? ' filtered' : ''}`;

        const score = (result.score * 100).toFixed(1);
        const text = result.text?.substring(0, 150) + (result.text?.length > 150 ? '...' : '');

        chunk.innerHTML = `
            <div class="rag-debug-chunk-header">
                <span class="rag-debug-chunk-doc">${this._escapeHtml(result.documentName)}</span>
                <span class="rag-debug-chunk-score">${score}%${isFiltered ? ' ✗' : ' ✓'}</span>
            </div>
            <div class="rag-debug-chunk-text">${this._escapeHtml(text)}</div>
        `;

        return chunk;
    },

    /**
     * Get display name for reranking strategy
     * @private
     */
    _getStrategyDisplayName(strategy) {
        const names = {
            'SCORE_THRESHOLD': 'Порог релевантности',
            'STATISTICAL': 'Статистический',
            'CROSS_ENCODER': 'Cross-Encoder',
            'NONE': 'Нет',
            'Отключен': 'Отключен'
        };
        return names[strategy] || strategy;
    },

    /**
     * Escape HTML special characters
     * @private
     */
    _escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },

    /**
     * Start timer for loading message
     * @private
     * @param {string} loadingId - Loading message ID
     */
    _startTimer(loadingId) {
        // Clear previous interval if exists
        if (timerInterval) {
            clearInterval(timerInterval);
        }

        timerInterval = setInterval(() => {
            const elapsed = ((Date.now() - requestStartTime) / 1000).toFixed(1);
            const timerElement = document.querySelector(`#${loadingId} .loading-timer`);
            if (timerElement) {
                timerElement.textContent = `${elapsed}с`;
            }
        }, UI_CONFIG.TIMER_UPDATE_INTERVAL);
    }
};
