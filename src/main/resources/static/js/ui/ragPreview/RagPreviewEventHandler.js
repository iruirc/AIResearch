/**
 * @fileoverview RAG Preview Event Handler
 * Handles all event listeners for RAG preview modal
 * @module ui/ragPreview/RagPreviewEventHandler
 */

/**
 * RAG Preview Event Handler class
 * Responsible for setting up and managing event listeners
 */
export class RagPreviewEventHandler {
    /**
     * Create event handler
     * @param {Object} callbacks - Callback functions
     * @param {Function} callbacks.onClose - Called when modal should close
     * @param {Function} callbacks.onPreview - Called when preview is requested
     * @param {Function} callbacks.onSendWithout - Called when send without reranking
     * @param {Function} callbacks.onSendWith - Called when send with reranking
     */
    constructor(callbacks) {
        this.callbacks = callbacks;
        this.modal = null;
        this.mouseDownTarget = null;
    }

    /**
     * Initialize event handlers
     * @param {HTMLElement} modal - Modal element
     */
    init(modal) {
        this.modal = modal;

        this.initCloseButton();
        this.initBackdropClick();
        this.initPreviewButton();
        this.initSendButtons();
    }

    /**
     * Initialize close button handler
     */
    initCloseButton() {
        const closeBtn = document.getElementById('closeRagPreviewModal');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                if (this.callbacks.onClose) {
                    this.callbacks.onClose();
                }
            });
        }
    }

    /**
     * Initialize backdrop click handler
     * Tracks mousedown to prevent accidental closes during text selection
     */
    initBackdropClick() {
        if (!this.modal) return;

        this.modal.addEventListener('mousedown', (e) => {
            this.mouseDownTarget = e.target;
        });

        this.modal.addEventListener('click', (e) => {
            // Only close if both mousedown and click happened on the modal backdrop
            if (e.target === this.modal && this.mouseDownTarget === this.modal) {
                if (this.callbacks.onClose) {
                    this.callbacks.onClose();
                }
            }
            this.mouseDownTarget = null;
        });
    }

    /**
     * Initialize preview button handler
     */
    initPreviewButton() {
        const previewBtn = document.getElementById('previewContextButton');
        if (previewBtn) {
            previewBtn.addEventListener('click', () => {
                if (this.callbacks.onPreview) {
                    this.callbacks.onPreview();
                }
            });
        }
    }

    /**
     * Initialize send buttons handlers
     */
    initSendButtons() {
        const sendWithoutBtn = document.getElementById('ragPreviewSendWithout');
        const sendWithBtn = document.getElementById('ragPreviewSendWith');

        if (sendWithoutBtn) {
            sendWithoutBtn.addEventListener('click', () => {
                if (this.callbacks.onSendWithout) {
                    this.callbacks.onSendWithout();
                }
            });
        }

        if (sendWithBtn) {
            sendWithBtn.addEventListener('click', () => {
                if (this.callbacks.onSendWith) {
                    this.callbacks.onSendWith();
                }
            });
        }
    }
}
