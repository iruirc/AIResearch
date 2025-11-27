/**
 * @fileoverview RAG Document Event Handler
 * Handles event listeners for document operations, file handling, and tab switching
 * @module ui/rag/documents/DocumentEventHandler
 */

import { readFileContent } from '../utils/RagUtils.js';

/**
 * Document Event Handler class
 */
export class DocumentEventHandler {
    /**
     * Create a DocumentEventHandler
     * @param {Object} callbacks - Event callbacks
     * @param {Function} callbacks.onFileAdded - Called when files are added
     * @param {Function} callbacks.onFileRemoved - Called when file is removed
     * @param {Function} callbacks.onTabSwitch - Called when tab is switched
     */
    constructor(callbacks = {}) {
        this.callbacks = callbacks;
        this.selectedFiles = [];
        this.activeTab = 'text';
        this.allowedExtensions = ['.txt', '.md', '.json', '.xml', '.log'];
    }

    /**
     * Setup event handlers (placeholder for initialization)
     */
    setup() {
        // Event handlers are set up per-form via setupDocumentTabs and setupDocumentFileHandlers
    }

    /**
     * Reset state for new form
     */
    reset() {
        this.selectedFiles = [];
        this.activeTab = 'text';
    }

    /**
     * Get the active tab name
     * @returns {string} Active tab name
     */
    getActiveTab() {
        return this.activeTab;
    }

    /**
     * Get the selected files array
     * @returns {File[]} Selected files
     */
    getSelectedFiles() {
        return this.selectedFiles;
    }

    /**
     * Setup tab switching functionality for document form
     */
    setupDocumentTabs() {
        const tabs = document.querySelectorAll('#ragSourceTabs .rag-tab');
        tabs.forEach(tab => {
            tab.onclick = () => {
                const tabName = tab.dataset.tab;
                this.switchTab(tabName);
            };
        });
    }

    /**
     * Switch between tabs
     * @param {string} tabName - Tab name ('text' or 'files')
     */
    switchTab(tabName) {
        this.activeTab = tabName;

        // Update tab buttons
        const tabs = document.querySelectorAll('#ragSourceTabs .rag-tab');
        tabs.forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tab === tabName);
        });

        // Update tab content
        const textContent = document.getElementById('ragTabText');
        const filesContent = document.getElementById('ragTabFiles');

        if (textContent) {
            textContent.classList.toggle('active', tabName === 'text');
        }
        if (filesContent) {
            filesContent.classList.toggle('active', tabName === 'files');
        }

        if (this.callbacks.onTabSwitch) {
            this.callbacks.onTabSwitch(tabName);
        }
    }

    /**
     * Setup file drag&drop and input handlers for document form
     */
    setupDocumentFileHandlers() {
        const dropzone = document.getElementById('ragDropzone');
        const fileInput = document.getElementById('ragFileInput');
        const browseButton = document.getElementById('ragBrowseButton');

        if (browseButton && fileInput) {
            browseButton.onclick = (e) => {
                e.preventDefault();
                e.stopPropagation();
                fileInput.click();
            };
        }

        if (fileInput) {
            fileInput.onchange = (e) => {
                if (e.target.files) {
                    this.addFiles(Array.from(e.target.files));
                    fileInput.value = ''; // Reset input
                }
            };
        }

        if (dropzone) {
            // Prevent default drag behaviors
            ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
                dropzone.addEventListener(eventName, (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                }, false);
            });

            // Highlight drop zone on drag
            ['dragenter', 'dragover'].forEach(eventName => {
                dropzone.addEventListener(eventName, () => {
                    dropzone.classList.add('drag-over');
                }, false);
            });

            ['dragleave', 'drop'].forEach(eventName => {
                dropzone.addEventListener(eventName, () => {
                    dropzone.classList.remove('drag-over');
                }, false);
            });

            // Handle dropped files
            dropzone.addEventListener('drop', (e) => {
                const files = e.dataTransfer?.files;
                if (files) {
                    this.addFiles(Array.from(files));
                }
            }, false);

            // Click on dropzone opens file dialog
            dropzone.onclick = (e) => {
                if (e.target === dropzone || e.target.closest('.rag-dropzone-text, .rag-dropzone-hint, svg')) {
                    fileInput?.click();
                }
            };
        }
    }

    /**
     * Add files to the selected files list
     * @param {File[]} files - Array of File objects
     */
    addFiles(files) {
        files.forEach(file => {
            const ext = '.' + file.name.split('.').pop().toLowerCase();
            if (this.allowedExtensions.includes(ext)) {
                // Check if file already exists
                if (!this.selectedFiles.some(f => f.name === file.name && f.size === file.size)) {
                    this.selectedFiles.push(file);
                }
            } else {
                console.warn(`File ${file.name} has unsupported extension`);
            }
        });

        if (this.callbacks.onFileAdded) {
            this.callbacks.onFileAdded(this.selectedFiles);
        }
    }

    /**
     * Remove file from selected files
     * @param {File} file - File to remove
     */
    removeFile(file) {
        const index = this.selectedFiles.indexOf(file);
        if (index > -1) {
            this.selectedFiles.splice(index, 1);
        }

        if (this.callbacks.onFileRemoved) {
            this.callbacks.onFileRemoved(this.selectedFiles);
        }
    }

    /**
     * Get combined content from all selected files
     * @returns {Promise<string>} Combined file content
     */
    async getCombinedFileContent() {
        if (this.selectedFiles.length === 0) {
            return '';
        }

        const contentParts = [];
        for (const file of this.selectedFiles) {
            const fileContent = await readFileContent(file);
            // Add file header for clarity
            contentParts.push(`--- Файл: ${file.name} ---\n${fileContent}`);
        }

        return contentParts.join('\n\n');
    }
}
