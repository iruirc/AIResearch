/**
 * @fileoverview RAG Document Management Modal
 * Handles UI for RAG document CRUD operations
 * @module ui/ragModal
 */

import { ragApi } from '../api/ragApi.js';
import { ragTestApi } from '../api/ragTestApi.js';
import { modalsUI } from './modalsUI.js';
import { llmModelApi } from '../api/llmModelApi.js';
import { appState } from '../state/appState.js';

/**
 * RAG Modal class for managing document operations
 */
export class RAGModal {
    constructor() {
        this.documents = [];
        this.currentMode = 'list'; // 'list', 'add', 'edit'
        this.editingDocument = null;
        this.activeTab = 'text'; // 'text' or 'files' (for document form)
        this.selectedFiles = []; // Array of File objects
        this.activeModalTab = 'documents'; // 'documents', 'settings', or 'testing'
        this.searchPreferences = null; // Cached search preferences

        // Test-related properties
        this.tests = [];
        this.currentTestMode = 'list'; // 'list', 'add', 'edit'
        this.editingTest = null;
        this.activeTestTab = 'text'; // 'text' or 'files' (for test form)
        this.selectedTestFile = null; // Single File object for test

        // Bind methods
        this.initialize = this.initialize.bind(this);
        this.loadDocuments = this.loadDocuments.bind(this);
        this.renderDocumentsList = this.renderDocumentsList.bind(this);
        this.openAddDocumentForm = this.openAddDocumentForm.bind(this);
        this.openEditDocumentForm = this.openEditDocumentForm.bind(this);
        this.handleAddDocument = this.handleAddDocument.bind(this);
        this.handleUpdateDocument = this.handleUpdateDocument.bind(this);
        this.handleDeleteDocument = this.handleDeleteDocument.bind(this);
        this.handleToggleDocument = this.handleToggleDocument.bind(this);
        this.setupTabs = this.setupTabs.bind(this);
        this.setupFileHandlers = this.setupFileHandlers.bind(this);
        this.switchTab = this.switchTab.bind(this);
        this.addFiles = this.addFiles.bind(this);
        this.removeFile = this.removeFile.bind(this);
        this.renderFilesList = this.renderFilesList.bind(this);
        this.readFileContent = this.readFileContent.bind(this);

        // Modal tab methods
        this.setupModalTabs = this.setupModalTabs.bind(this);
        this.switchModalTab = this.switchModalTab.bind(this);

        // Settings methods
        this.loadSearchPreferences = this.loadSearchPreferences.bind(this);
        this.populateSettingsForm = this.populateSettingsForm.bind(this);
        this.handleSaveSettings = this.handleSaveSettings.bind(this);
        this.handleResetSettings = this.handleResetSettings.bind(this);
        this.updateStrategyParams = this.updateStrategyParams.bind(this);
        this.setupSettingsHandlers = this.setupSettingsHandlers.bind(this);

        // Cross-Encoder provider/model methods
        this.loadCrossEncoderProviders = this.loadCrossEncoderProviders.bind(this);
        this.loadCrossEncoderModels = this.loadCrossEncoderModels.bind(this);

        // Test methods
        this.loadTests = this.loadTests.bind(this);
        this.renderTestsList = this.renderTestsList.bind(this);
        this.openAddTestForm = this.openAddTestForm.bind(this);
        this.openEditTestForm = this.openEditTestForm.bind(this);
        this.handleAddTest = this.handleAddTest.bind(this);
        this.handleUpdateTest = this.handleUpdateTest.bind(this);
        this.handleDeleteTest = this.handleDeleteTest.bind(this);
        this.setupTestTabs = this.setupTestTabs.bind(this);
        this.setupTestFileHandlers = this.setupTestFileHandlers.bind(this);
        this.switchTestTab = this.switchTestTab.bind(this);

        // Validation methods
        this.validateTestJSON = this.validateTestJSON.bind(this);
        this.showValidationError = this.showValidationError.bind(this);
        this.hideValidationError = this.hideValidationError.bind(this);

        // Test execution methods
        this.handleExecuteTest = this.handleExecuteTest.bind(this);
        this.openExecutionModal = this.openExecutionModal.bind(this);
        this.updateExecutionProgress = this.updateExecutionProgress.bind(this);
        this.handleExecutionComplete = this.handleExecutionComplete.bind(this);
        this.handleExecutionCancel = this.handleExecutionCancel.bind(this);
        this.downloadResults = this.downloadResults.bind(this);
        this.formatTime = this.formatTime.bind(this);

        // Test execution state
        this.currentExecution = null;
        this.executionTimer = null;
        this.executionStartTime = null;
        this.executionResults = null;
    }

    /**
     * Initialize the RAG modal
     */
    async initialize() {
        console.log('Initializing RAG Modal...');

        // Setup event listeners for modal buttons
        const ragButton = document.getElementById('ragButton');
        if (ragButton) {
            ragButton.addEventListener('click', async () => {
                await this.open();
            });
        }

        const closeButton = document.getElementById('closeRagModal');
        if (closeButton) {
            closeButton.addEventListener('click', () => {
                modalsUI.closeModal('ragModal');
            });
        }

        // Setup modal tabs (Documents / Settings)
        this.setupModalTabs();

        // Setup settings form handlers
        this.setupSettingsHandlers();

        console.log('RAG Modal initialized');
    }

    /**
     * Open the RAG modal and load documents
     */
    async open() {
        console.log('Opening RAG modal...');
        this.currentMode = 'list';
        modalsUI.openModal('ragModal');
        await this.loadDocuments();
    }

    /**
     * Load all RAG documents from the API
     */
    async loadDocuments() {
        try {
            console.log('Loading RAG documents...');
            const ragDocumentsList = document.getElementById('ragDocumentsList');

            if (ragDocumentsList) {
                ragDocumentsList.innerHTML = '<div class="rag-documents-loading">Загрузка документов...</div>';
            }

            this.documents = await ragApi.loadDocuments();
            console.log(`Loaded ${this.documents.length} documents`);

            this.renderDocumentsList();
        } catch (error) {
            console.error('Error loading RAG documents:', error);
            const ragDocumentsList = document.getElementById('ragDocumentsList');
            if (ragDocumentsList) {
                ragDocumentsList.innerHTML = `<div class="rag-documents-error">Ошибка загрузки: ${error.message}</div>`;
            }
        }
    }

    /**
     * Render the documents list view
     */
    renderDocumentsList() {
        const ragDocumentsList = document.getElementById('ragDocumentsList');
        if (!ragDocumentsList) return;

        ragDocumentsList.innerHTML = '';

        // Add "Add Document" button at the top (gradient style like pipeline)
        const addButton = document.createElement('button');
        addButton.className = 'rag-add-document-button';
        addButton.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>Добавить знание</span>
        `;
        addButton.addEventListener('click', () => this.openAddDocumentForm());
        ragDocumentsList.appendChild(addButton);

        // Show empty state if no documents
        if (!this.documents || this.documents.length === 0) {
            const emptyMessage = document.createElement('div');
            emptyMessage.className = 'rag-documents-empty';
            emptyMessage.textContent = 'Нет документов. Добавьте документ для использования RAG.';
            ragDocumentsList.appendChild(emptyMessage);
            return;
        }

        // Render each document
        this.documents.forEach(doc => {
            const docItem = document.createElement('div');
            docItem.className = 'rag-document-item';
            if (!doc.enabled) {
                docItem.classList.add('disabled');
            }

            // Document content
            const contentDiv = document.createElement('div');
            contentDiv.className = 'rag-document-content';

            const nameDiv = document.createElement('div');
            nameDiv.className = 'rag-document-name';
            nameDiv.textContent = doc.name;

            const metaDiv = document.createElement('div');
            metaDiv.className = 'rag-document-meta';
            metaDiv.innerHTML = `
                <span>Чанков: ${doc.chunks?.length || 0}</span>
                <span>Стратегия: ${this.getStrategyLabel(doc.chunkingStrategy)}</span>
                <span class="rag-document-status ${doc.enabled ? 'enabled' : 'disabled'}">${doc.enabled ? 'Активен' : 'Отключён'}</span>
            `;

            contentDiv.appendChild(nameDiv);
            contentDiv.appendChild(metaDiv);

            // Document actions
            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'rag-document-actions';

            // Toggle switch (ON/OFF)
            const toggleSwitch = document.createElement('label');
            toggleSwitch.className = 'rag-toggle-switch';
            toggleSwitch.title = doc.enabled ? 'Отключить' : 'Включить';
            const toggleInput = document.createElement('input');
            toggleInput.type = 'checkbox';
            toggleInput.checked = doc.enabled;
            toggleInput.addEventListener('change', async (e) => {
                e.stopPropagation();
                await this.handleToggleDocument(doc.id, e.target.checked);
            });
            const toggleSlider = document.createElement('span');
            toggleSlider.className = 'rag-toggle-slider';
            toggleSwitch.appendChild(toggleInput);
            toggleSwitch.appendChild(toggleSlider);

            // Edit button
            const editButton = document.createElement('button');
            editButton.className = 'rag-action-button edit-button';
            editButton.title = 'Редактировать';
            editButton.innerHTML = `
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
            `;
            editButton.addEventListener('click', async (e) => {
                e.stopPropagation();
                await this.openEditDocumentForm(doc.id);
            });

            // Delete button
            const deleteButton = document.createElement('button');
            deleteButton.className = 'rag-action-button delete-button';
            deleteButton.title = 'Удалить';
            deleteButton.textContent = '🗑️';
            deleteButton.addEventListener('click', async (e) => {
                e.stopPropagation();
                await this.handleDeleteDocument(doc.id, doc.name);
            });

            // Order: Toggle, Edit, Delete (right to left means we add in this order)
            actionsDiv.appendChild(toggleSwitch);
            actionsDiv.appendChild(editButton);
            actionsDiv.appendChild(deleteButton);

            docItem.appendChild(contentDiv);
            docItem.appendChild(actionsDiv);
            ragDocumentsList.appendChild(docItem);
        });
    }

    /**
     * Open the add document form
     */
    openAddDocumentForm() {
        this.currentMode = 'add';
        this.editingDocument = null;
        this.selectedFiles = [];
        this.activeTab = 'text';
        modalsUI.closeModal('ragModal');
        modalsUI.openModal('ragFormModal');

        // Set form title
        const formTitle = document.getElementById('ragFormTitle');
        if (formTitle) {
            formTitle.textContent = 'Добавить знание';
        }

        // Clear form
        const nameInput = document.getElementById('ragDocumentName');
        const contentInput = document.getElementById('ragDocumentContent');
        const strategySelect = document.getElementById('ragChunkingStrategy');
        const enabledCheckbox = document.getElementById('ragDocumentEnabled');

        if (nameInput) nameInput.value = '';
        if (contentInput) {
            contentInput.value = '';
            contentInput.disabled = false;
        }
        if (strategySelect) strategySelect.value = 'FIXED_SIZE';
        if (enabledCheckbox) enabledCheckbox.checked = true;

        // Show tabs and reset to text tab
        const tabsContainer = document.getElementById('ragSourceTabs');
        if (tabsContainer) {
            tabsContainer.classList.remove('hidden');
        }
        this.switchTab('text');
        this.renderFilesList();

        // Setup tabs and file handlers
        this.setupTabs();
        this.setupFileHandlers();

        // Setup form submission
        const form = document.getElementById('ragDocumentForm');
        if (form) {
            form.onsubmit = async (e) => {
                e.preventDefault();
                await this.handleAddDocument();
            };
        }

        // Setup cancel button
        const cancelButton = document.getElementById('cancelRagFormButton');
        if (cancelButton) {
            cancelButton.onclick = () => {
                modalsUI.closeModal('ragFormModal');
                modalsUI.openModal('ragModal');
            };
        }
    }

    /**
     * Setup tab switching functionality
     */
    setupTabs() {
        const tabs = document.querySelectorAll('.rag-tab');
        tabs.forEach(tab => {
            tab.onclick = () => {
                const tabName = tab.dataset.tab;
                this.switchTab(tabName);
            };
        });
    }

    /**
     * Switch between text and files tabs
     * @param {string} tabName - Tab name ('text' or 'files')
     */
    switchTab(tabName) {
        this.activeTab = tabName;

        // Update tab buttons
        const tabs = document.querySelectorAll('.rag-tab');
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
    }

    /**
     * Setup file drag&drop and input handlers
     */
    setupFileHandlers() {
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
        const allowedExtensions = ['.txt', '.md', '.json', '.xml', '.log'];

        files.forEach(file => {
            const ext = '.' + file.name.split('.').pop().toLowerCase();
            if (allowedExtensions.includes(ext)) {
                // Check if file already exists
                if (!this.selectedFiles.some(f => f.name === file.name && f.size === file.size)) {
                    this.selectedFiles.push(file);
                }
            } else {
                console.warn(`File ${file.name} has unsupported extension`);
            }
        });

        this.renderFilesList();
    }

    /**
     * Remove file from selected files
     * @param {number} index - Index of file to remove
     */
    removeFile(index) {
        this.selectedFiles.splice(index, 1);
        this.renderFilesList();
    }

    /**
     * Render the list of selected files
     */
    renderFilesList() {
        const filesList = document.getElementById('ragFilesList');
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
            fileSize.textContent = this.formatFileSize(file.size);

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
     * Format file size for display
     * @param {number} bytes - File size in bytes
     * @returns {string} Formatted file size
     */
    formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    /**
     * Read content from a file
     * @param {File} file - File to read
     * @returns {Promise<string>} File content
     */
    readFileContent(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = (e) => resolve(e.target?.result || '');
            reader.onerror = (e) => reject(new Error(`Failed to read file: ${file.name}`));
            reader.readAsText(file);
        });
    }

    /**
     * Open the edit document form
     * @param {string} documentId - Document ID to edit
     */
    async openEditDocumentForm(documentId) {
        try {
            this.currentMode = 'edit';

            // Load full document details
            const doc = await ragApi.getDocument(documentId);
            this.editingDocument = doc;

            modalsUI.closeModal('ragModal');
            modalsUI.openModal('ragFormModal');

            // Set form title
            const formTitle = document.getElementById('ragFormTitle');
            if (formTitle) {
                formTitle.textContent = 'Редактировать документ';
            }

            // Hide tabs in edit mode (content cannot be changed)
            const tabsContainer = document.getElementById('ragSourceTabs');
            if (tabsContainer) {
                tabsContainer.classList.add('hidden');
            }

            // Show text tab content only
            this.switchTab('text');

            // Fill form with document data
            const nameInput = document.getElementById('ragDocumentName');
            const contentInput = document.getElementById('ragDocumentContent');
            const strategySelect = document.getElementById('ragChunkingStrategy');
            const enabledCheckbox = document.getElementById('ragDocumentEnabled');

            if (nameInput) nameInput.value = doc.name || '';
            if (contentInput) contentInput.value = doc.originalContent || '';
            if (strategySelect) strategySelect.value = doc.chunkingStrategy || 'FIXED_SIZE';
            if (enabledCheckbox) enabledCheckbox.checked = doc.enabled !== false;

            // Disable content editing (can't change document content, only metadata)
            if (contentInput) {
                contentInput.disabled = true;
                contentInput.title = 'Содержимое документа нельзя изменить после создания';
            }

            // Setup form submission
            const form = document.getElementById('ragDocumentForm');
            if (form) {
                form.onsubmit = async (e) => {
                    e.preventDefault();
                    await this.handleUpdateDocument();
                };
            }

            // Setup cancel button
            const cancelButton = document.getElementById('cancelRagFormButton');
            if (cancelButton) {
                cancelButton.onclick = () => {
                    modalsUI.closeModal('ragFormModal');
                    modalsUI.openModal('ragModal');
                };
            }
        } catch (error) {
            console.error('Error loading document for editing:', error);
            alert(`Ошибка загрузки документа: ${error.message}`);
        }
    }

    /**
     * Handle adding a new document
     */
    async handleAddDocument() {
        const nameInput = document.getElementById('ragDocumentName');
        const contentInput = document.getElementById('ragDocumentContent');
        const strategySelect = document.getElementById('ragChunkingStrategy');
        const enabledCheckbox = document.getElementById('ragDocumentEnabled');

        const baseName = nameInput?.value?.trim();
        const strategy = strategySelect?.value || 'FIXED_SIZE';
        const enabled = enabledCheckbox?.checked !== false;

        if (!baseName) {
            alert('Пожалуйста, укажите название документа');
            return;
        }

        try {
            if (this.activeTab === 'text') {
                // Text mode: single document from textarea
                const content = contentInput?.value?.trim();
                if (!content) {
                    alert('Пожалуйста, введите содержимое документа');
                    return;
                }

                console.log(`Adding document: ${baseName}`);
                await ragApi.addDocument(baseName, content, strategy, enabled);
                console.log('Document added successfully');

            } else {
                // Files mode: combine all files into single document
                if (this.selectedFiles.length === 0) {
                    alert('Пожалуйста, выберите хотя бы один файл');
                    return;
                }

                console.log(`Combining ${this.selectedFiles.length} file(s) into single document`);

                // Read all files and combine their content
                const contentParts = [];
                for (const file of this.selectedFiles) {
                    const fileContent = await this.readFileContent(file);
                    // Add file header for clarity
                    contentParts.push(`--- Файл: ${file.name} ---\n${fileContent}`);
                }

                // Combine all file contents with separator
                const combinedContent = contentParts.join('\n\n');

                console.log(`Adding combined document: ${baseName}`);
                await ragApi.addDocument(baseName, combinedContent, strategy, enabled);
                console.log('Combined document added successfully');
            }

            modalsUI.closeModal('ragFormModal');
            modalsUI.openModal('ragModal');
            await this.loadDocuments();

        } catch (error) {
            console.error('Error adding document:', error);
            if (error.message.startsWith('DUPLICATE_NAME:')) {
                alert(`Знание с именем "${baseName}" уже существует.\nВыберите другое имя.`);
            } else {
                alert(`Ошибка при добавлении документа: ${error.message}`);
            }
        }
    }

    /**
     * Handle updating an existing document
     */
    async handleUpdateDocument() {
        if (!this.editingDocument) return;

        const nameInput = document.getElementById('ragDocumentName');
        const strategySelect = document.getElementById('ragChunkingStrategy');
        const enabledCheckbox = document.getElementById('ragDocumentEnabled');

        const name = nameInput?.value?.trim();
        const strategy = strategySelect?.value;
        const enabled = enabledCheckbox?.checked;

        if (!name) {
            alert('Пожалуйста, укажите название документа');
            return;
        }

        try {
            console.log(`Updating document: ${this.editingDocument.id}`);
            await ragApi.updateDocument(this.editingDocument.id, name, enabled, strategy);

            modalsUI.closeModal('ragFormModal');
            modalsUI.openModal('ragModal');
            await this.loadDocuments();

            console.log('Document updated successfully');
        } catch (error) {
            console.error('Error updating document:', error);
            if (error.message.startsWith('DUPLICATE_NAME:')) {
                alert(`Знание с именем "${name}" уже существует.\nВыберите другое имя.`);
            } else {
                alert(`Ошибка при обновлении документа: ${error.message}`);
            }
        }
    }

    /**
     * Handle deleting a document
     * @param {string} documentId - Document ID to delete
     * @param {string} documentName - Document name for confirmation
     */
    async handleDeleteDocument(documentId, documentName) {
        if (!confirm(`Вы уверены, что хотите удалить документ "${documentName}"?\nЭто действие нельзя отменить.`)) {
            return;
        }

        try {
            console.log(`Deleting document: ${documentId}`);
            await ragApi.deleteDocument(documentId);
            await this.loadDocuments();
            console.log('Document deleted successfully');
        } catch (error) {
            console.error('Error deleting document:', error);
            alert(`Ошибка при удалении документа: ${error.message}`);
        }
    }

    /**
     * Handle toggling document enabled/disabled state
     * @param {string} documentId - Document ID
     * @param {boolean} enabled - New enabled state
     */
    async handleToggleDocument(documentId, enabled) {
        try {
            console.log(`Toggling document ${documentId} to ${enabled ? 'enabled' : 'disabled'}`);
            await ragApi.updateDocument(documentId, null, enabled, null);
            await this.loadDocuments();
            console.log(`Document ${enabled ? 'enabled' : 'disabled'} successfully`);
        } catch (error) {
            console.error('Error toggling document:', error);
            alert(`Ошибка при изменении состояния документа: ${error.message}`);
        }
    }

    /**
     * Get human-readable label for chunking strategy
     * @param {string} strategy - Chunking strategy enum value
     * @returns {string} Human-readable label
     */
    getStrategyLabel(strategy) {
        const labels = {
            'FIXED_SIZE': 'Фиксированный размер',
            'RECURSIVE': 'Рекурсивный',
            'SEMANTIC': 'Семантический'
        };
        return labels[strategy] || strategy;
    }

    // ============================================
    // Modal Tab Methods
    // ============================================

    /**
     * Setup modal tab switching (Documents / Settings)
     */
    setupModalTabs() {
        const tabs = document.querySelectorAll('.rag-modal-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const tabName = tab.dataset.tab;
                this.switchModalTab(tabName);
            });
        });
    }

    /**
     * Switch between modal tabs
     * @param {string} tabName - Tab name ('documents', 'settings', or 'testing')
     */
    async switchModalTab(tabName) {
        this.activeModalTab = tabName;

        // Update tab buttons
        const tabs = document.querySelectorAll('.rag-modal-tab');
        tabs.forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tab === tabName);
        });

        // Update tab content
        const documentsTab = document.getElementById('ragTabDocuments');
        const settingsTab = document.getElementById('ragTabSettings');
        const testingTab = document.getElementById('ragTabTesting');

        if (documentsTab) {
            documentsTab.classList.toggle('active', tabName === 'documents');
        }
        if (settingsTab) {
            settingsTab.classList.toggle('active', tabName === 'settings');
        }
        if (testingTab) {
            testingTab.classList.toggle('active', tabName === 'testing');
        }

        // Load data for the active tab
        if (tabName === 'settings') {
            await this.loadSearchPreferences();
        } else if (tabName === 'testing') {
            await this.loadTests();
        }
    }

    // ============================================
    // Search Settings Methods
    // ============================================

    /**
     * Setup settings form event handlers
     */
    setupSettingsHandlers() {
        // Enable reranking toggle
        const enableRerankingToggle = document.getElementById('ragEnableReranking');
        if (enableRerankingToggle) {
            enableRerankingToggle.addEventListener('change', (e) => {
                const options = document.getElementById('ragRerankingOptions');
                if (options) {
                    options.classList.toggle('hidden', !e.target.checked);
                }
            });
        }

        // Strategy selector
        const strategySelect = document.getElementById('ragStrategy');
        if (strategySelect) {
            strategySelect.addEventListener('change', async (e) => {
                await this.updateStrategyParams(e.target.value);
            });
        }

        // Settings form submission
        const settingsForm = document.getElementById('ragSearchSettingsForm');
        if (settingsForm) {
            settingsForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                await this.handleSaveSettings();
            });
        }

        // Reset button
        const resetButton = document.getElementById('ragResetSettingsButton');
        if (resetButton) {
            resetButton.addEventListener('click', async () => {
                await this.handleResetSettings();
            });
        }

        // Cross-Encoder provider selector
        const crossEncoderProviderSelect = document.getElementById('ragCrossEncoderProvider');
        if (crossEncoderProviderSelect) {
            crossEncoderProviderSelect.addEventListener('change', async (e) => {
                await this.loadCrossEncoderModels(e.target.value);
            });
        }
    }

    /**
     * Load search preferences from API
     */
    async loadSearchPreferences() {
        try {
            console.log('Loading RAG search preferences...');
            this.searchPreferences = await ragApi.getSearchPreferences();
            this.populateSettingsForm(this.searchPreferences);
        } catch (error) {
            console.error('Error loading search preferences:', error);
            // Use defaults if loading fails
            try {
                this.searchPreferences = await ragApi.getDefaultPreferences();
                this.populateSettingsForm(this.searchPreferences);
            } catch (defaultError) {
                console.error('Error loading default preferences:', defaultError);
            }
        }
    }

    /**
     * Populate settings form with preferences data
     * @param {Object} prefs - Preferences object
     */
    async populateSettingsForm(prefs) {
        if (!prefs) return;

        // Enable reranking toggle
        const enableReranking = document.getElementById('ragEnableReranking');
        if (enableReranking) {
            enableReranking.checked = prefs.enableReranking || false;
            // Show/hide options
            const options = document.getElementById('ragRerankingOptions');
            if (options) {
                options.classList.toggle('hidden', !prefs.enableReranking);
            }
        }

        // Strategy - pass saved provider and model for CROSS_ENCODER
        const strategy = document.getElementById('ragStrategy');
        if (strategy) {
            strategy.value = prefs.strategy || 'SCORE_THRESHOLD';
            await this.updateStrategyParams(
                prefs.strategy || 'SCORE_THRESHOLD',
                prefs.crossEncoderProvider,
                prefs.crossEncoderModel
            );
        }

        // SCORE_THRESHOLD params
        const scoreThreshold = document.getElementById('ragScoreThreshold');
        if (scoreThreshold) {
            scoreThreshold.value = prefs.scoreThreshold ?? 0.75;
        }

        // STATISTICAL params
        const stdDevMultiplier = document.getElementById('ragStdDevMultiplier');
        if (stdDevMultiplier) {
            stdDevMultiplier.value = prefs.stdDevMultiplier ?? 1.0;
        }

        const minResultsToKeep = document.getElementById('ragMinResultsToKeep');
        if (minResultsToKeep) {
            minResultsToKeep.value = prefs.minResultsToKeep ?? 1;
        }

        // CROSS_ENCODER params - model is populated dynamically via updateStrategyParams
        // Just set the min score here

        const crossEncoderMinScore = document.getElementById('ragCrossEncoderMinScore');
        if (crossEncoderMinScore) {
            crossEncoderMinScore.value = prefs.crossEncoderMinScore ?? 6.0;
        }

        // General search params
        const searchTopK = document.getElementById('ragSearchTopK');
        if (searchTopK) {
            searchTopK.value = prefs.searchTopK ?? 5;
        }

        const searchMinScore = document.getElementById('ragSearchMinScore');
        if (searchMinScore) {
            searchMinScore.value = prefs.searchMinScore ?? 0.7;
        }

        // Debug mode
        const debugMode = document.getElementById('ragDebugMode');
        if (debugMode) {
            debugMode.checked = prefs.debugMode || false;
        }
    }

    /**
     * Update visibility of strategy-specific parameter sections
     * @param {string} strategy - Selected strategy
     * @param {string} savedProvider - Provider from saved preferences (optional)
     * @param {string} savedModel - Model from saved preferences (optional)
     */
    async updateStrategyParams(strategy, savedProvider = null, savedModel = null) {
        const scoreThresholdParams = document.getElementById('ragScoreThresholdParams');
        const statisticalParams = document.getElementById('ragStatisticalParams');
        const crossEncoderParams = document.getElementById('ragCrossEncoderParams');

        // Hide all
        if (scoreThresholdParams) scoreThresholdParams.classList.add('hidden');
        if (statisticalParams) statisticalParams.classList.add('hidden');
        if (crossEncoderParams) crossEncoderParams.classList.add('hidden');

        // Show relevant section
        switch (strategy) {
            case 'SCORE_THRESHOLD':
                if (scoreThresholdParams) scoreThresholdParams.classList.remove('hidden');
                break;
            case 'STATISTICAL':
                if (statisticalParams) statisticalParams.classList.remove('hidden');
                break;
            case 'CROSS_ENCODER':
                if (crossEncoderParams) crossEncoderParams.classList.remove('hidden');
                // Load providers and models for Cross-Encoder
                await this.loadCrossEncoderProviders(savedProvider, savedModel);
                break;
        }
    }

    /**
     * Handle saving search settings
     */
    async handleSaveSettings() {
        try {
            // Gather form data
            const preferences = {
                enableReranking: document.getElementById('ragEnableReranking')?.checked || false,
                strategy: document.getElementById('ragStrategy')?.value || 'SCORE_THRESHOLD',
                scoreThreshold: parseFloat(document.getElementById('ragScoreThreshold')?.value) || 0.75,
                stdDevMultiplier: parseFloat(document.getElementById('ragStdDevMultiplier')?.value) || 1.0,
                minResultsToKeep: parseInt(document.getElementById('ragMinResultsToKeep')?.value) || 1,
                crossEncoderProvider: document.getElementById('ragCrossEncoderProvider')?.value || null,
                crossEncoderModel: document.getElementById('ragCrossEncoderModel')?.value || null,
                crossEncoderMinScore: parseFloat(document.getElementById('ragCrossEncoderMinScore')?.value) || 6.0,
                searchTopK: parseInt(document.getElementById('ragSearchTopK')?.value) || 5,
                searchMinScore: parseFloat(document.getElementById('ragSearchMinScore')?.value) || 0.7,
                debugMode: document.getElementById('ragDebugMode')?.checked || false
            };

            console.log('Saving RAG search preferences:', preferences);
            await ragApi.saveSearchPreferences(preferences);
            this.searchPreferences = preferences;

            // Show success message
            this.showSettingsNotification('Настройки сохранены', 'success');
        } catch (error) {
            console.error('Error saving search preferences:', error);
            this.showSettingsNotification(`Ошибка: ${error.message}`, 'error');
        }
    }

    /**
     * Handle resetting search settings to defaults
     */
    async handleResetSettings() {
        if (!confirm('Сбросить настройки поиска к значениям по умолчанию?')) {
            return;
        }

        try {
            console.log('Resetting RAG search preferences...');
            const defaults = await ragApi.resetSearchPreferences();
            this.searchPreferences = defaults;
            this.populateSettingsForm(defaults);

            this.showSettingsNotification('Настройки сброшены', 'success');
        } catch (error) {
            console.error('Error resetting search preferences:', error);
            this.showSettingsNotification(`Ошибка: ${error.message}`, 'error');
        }
    }

    /**
     * Show notification in settings tab
     * @param {string} message - Message to display
     * @param {string} type - 'success' or 'error'
     */
    showSettingsNotification(message, type) {
        // Create or reuse notification element
        let notification = document.querySelector('.rag-settings-notification');
        if (!notification) {
            notification = document.createElement('div');
            notification.className = 'rag-settings-notification';
            const settingsContent = document.querySelector('.rag-settings-content');
            if (settingsContent) {
                settingsContent.insertBefore(notification, settingsContent.firstChild);
            }
        }

        notification.textContent = message;
        notification.className = `rag-settings-notification ${type}`;
        notification.style.display = 'block';

        // Auto-hide after 3 seconds
        setTimeout(() => {
            notification.style.display = 'none';
        }, 3000);
    }

    // ============================================
    // Cross-Encoder Provider/Model Methods
    // ============================================

    /**
     * Load providers list for Cross-Encoder dropdown
     * Uses the same providers as global settings
     * @param {string} currentProvider - Currently selected provider ID (optional)
     * @param {string} currentModel - Currently selected model ID (optional)
     */
    async loadCrossEncoderProviders(currentProvider = null, currentModel = null) {
        const providerSelect = document.getElementById('ragCrossEncoderProvider');
        if (!providerSelect) return;

        try {
            // Get providers from appState or load from API
            let providers = appState.providers;
            if (!providers || providers.length === 0) {
                providers = await llmModelApi.loadProviders();
            }

            // Clear existing options
            providerSelect.innerHTML = '';

            // Populate provider dropdown
            providers.forEach(provider => {
                const option = document.createElement('option');
                option.value = provider.id;
                option.textContent = provider.displayName || provider.name;
                providerSelect.appendChild(option);
            });

            // Determine default provider: use provided, or current global, or first available
            let defaultProvider = currentProvider;
            if (!defaultProvider && appState.currentProvider) {
                defaultProvider = appState.currentProvider;
            }
            if (!defaultProvider && providers.length > 0) {
                defaultProvider = providers[0].id;
            }

            if (defaultProvider) {
                providerSelect.value = defaultProvider;
                // Load models for the selected provider
                await this.loadCrossEncoderModels(defaultProvider, currentModel);
            }
        } catch (error) {
            console.error('Error loading Cross-Encoder providers:', error);
        }
    }

    /**
     * Load models for selected provider in Cross-Encoder dropdown
     * @param {string} providerId - Provider ID to load models for
     * @param {string} currentModel - Currently selected model ID (optional)
     */
    async loadCrossEncoderModels(providerId, currentModel = null) {
        const modelSelect = document.getElementById('ragCrossEncoderModel');
        if (!modelSelect || !providerId) return;

        try {
            // Load models from API
            const models = await llmModelApi.loadModels(providerId);

            // Clear existing options
            modelSelect.innerHTML = '';

            // Populate model dropdown
            models.forEach(model => {
                const option = document.createElement('option');
                option.value = model.id;
                option.textContent = model.displayName || model.name || model.id;
                modelSelect.appendChild(option);
            });

            // Determine default model
            let defaultModel = currentModel;

            // If no current model set, try to use the global model (if same provider)
            if (!defaultModel && appState.currentProvider === providerId && appState.currentSettings?.model) {
                defaultModel = appState.currentSettings.model;
            }

            // If model exists in options, select it
            if (defaultModel) {
                const modelExists = Array.from(modelSelect.options).some(opt => opt.value === defaultModel);
                if (modelExists) {
                    modelSelect.value = defaultModel;
                }
            }
        } catch (error) {
            console.error('Error loading Cross-Encoder models:', error);
            modelSelect.innerHTML = '<option value="">Ошибка загрузки</option>';
        }
    }

    // ============================================
    // RAG Testing Methods
    // ============================================

    /**
     * Load all RAG tests from the API
     */
    async loadTests() {
        try {
            console.log('Loading RAG tests...');
            const ragTestsList = document.getElementById('ragTestsList');

            if (ragTestsList) {
                ragTestsList.innerHTML = '<div class="rag-tests-loading">Загрузка тестов...</div>';
            }

            this.tests = await ragTestApi.loadTests();
            console.log(`Loaded ${this.tests.length} tests`);

            this.renderTestsList();
        } catch (error) {
            console.error('Error loading RAG tests:', error);
            const ragTestsList = document.getElementById('ragTestsList');
            if (ragTestsList) {
                ragTestsList.innerHTML = `<div class="rag-tests-error">Ошибка загрузки: ${error.message}</div>`;
            }
        }
    }

    /**
     * Render the tests list view
     */
    renderTestsList() {
        const ragTestsList = document.getElementById('ragTestsList');
        if (!ragTestsList) return;

        ragTestsList.innerHTML = '';

        // Add "Add Test" button at the top
        const addButton = document.createElement('button');
        addButton.className = 'rag-add-test-button';
        addButton.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>Добавить тест</span>
        `;
        addButton.addEventListener('click', () => this.openAddTestForm());
        ragTestsList.appendChild(addButton);

        // Show empty state if no tests
        if (!this.tests || this.tests.length === 0) {
            const emptyMessage = document.createElement('div');
            emptyMessage.className = 'rag-tests-empty';
            emptyMessage.textContent = 'Нет тестов. Добавьте тест для проверки качества RAG.';
            ragTestsList.appendChild(emptyMessage);
            return;
        }

        // Render each test
        this.tests.forEach(test => {
            const testItem = document.createElement('div');
            testItem.className = 'rag-test-item';

            // Test content
            const contentDiv = document.createElement('div');
            contentDiv.className = 'rag-test-content';

            const nameDiv = document.createElement('div');
            nameDiv.className = 'rag-test-name';
            nameDiv.textContent = test.name;

            const metaDiv = document.createElement('div');
            metaDiv.className = 'rag-test-meta';
            const createdDate = new Date(test.createdAt).toLocaleDateString('ru-RU');
            const queriesCount = test.queries ? test.queries.length : 0;
            metaDiv.innerHTML = `
                <span>Запросов: ${queriesCount}</span>
                <span>Создан: ${createdDate}</span>
            `;

            contentDiv.appendChild(nameDiv);
            contentDiv.appendChild(metaDiv);

            // Test actions
            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'rag-test-actions';

            // Edit button
            const editButton = document.createElement('button');
            editButton.className = 'rag-test-action-button edit-button';
            editButton.title = 'Редактировать';
            editButton.innerHTML = `
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
            `;
            editButton.addEventListener('click', async (e) => {
                e.stopPropagation();
                await this.openEditTestForm(test.id);
            });

            // Delete button
            const deleteButton = document.createElement('button');
            deleteButton.className = 'rag-test-action-button delete-button';
            deleteButton.title = 'Удалить';
            deleteButton.textContent = '🗑️';
            deleteButton.addEventListener('click', async (e) => {
                e.stopPropagation();
                await this.handleDeleteTest(test.id, test.name);
            });

            // Execute button
            const executeButton = document.createElement('button');
            executeButton.className = 'rag-test-action-button execute-button';
            executeButton.title = 'Выполнить тест';
            executeButton.innerHTML = `
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
            `;
            executeButton.addEventListener('click', async (e) => {
                e.stopPropagation();
                await this.handleExecuteTest(test);
            });

            actionsDiv.appendChild(executeButton);
            actionsDiv.appendChild(editButton);
            actionsDiv.appendChild(deleteButton);

            testItem.appendChild(contentDiv);
            testItem.appendChild(actionsDiv);
            ragTestsList.appendChild(testItem);
        });
    }

    /**
     * Open the add test form
     */
    openAddTestForm() {
        this.currentTestMode = 'add';
        this.editingTest = null;
        this.selectedTestFile = null;
        this.activeTestTab = 'text';
        modalsUI.closeModal('ragModal');
        modalsUI.openModal('ragTestFormModal');

        // Set form title
        const formTitle = document.getElementById('ragTestFormTitle');
        if (formTitle) {
            formTitle.textContent = 'Добавить тест';
        }

        // Clear form
        const nameInput = document.getElementById('ragTestName');
        const contentInput = document.getElementById('ragTestContent');

        if (nameInput) nameInput.value = '';
        if (contentInput) {
            contentInput.value = '';
            contentInput.disabled = false;
        }

        // Hide any previous validation errors
        this.hideValidationError();

        // Show tabs and reset to text tab
        const tabsContainer = document.getElementById('ragTestSourceTabs');
        if (tabsContainer) {
            tabsContainer.classList.remove('hidden');
        }
        this.switchTestTab('text');
        this.renderTestFilesList();

        // Setup tabs and file handlers
        this.setupTestTabs();
        this.setupTestFileHandlers();

        // Setup form submission
        const form = document.getElementById('ragTestForm');
        if (form) {
            form.onsubmit = async (e) => {
                e.preventDefault();
                await this.handleAddTest();
            };
        }

        // Setup cancel button
        const cancelButton = document.getElementById('cancelRagTestFormButton');
        if (cancelButton) {
            cancelButton.onclick = () => {
                modalsUI.closeModal('ragTestFormModal');
                modalsUI.openModal('ragModal');
            };
        }

        // Setup close button
        const closeButton = document.getElementById('closeRagTestFormModal');
        if (closeButton) {
            closeButton.onclick = () => {
                modalsUI.closeModal('ragTestFormModal');
                modalsUI.openModal('ragModal');
            };
        }
    }

    /**
     * Open the edit test form
     * @param {string} testId - Test ID to edit
     */
    async openEditTestForm(testId) {
        try {
            this.currentTestMode = 'edit';

            // Hide any previous validation errors
            this.hideValidationError();

            // Load full test details
            const test = await ragTestApi.getTest(testId);
            this.editingTest = test;

            modalsUI.closeModal('ragModal');
            modalsUI.openModal('ragTestFormModal');

            // Set form title
            const formTitle = document.getElementById('ragTestFormTitle');
            if (formTitle) {
                formTitle.textContent = 'Редактировать тест';
            }

            // Hide tabs in edit mode (show text content only)
            const tabsContainer = document.getElementById('ragTestSourceTabs');
            if (tabsContainer) {
                tabsContainer.classList.add('hidden');
            }

            // Show text tab content only
            this.switchTestTab('text');

            // Fill form with test data
            const nameInput = document.getElementById('ragTestName');
            const contentInput = document.getElementById('ragTestContent');

            if (nameInput) nameInput.value = test.name || '';
            if (contentInput) {
                // Convert queries to JSON format for editing
                const contentObj = {
                    queries: test.queries || []
                };
                if (test.evaluationMetrics) {
                    contentObj.evaluationMetrics = test.evaluationMetrics;
                }
                contentInput.value = JSON.stringify(contentObj, null, 2);
                contentInput.disabled = false;
            }

            // Setup form submission
            const form = document.getElementById('ragTestForm');
            if (form) {
                form.onsubmit = async (e) => {
                    e.preventDefault();
                    await this.handleUpdateTest();
                };
            }

            // Setup cancel button
            const cancelButton = document.getElementById('cancelRagTestFormButton');
            if (cancelButton) {
                cancelButton.onclick = () => {
                    modalsUI.closeModal('ragTestFormModal');
                    modalsUI.openModal('ragModal');
                };
            }

            // Setup close button
            const closeButton = document.getElementById('closeRagTestFormModal');
            if (closeButton) {
                closeButton.onclick = () => {
                    modalsUI.closeModal('ragTestFormModal');
                    modalsUI.openModal('ragModal');
                };
            }
        } catch (error) {
            console.error('Error loading test for editing:', error);
            alert(`Ошибка загрузки теста: ${error.message}`);
        }
    }

    /**
     * Validate test content as JSON with required fields
     * @param {string} content - JSON content to validate
     * @returns {{valid: boolean, error?: string, lineNumber?: number}} Validation result
     */
    validateTestJSON(content) {
        // Required fields for each query element
        const requiredFields = ['id', 'query', 'explanation'];

        // First, try to parse as JSON
        let parsed;
        try {
            parsed = JSON.parse(content);
        } catch (e) {
            // Extract line number from JSON parse error if available
            const lineMatch = e.message.match(/position\s+(\d+)/i) ||
                              e.message.match(/line\s+(\d+)/i) ||
                              e.message.match(/at\s+(\d+)/i);

            let lineNumber = null;
            if (lineMatch) {
                // Try to calculate line number from position
                const position = parseInt(lineMatch[1], 10);
                const lines = content.substring(0, position).split('\n');
                lineNumber = lines.length;
            }

            return {
                valid: false,
                error: `Некорректный JSON: ${e.message}`,
                lineNumber: lineNumber
            };
        }

        // Find queries array (could be top-level array or nested under "queries" key)
        let queries = Array.isArray(parsed) ? parsed : parsed.queries;

        if (!queries) {
            return {
                valid: false,
                error: 'JSON должен содержать массив запросов (либо как массив, либо в поле "queries")'
            };
        }

        if (!Array.isArray(queries)) {
            return {
                valid: false,
                error: 'Поле "queries" должно быть массивом'
            };
        }

        if (queries.length === 0) {
            return {
                valid: false,
                error: 'Массив запросов не может быть пустым'
            };
        }

        // Validate each query element
        for (let i = 0; i < queries.length; i++) {
            const query = queries[i];
            const missingFields = [];

            for (const field of requiredFields) {
                if (!(field in query) || query[field] === null || query[field] === undefined) {
                    missingFields.push(field);
                } else if (typeof query[field] === 'string' && query[field].trim() === '') {
                    missingFields.push(`${field} (пустое значение)`);
                }
            }

            if (missingFields.length > 0) {
                // Try to find line number for this query in the JSON string
                let lineNumber = null;
                try {
                    // Search for this query's id in the original content
                    const queryId = query.id || `элемент ${i + 1}`;
                    const idPattern = new RegExp(`"id"\\s*:\\s*"${queryId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}"`, 'g');
                    const match = idPattern.exec(content);
                    if (match) {
                        const lines = content.substring(0, match.index).split('\n');
                        lineNumber = lines.length;
                    }
                } catch (e) {
                    // Ignore regex errors
                }

                return {
                    valid: false,
                    error: `Элемент #${i + 1}${query.id ? ` (id: "${query.id}")` : ''}: отсутствуют обязательные поля: ${missingFields.join(', ')}`,
                    lineNumber: lineNumber
                };
            }
        }

        return { valid: true };
    }

    /**
     * Show validation error in the UI
     * @param {string} error - Error message
     * @param {number|null} lineNumber - Line number where error occurred
     */
    showValidationError(error, lineNumber = null) {
        const errorDiv = document.getElementById('ragTestValidationError');
        if (!errorDiv) return;

        let html = `<div class="validation-error-title">Ошибка валидации JSON</div>`;
        if (lineNumber) {
            html += `<div class="validation-error-line">Строка: ${lineNumber}</div>`;
        }
        html += `<div>${error}</div>`;
        html += `<div class="validation-error-details">Обязательные поля для каждого запроса: id, query, explanation</div>`;

        errorDiv.innerHTML = html;
        errorDiv.classList.remove('hidden');
    }

    /**
     * Hide validation error
     */
    hideValidationError() {
        const errorDiv = document.getElementById('ragTestValidationError');
        if (errorDiv) {
            errorDiv.classList.add('hidden');
            errorDiv.innerHTML = '';
        }
    }

    /**
     * Handle adding a new test
     */
    async handleAddTest() {
        const nameInput = document.getElementById('ragTestName');
        const contentInput = document.getElementById('ragTestContent');

        const name = nameInput?.value?.trim();

        // Hide any previous validation errors
        this.hideValidationError();

        if (!name) {
            alert('Пожалуйста, укажите название теста');
            return;
        }

        try {
            let content = '';

            if (this.activeTestTab === 'text') {
                content = contentInput?.value?.trim() || '';
                if (!content) {
                    alert('Пожалуйста, введите содержимое теста');
                    return;
                }
            } else {
                // Files mode: read file content
                if (!this.selectedTestFile) {
                    alert('Пожалуйста, выберите файл');
                    return;
                }
                content = await this.readFileContent(this.selectedTestFile);
            }

            // Validate JSON content
            const validation = this.validateTestJSON(content);
            if (!validation.valid) {
                this.showValidationError(validation.error, validation.lineNumber);
                return;
            }

            // Parse JSON and extract queries and evaluationMetrics
            const parsed = JSON.parse(content);
            const queries = Array.isArray(parsed) ? parsed : parsed.queries;
            const evaluationMetrics = parsed.evaluationMetrics || null;

            console.log(`Adding test: ${name} with ${queries.length} queries`);
            await ragTestApi.addTest(name, queries, evaluationMetrics);
            console.log('Test added successfully');

            modalsUI.closeModal('ragTestFormModal');
            modalsUI.openModal('ragModal');
            await this.loadTests();

        } catch (error) {
            console.error('Error adding test:', error);
            if (error.message.startsWith('DUPLICATE_NAME:')) {
                alert(`Тест с именем "${name}" уже существует.\nВыберите другое имя.`);
            } else {
                alert(`Ошибка при добавлении теста: ${error.message}`);
            }
        }
    }

    /**
     * Handle updating an existing test
     */
    async handleUpdateTest() {
        if (!this.editingTest) return;

        const nameInput = document.getElementById('ragTestName');
        const contentInput = document.getElementById('ragTestContent');

        const name = nameInput?.value?.trim();
        const content = contentInput?.value?.trim();

        // Hide any previous validation errors
        this.hideValidationError();

        if (!name) {
            alert('Пожалуйста, укажите название теста');
            return;
        }

        // Validate JSON content if provided
        let queries = null;
        let evaluationMetrics = null;

        if (content) {
            const validation = this.validateTestJSON(content);
            if (!validation.valid) {
                this.showValidationError(validation.error, validation.lineNumber);
                return;
            }

            // Parse JSON and extract queries and evaluationMetrics
            const parsed = JSON.parse(content);
            queries = Array.isArray(parsed) ? parsed : parsed.queries;
            evaluationMetrics = parsed.evaluationMetrics || null;
        }

        try {
            console.log(`Updating test: ${this.editingTest.id}`);
            await ragTestApi.updateTest(this.editingTest.id, name, queries, evaluationMetrics);

            modalsUI.closeModal('ragTestFormModal');
            modalsUI.openModal('ragModal');
            await this.loadTests();

            console.log('Test updated successfully');
        } catch (error) {
            console.error('Error updating test:', error);
            if (error.message.startsWith('DUPLICATE_NAME:')) {
                alert(`Тест с именем "${name}" уже существует.\nВыберите другое имя.`);
            } else {
                alert(`Ошибка при обновлении теста: ${error.message}`);
            }
        }
    }

    /**
     * Handle deleting a test
     * @param {string} testId - Test ID to delete
     * @param {string} testName - Test name for confirmation
     */
    async handleDeleteTest(testId, testName) {
        if (!confirm(`Вы уверены, что хотите удалить тест "${testName}"?\nЭто действие нельзя отменить.`)) {
            return;
        }

        try {
            console.log(`Deleting test: ${testId}`);
            await ragTestApi.deleteTest(testId);
            await this.loadTests();
            console.log('Test deleted successfully');
        } catch (error) {
            console.error('Error deleting test:', error);
            alert(`Ошибка при удалении теста: ${error.message}`);
        }
    }

    /**
     * Setup test tab switching functionality
     */
    setupTestTabs() {
        const tabs = document.querySelectorAll('#ragTestSourceTabs .rag-tab');
        tabs.forEach(tab => {
            tab.onclick = () => {
                const tabName = tab.dataset.tab;
                this.switchTestTab(tabName);
            };
        });
    }

    /**
     * Switch between test text and files tabs
     * @param {string} tabName - Tab name ('text' or 'files')
     */
    switchTestTab(tabName) {
        this.activeTestTab = tabName;

        // Update tab buttons
        const tabs = document.querySelectorAll('#ragTestSourceTabs .rag-tab');
        tabs.forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tab === tabName);
        });

        // Update tab content
        const textContent = document.getElementById('ragTestTabText');
        const filesContent = document.getElementById('ragTestTabFiles');

        if (textContent) {
            textContent.classList.toggle('active', tabName === 'text');
        }
        if (filesContent) {
            filesContent.classList.toggle('active', tabName === 'files');
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
        const allowedExtensions = ['.txt', '.md', '.json', '.xml', '.log'];
        const ext = '.' + file.name.split('.').pop().toLowerCase();

        if (!allowedExtensions.includes(ext)) {
            alert(`Формат файла не поддерживается.\nПоддерживаемые форматы: ${allowedExtensions.join(', ')}`);
            return;
        }

        this.selectedTestFile = file;
        this.renderTestFilesList();

        // Also populate the content textarea with file content
        try {
            const content = await this.readFileContent(file);
            const contentInput = document.getElementById('ragTestContent');
            if (contentInput) {
                contentInput.value = content;
            }
        } catch (error) {
            console.error('Error reading file:', error);
        }
    }

    /**
     * Remove the selected test file
     */
    removeTestFile() {
        this.selectedTestFile = null;
        this.renderTestFilesList();
    }

    /**
     * Render the list of selected test file (only one)
     */
    renderTestFilesList() {
        const filesList = document.getElementById('ragTestFilesList');
        if (!filesList) return;

        filesList.innerHTML = '';

        if (!this.selectedTestFile) return;

        const file = this.selectedTestFile;
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
        fileSize.textContent = this.formatFileSize(file.size);

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
        removeButton.onclick = () => this.removeTestFile();

        fileItem.appendChild(fileIcon);
        fileItem.appendChild(fileInfo);
        fileItem.appendChild(removeButton);
        filesList.appendChild(fileItem);
    }

    // ===== Test Execution Methods =====

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
            onCompleted: (data) => {
                console.log('Query completed:', data);
                this.updateExecutionProgress(data.current, data.total, data.result.query);
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

        // Setup cancel button
        const cancelButton = document.getElementById('ragExecutionCancelButton');
        if (cancelButton) {
            cancelButton.onclick = () => {
                if (this.currentExecution) {
                    this.currentExecution.cancel();
                }
            };
        }

        // Setup close button
        const closeButton = document.getElementById('closeRagExecutionModal');
        if (closeButton) {
            closeButton.onclick = () => {
                // Check if execution is still in progress (not finished and not cancelled)
                if (this.currentExecution && !this.currentExecution.isComplete) {
                    if (confirm('Выполнение теста ещё не завершено. Вы уверены, что хотите закрыть окно и отменить выполнение?')) {
                        this.currentExecution.cancel();
                        this.stopExecutionTimer();
                        modalsUI.closeModal('ragTestExecutionModal');
                        modalsUI.openModal('ragModal');
                    }
                } else {
                    this.stopExecutionTimer();
                    modalsUI.closeModal('ragTestExecutionModal');
                    modalsUI.openModal('ragModal');
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
    }

    /**
     * Update execution progress display
     * @param {number} current - Current query index (1-based)
     * @param {number} total - Total number of queries
     * @param {string} query - Current query text
     */
    updateExecutionProgress(current, total, query) {
        // Update progress bar
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

        // Update progress to 100% (with defensive coding)
        const totalResults = result?.results?.length || 0;
        this.updateExecutionProgress(totalResults, totalResults, '-');

        // Show download button, hide cancel button
        const downloadButton = document.getElementById('ragExecutionDownloadButton');
        const cancelButton = document.getElementById('ragExecutionCancelButton');

        console.log('Button elements found:', { downloadButton: !!downloadButton, cancelButton: !!cancelButton });

        if (downloadButton) {
            downloadButton.classList.remove('hidden');
            downloadButton.onclick = () => this.downloadResults();
        }
        if (cancelButton) {
            cancelButton.classList.add('hidden');
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
                timerEl.textContent = this.formatTime(elapsed);
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

    /**
     * Format milliseconds as MM:SS
     * @param {number} ms - Milliseconds
     * @returns {string} Formatted time string
     */
    formatTime(ms) {
        const totalSeconds = Math.floor(ms / 1000);
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }

    /**
     * Format execution results as Markdown
     * Results include both without reranking and with reranking responses
     * @returns {string} Markdown formatted results
     */
    formatResultsAsMarkdown() {
        if (!this.executionResults) {
            return '';
        }

        const result = this.executionResults;
        const lines = [];

        // Header
        lines.push(`# RAG Test Results: ${result.testName}`);
        lines.push('');

        // Metadata
        lines.push('## Metadata');
        lines.push('');
        lines.push(`- **Test ID:** ${result.testId}`);
        lines.push(`- **Session ID:** ${result.sessionId}`);
        lines.push(`- **Provider:** ${result.provider}`);
        lines.push(`- **Model:** ${result.model}`);
        lines.push(`- **Executed At:** ${result.executedAt}`);
        lines.push(`- **Total Time:** ${result.totalTimeMs} ms`);
        lines.push(`- **Status:** ${result.cancelled ? 'Cancelled' : 'Completed'}`);
        lines.push('');

        // Summary statistics (aggregated for both modes)
        let totalTokensNoRerank = 0;
        let totalTokensRerank = 0;
        let totalChunksNoRerank = 0;
        let totalChunksRerank = 0;

        result.results.forEach(r => {
            if (r.withoutReranking) {
                totalTokensNoRerank += r.withoutReranking.tokensUsed || 0;
                totalChunksNoRerank += r.withoutReranking.chunksCount || 0;
            }
            if (r.withReranking) {
                totalTokensRerank += r.withReranking.tokensUsed || 0;
                totalChunksRerank += r.withReranking.chunksCount || 0;
            }
        });

        lines.push('## Summary');
        lines.push('');
        lines.push(`- **Total Queries:** ${result.results.length}`);
        lines.push('');
        lines.push('### Without Reranking');
        lines.push(`- **Total Tokens Used:** ${totalTokensNoRerank}`);
        lines.push(`- **Total Chunks Used:** ${totalChunksNoRerank}`);
        lines.push('');
        lines.push('### With Reranking');
        lines.push(`- **Total Tokens Used:** ${totalTokensRerank}`);
        lines.push(`- **Total Chunks Used:** ${totalChunksRerank}`);
        lines.push('');

        // Query Results
        lines.push('## Query Results');
        lines.push('');

        result.results.forEach((queryResult, index) => {
            lines.push(`### ${index + 1}. ${queryResult.queryId}`);
            lines.push('');
            lines.push('**Query:**');
            lines.push('');
            lines.push(`> ${queryResult.query}`);
            lines.push('');
            lines.push('**Explanation:**');
            lines.push('');
            lines.push(`> ${queryResult.explanation}`);
            lines.push('');

            if (queryResult.model) {
                lines.push(`**Model:** ${queryResult.model}`);
                lines.push('');
            }

            // Response WITHOUT Reranking
            lines.push('#### Response WITHOUT Reranking');
            lines.push('');
            if (queryResult.withoutReranking) {
                const noRerank = queryResult.withoutReranking;
                lines.push(`**Chunks Used:** ${noRerank.chunksCount}`);
                lines.push('');

                // Show chunk details if available
                if (noRerank.chunks && noRerank.chunks.length > 0) {
                    lines.push('<details>');
                    lines.push('<summary>Chunks Details</summary>');
                    lines.push('');
                    noRerank.chunks.forEach((chunk, i) => {
                        lines.push(`${i + 1}. **${chunk.documentName}** (chunk #${chunk.chunkIndex}, score: ${chunk.score.toFixed(3)})`);
                        lines.push(`   > ${chunk.text.substring(0, 200)}${chunk.text.length > 200 ? '...' : ''}`);
                        lines.push('');
                    });
                    lines.push('</details>');
                    lines.push('');
                }

                lines.push('**Response:**');
                lines.push('');
                lines.push('```');
                lines.push(noRerank.response);
                lines.push('```');
                lines.push('');
                lines.push('**Metrics:**');
                lines.push(`- Time: ${noRerank.elapsedTimeMs} ms`);
                if (noRerank.tokensUsed) lines.push(`- Tokens Used: ${noRerank.tokensUsed}`);
                if (noRerank.inputTokens) lines.push(`- Input Tokens: ${noRerank.inputTokens}`);
                if (noRerank.outputTokens) lines.push(`- Output Tokens: ${noRerank.outputTokens}`);
            } else {
                lines.push('*No data available*');
            }
            lines.push('');

            // Response WITH Reranking
            lines.push('#### Response WITH Reranking');
            lines.push('');
            if (queryResult.withReranking) {
                const rerank = queryResult.withReranking;
                lines.push(`**Chunks Used:** ${rerank.chunksCount}`);
                lines.push('');

                // Show chunk details if available
                if (rerank.chunks && rerank.chunks.length > 0) {
                    lines.push('<details>');
                    lines.push('<summary>Chunks Details</summary>');
                    lines.push('');
                    rerank.chunks.forEach((chunk, i) => {
                        lines.push(`${i + 1}. **${chunk.documentName}** (chunk #${chunk.chunkIndex}, score: ${chunk.score.toFixed(3)})`);
                        lines.push(`   > ${chunk.text.substring(0, 200)}${chunk.text.length > 200 ? '...' : ''}`);
                        lines.push('');
                    });
                    lines.push('</details>');
                    lines.push('');
                }

                lines.push('**Response:**');
                lines.push('');
                lines.push('```');
                lines.push(rerank.response);
                lines.push('```');
                lines.push('');
                lines.push('**Metrics:**');
                lines.push(`- Time: ${rerank.elapsedTimeMs} ms`);
                if (rerank.tokensUsed) lines.push(`- Tokens Used: ${rerank.tokensUsed}`);
                if (rerank.inputTokens) lines.push(`- Input Tokens: ${rerank.inputTokens}`);
                if (rerank.outputTokens) lines.push(`- Output Tokens: ${rerank.outputTokens}`);
            } else {
                lines.push('*No data available*');
            }
            lines.push('');
            lines.push('---');
            lines.push('');
        });

        return lines.join('\n');
    }

    /**
     * Download a single file
     * @param {string|Blob} content - File content (string or Blob)
     * @param {string} filename - File name
     * @param {string} mimeType - MIME type (ignored if content is Blob)
     */
    downloadFile(content, filename, mimeType) {
        const blob = content instanceof Blob ? content : new Blob([content], { type: mimeType });
        const url = URL.createObjectURL(blob);

        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        console.log(`Downloaded: ${filename}`);
    }

    /**
     * Download execution results as ZIP archive containing JSON and Markdown files
     */
    async downloadResults() {
        if (!this.executionResults) {
            console.error('No results to download');
            return;
        }

        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const baseFilename = `rag-test-results-${this.executionResults.testId}-${timestamp}`;

        // Prepare file contents
        const jsonContent = JSON.stringify(this.executionResults, null, 2);
        const mdContent = this.formatResultsAsMarkdown();

        // Create ZIP archive using JSZip
        if (typeof JSZip === 'undefined') {
            console.error('JSZip library not loaded, falling back to separate downloads');
            // Fallback to separate downloads
            this.downloadFile(jsonContent, `${baseFilename}.json`, 'application/json');
            setTimeout(() => {
                this.downloadFile(mdContent, `${baseFilename}.md`, 'text/markdown');
            }, 100);
            return;
        }

        try {
            const zip = new JSZip();

            // Add files to archive
            zip.file(`${baseFilename}.json`, jsonContent);
            zip.file(`${baseFilename}.md`, mdContent);

            // Generate ZIP and download
            const zipBlob = await zip.generateAsync({ type: 'blob' });
            const zipFilename = `${baseFilename}.zip`;

            this.downloadFile(zipBlob, zipFilename, 'application/zip');
            console.log(`Downloaded results archive: ${zipFilename}`);

        } catch (error) {
            console.error('Error creating ZIP archive:', error);
            // Fallback to separate downloads
            this.downloadFile(jsonContent, `${baseFilename}.json`, 'application/json');
            setTimeout(() => {
                this.downloadFile(mdContent, `${baseFilename}.md`, 'text/markdown');
            }, 100);
        }
    }
}

/**
 * Initialize RAG modal (called from main.js)
 */
export async function initializeRAGModal() {
    const ragModal = new RAGModal();
    await ragModal.initialize();
    return ragModal;
}
