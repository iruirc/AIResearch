// Messages UI module - manages message display and interactions
import { scrollToBottom } from '../utils/helpers.js';
import { UI_CONFIG } from '../config.js';

// DOM element references
let messagesContainer = null;
let requestStartTime = null;
let timerInterval = null;

/**
 * Initialize messages UI with DOM element
 * @param {HTMLElement} container - Messages container element
 */
export function initMessagesUI(container) {
    messagesContainer = container;
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
     */
    addMessage(text, type, metadata = null) {
        console.log('addMessage called with:', { text: text?.substring(0, 100), type, metadata });

        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.textContent = text;

        // Add metadata for assistant messages
        if (metadata && type === 'assistant') {
            const metadataDiv = this._createMetadataDiv(metadata);
            contentDiv.appendChild(metadataDiv);
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
            this.addMessage(message.content, message.role, message.metadata);
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
