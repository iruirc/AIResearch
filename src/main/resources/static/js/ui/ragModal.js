/**
 * @fileoverview RAG Document Management Modal
 * Handles UI for RAG document CRUD operations
 * @module ui/ragModal
 */

import { ragApi } from '../api/ragApi.js';
import { modalsUI } from './modalsUI.js';

/**
 * RAG Modal class for managing document operations
 */
export class RAGModal {
    constructor() {
        this.documents = [];
        this.currentMode = 'list'; // 'list', 'add', 'edit'
        this.editingDocument = null;

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
                <span>Чанков: ${doc.chunkCount || 0}</span>
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
        if (contentInput) contentInput.value = '';
        if (strategySelect) strategySelect.value = 'FIXED_SIZE';
        if (enabledCheckbox) enabledCheckbox.checked = true;

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
     * Open the edit document form
     * @param {string} documentId - Document ID to edit
     */
    async openEditDocumentForm(documentId) {
        try {
            this.currentMode = 'edit';

            // Load full document details
            const document = await ragApi.getDocument(documentId);
            this.editingDocument = document;

            modalsUI.closeModal('ragModal');
            modalsUI.openModal('ragFormModal');

            // Set form title
            const formTitle = document.getElementById('ragFormTitle');
            if (formTitle) {
                formTitle.textContent = 'Редактировать документ';
            }

            // Fill form with document data
            const nameInput = document.getElementById('ragDocumentName');
            const contentInput = document.getElementById('ragDocumentContent');
            const strategySelect = document.getElementById('ragChunkingStrategy');
            const enabledCheckbox = document.getElementById('ragDocumentEnabled');

            if (nameInput) nameInput.value = document.name || '';
            if (contentInput) contentInput.value = document.originalContent || '';
            if (strategySelect) strategySelect.value = document.chunkingStrategy || 'FIXED_SIZE';
            if (enabledCheckbox) enabledCheckbox.checked = document.enabled !== false;

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

        const name = nameInput?.value?.trim();
        const content = contentInput?.value?.trim();
        const strategy = strategySelect?.value || 'FIXED_SIZE';
        const enabled = enabledCheckbox?.checked !== false;

        if (!name || !content) {
            alert('Пожалуйста, заполните название и содержимое документа');
            return;
        }

        try {
            console.log(`Adding document: ${name}`);
            await ragApi.addDocument(name, content, strategy, enabled);

            modalsUI.closeModal('ragFormModal');
            modalsUI.openModal('ragModal');
            await this.loadDocuments();

            console.log('Document added successfully');
        } catch (error) {
            console.error('Error adding document:', error);
            alert(`Ошибка при добавлении документа: ${error.message}`);
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
            alert(`Ошибка при обновлении документа: ${error.message}`);
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
}

/**
 * Initialize RAG modal (called from main.js)
 */
export async function initializeRAGModal() {
    const ragModal = new RAGModal();
    await ragModal.initialize();
    return ragModal;
}
