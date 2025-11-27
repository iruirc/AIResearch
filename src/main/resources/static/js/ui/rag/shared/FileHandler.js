/**
 * @fileoverview RAG File Handler
 * Handles file upload, drag & drop, and file list management
 * @module ui/rag/shared/FileHandler
 */

import { formatFileSize, readFileContent } from '../utils/index.js';

/**
 * File Handler class for managing file uploads
 */
export class FileHandler {
    /**
     * Create a FileHandler
     * @param {Object} options - Configuration options
     * @param {string} options.dropzoneId - ID of dropzone element
     * @param {string} options.fileInputId - ID of file input element
     * @param {string} options.browseButtonId - ID of browse button element
     * @param {string} options.filesListId - ID of files list container
     * @param {string[]} [options.allowedExtensions] - Allowed file extensions
     * @param {boolean} [options.multiple=true] - Allow multiple files
     */
    constructor(options) {
        this.dropzoneId = options.dropzoneId;
        this.fileInputId = options.fileInputId;
        this.browseButtonId = options.browseButtonId;
        this.filesListId = options.filesListId;
        this.allowedExtensions = options.allowedExtensions || ['.txt', '.md', '.json', '.xml', '.log'];
        this.multiple = options.multiple !== false;
        this.selectedFiles = [];
        this.onFilesChanged = null;
    }

    /**
     * Set callback for files changed event
     * @param {Function} callback - Callback function(files)
     */
    setOnFilesChanged(callback) {
        this.onFilesChanged = callback;
    }

    /**
     * Get selected files
     * @returns {File[]} Array of selected files
     */
    getSelectedFiles() {
        return this.selectedFiles;
    }

    /**
     * Clear selected files
     */
    clearFiles() {
        this.selectedFiles = [];
        this.renderFilesList();
    }

    /**
     * Setup file drag & drop and input handlers
     */
    setup() {
        const dropzone = document.getElementById(this.dropzoneId);
        const fileInput = document.getElementById(this.fileInputId);
        const browseButton = document.getElementById(this.browseButtonId);

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
                if (this.multiple) {
                    // Check if file already exists
                    if (!this.selectedFiles.some(f => f.name === file.name && f.size === file.size)) {
                        this.selectedFiles.push(file);
                    }
                } else {
                    // Single file mode - replace existing
                    this.selectedFiles = [file];
                }
            } else {
                console.warn(`File ${file.name} has unsupported extension`);
            }
        });

        this.renderFilesList();

        if (this.onFilesChanged) {
            this.onFilesChanged(this.selectedFiles);
        }
    }

    /**
     * Remove file from selected files
     * @param {number} index - Index of file to remove
     */
    removeFile(index) {
        this.selectedFiles.splice(index, 1);
        this.renderFilesList();

        if (this.onFilesChanged) {
            this.onFilesChanged(this.selectedFiles);
        }
    }

    /**
     * Render the list of selected files
     */
    renderFilesList() {
        const filesList = document.getElementById(this.filesListId);
        if (!filesList) return;

        filesList.innerHTML = '';

        this.selectedFiles.forEach((file, index) => {
            const fileItem = document.createElement('div');
            fileItem.className = 'rag-file-item';

            const fileIcon = document.createElement('div');
            fileIcon.className = 'rag-file-icon';
            fileIcon.innerHTML = `
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                </svg>
            `;

            const fileInfo = document.createElement('div');
            fileInfo.className = 'rag-file-info';

            const fileName = document.createElement('div');
            fileName.className = 'rag-file-name';
            fileName.textContent = file.name;

            const fileSize = document.createElement('div');
            fileSize.className = 'rag-file-size';
            fileSize.textContent = formatFileSize(file.size);

            fileInfo.appendChild(fileName);
            fileInfo.appendChild(fileSize);

            const removeButton = document.createElement('button');
            removeButton.type = 'button';
            removeButton.className = 'rag-file-remove';
            removeButton.title = 'Удалить';
            removeButton.innerHTML = `
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
            `;
            removeButton.onclick = () => this.removeFile(index);

            fileItem.appendChild(fileIcon);
            fileItem.appendChild(fileInfo);
            fileItem.appendChild(removeButton);
            filesList.appendChild(fileItem);
        });
    }

    /**
     * Read content from first selected file
     * @returns {Promise<string>} File content
     */
    async readFirstFileContent() {
        if (this.selectedFiles.length === 0) {
            return '';
        }
        return readFileContent(this.selectedFiles[0]);
    }

    /**
     * Read content from all selected files
     * @returns {Promise<Array<{name: string, content: string}>>} Array of file contents
     */
    async readAllFilesContent() {
        const results = [];
        for (const file of this.selectedFiles) {
            const content = await readFileContent(file);
            results.push({ name: file.name, content });
        }
        return results;
    }
}
