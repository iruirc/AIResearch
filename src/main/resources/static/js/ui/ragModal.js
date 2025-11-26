/**
 * @fileoverview RAG Document Management Modal
 * Handles UI for RAG document CRUD operations
 * @module ui/ragModal
 */

import { ragApi } from '../api/ragApi.js';
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
        this.activeModalTab = 'documents'; // 'documents' or 'settings'
        this.searchPreferences = null; // Cached search preferences

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
     * @param {string} tabName - Tab name ('documents' or 'settings')
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

        if (documentsTab) {
            documentsTab.classList.toggle('active', tabName === 'documents');
        }
        if (settingsTab) {
            settingsTab.classList.toggle('active', tabName === 'settings');
        }

        // Load data for the active tab
        if (tabName === 'settings') {
            await this.loadSearchPreferences();
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
                searchMinScore: parseFloat(document.getElementById('ragSearchMinScore')?.value) || 0.7
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
}

/**
 * Initialize RAG modal (called from main.js)
 */
export async function initializeRAGModal() {
    const ragModal = new RAGModal();
    await ragModal.initialize();
    return ragModal;
}
