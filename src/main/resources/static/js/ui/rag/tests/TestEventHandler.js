/**
 * @fileoverview RAG Test Event Handler
 * Handles event listeners for test form and file operations
 * @module ui/rag/tests/TestEventHandler
 */

import { readFileContent } from '../utils/RagUtils.js';

/**
 * Test Event Handler class
 */
export class TestEventHandler {
    /**
     * Create a TestEventHandler
     * @param {Object} callbacks - Event callbacks
     * @param {Function} callbacks.onTabSwitch - Called when tab is switched
     * @param {Function} callbacks.onFileAdded - Called when file is added
     * @param {Function} callbacks.onFileRemoved - Called when file is removed
     */
    constructor(callbacks = {}) {
        this.callbacks = callbacks;
        this.selectedTestFile = null;
        this.activeTestTab = 'text';
        this.allowedExtensions = ['.txt', '.md', '.json', '.xml', '.log'];
    }

    /**
     * Get the active tab name
     * @returns {string} Active tab name
     */
    getActiveTab() {
        return this.activeTestTab;
    }

    /**
     * Get the selected file
     * @returns {File|null} Selected file
     */
    getSelectedFile() {
        return this.selectedTestFile;
    }

    /**
     * Reset state for new form
     */
    reset() {
        this.selectedTestFile = null;
        this.activeTestTab = 'text';
    }

    /**
     * Setup test tab switching functionality
     */
    setupTestTabs() {
        const tabs = document.querySelectorAll('#ragTestSourceTabs .rag-tab');
        tabs.forEach(tab => {
            tab.onclick = () => {
                const tabName = tab.dataset.tab;
                this.switchTab(tabName);
            };
        });
    }

    /**
     * Switch to a specific tab
     * @param {string} tabName - Tab name ('text' or 'files')
     */
    switchTab(tabName) {
        this.activeTestTab = tabName;
        if (this.callbacks.onTabSwitch) {
            this.callbacks.onTabSwitch(tabName);
        }
    }

    /**
     * Setup test file drag&drop and input handlers
     */
    setupTestFileHandlers() {
        const dropzone = document.getElementById('ragTestDropzone');
        const fileInput = document.getElementById('ragTestFileInput');
        const browseButton = document.getElementById('ragTestBrowseButton');

        if (browseButton && fileInput) {
            browseButton.onclick = (e) => {
                e.preventDefault();
                e.stopPropagation();
                fileInput.click();
            };
        }

        if (fileInput) {
            fileInput.onchange = async (e) => {
                if (e.target.files && e.target.files.length > 0) {
                    await this.addTestFile(e.target.files[0]);
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
            dropzone.addEventListener('drop', async (e) => {
                const files = e.dataTransfer?.files;
                if (files && files.length > 0) {
                    await this.addTestFile(files[0]);
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
     * Add file as test content (only one file allowed)
     * @param {File} file - File object
     */
    async addTestFile(file) {
        const ext = '.' + file.name.split('.').pop().toLowerCase();

        if (!this.allowedExtensions.includes(ext)) {
            alert(`Формат файла не поддерживается.\nПоддерживаемые форматы: ${this.allowedExtensions.join(', ')}`);
            return;
        }

        this.selectedTestFile = file;

        // Also populate the content textarea with file content
        try {
            const content = await readFileContent(file);
            const contentInput = document.getElementById('ragTestContent');
            if (contentInput) {
                contentInput.value = content;
            }
        } catch (error) {
            console.error('Error reading file:', error);
        }

        if (this.callbacks.onFileAdded) {
            this.callbacks.onFileAdded(file);
        }
    }

    /**
     * Remove the selected test file
     */
    removeTestFile() {
        this.selectedTestFile = null;
        if (this.callbacks.onFileRemoved) {
            this.callbacks.onFileRemoved();
        }
    }

    /**
     * Get content from current source (text or file)
     * @returns {Promise<string>} Content string
     */
    async getContent() {
        if (this.activeTestTab === 'text') {
            const contentInput = document.getElementById('ragTestContent');
            return contentInput?.value?.trim() || '';
        } else {
            if (!this.selectedTestFile) {
                return '';
            }
            return await readFileContent(this.selectedTestFile);
        }
    }
}
