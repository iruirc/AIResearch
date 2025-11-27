/**
 * @fileoverview RAG Context Preview Modal
 * Main coordinator class for RAG preview modal functionality
 * @module ui/ragPreview/RagPreviewModal
 */

import { RagPreviewRenderer } from './RagPreviewRenderer.js';
import { RagPreviewDataManager } from './RagPreviewDataManager.js';
import { RagPreviewEventHandler } from './RagPreviewEventHandler.js';

/**
 * RAG Preview Modal coordinator class
 * Orchestrates data loading, rendering, and event handling
 */
class RagPreviewModal {
    constructor() {
        this.modal = null;
        this.onSendCallback = null;
        this.initialized = false;

        // Initialize sub-modules
        this.renderer = new RagPreviewRenderer();
        this.dataManager = new RagPreviewDataManager();
        this.eventHandler = null; // Initialized in init()
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

        // Initialize event handler with callbacks
        this.eventHandler = new RagPreviewEventHandler({
            onClose: () => this.hide(),
            onPreview: () => this.showPreview(),
            onSendWithout: () => this.sendMessage(false),
            onSendWith: () => this.sendMessage(true)
        });

        this.eventHandler.init(this.modal);
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
        const query = this.dataManager.getQueryFromInput();

        if (!query) {
            alert('Введите текст запроса для предпросмотра RAG контекста');
            const messageInput = document.getElementById('messageInput');
            if (messageInput) {
                messageInput.focus();
            }
            return;
        }

        this.dataManager.setCurrentQuery(query);
        this.show();
        this.renderer.showLoading(query);

        try {
            const data = await this.dataManager.loadPreviewData(query);
            this.renderer.renderPreview(data);
        } catch (error) {
            console.error('Failed to load preview:', error);
            this.renderer.showError(error.message || 'Ошибка загрузки предпросмотра');
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
     * Send message with selected reranking option
     * @param {boolean} useReranking - Whether to use reranking
     */
    sendMessage(useReranking) {
        this.hide();

        if (this.onSendCallback) {
            this.onSendCallback(this.dataManager.getCurrentQuery(), useReranking);
        }
    }
}

// Export singleton instance
export const ragPreviewModal = new RagPreviewModal();
