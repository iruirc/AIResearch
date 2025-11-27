/**
 * @fileoverview RAG Document Renderer
 * Handles rendering of document list and forms
 * @module ui/rag/documents/DocumentRenderer
 */

import { formatFileSize } from '../utils/RagUtils.js';

/**
 * Document Renderer class for rendering document UI
 */
export class DocumentRenderer {
    /**
     * Create a DocumentRenderer
     * @param {Object} callbacks - Event callbacks
     * @param {Function} callbacks.onAddDocument - Callback when add button is clicked
     * @param {Function} callbacks.onEditDocument - Callback when edit button is clicked
     * @param {Function} callbacks.onDeleteDocument - Callback when delete button is clicked
     * @param {Function} callbacks.onToggleDocument - Callback when toggle is clicked
     */
    constructor(callbacks = {}) {
        this.callbacks = callbacks;
        // Backward compatibility
        this.onAddClick = callbacks.onAddDocument || null;
        this.onEditClick = callbacks.onEditDocument || null;
        this.onDeleteClick = callbacks.onDeleteDocument || null;
        this.onToggleClick = callbacks.onToggleDocument || null;
    }

    /**
     * Show loading state in documents list
     */
    showLoading() {
        const ragDocumentsList = document.getElementById('ragDocumentsList');
        if (ragDocumentsList) {
            ragDocumentsList.innerHTML = '<div class="rag-documents-loading">Загрузка документов...</div>';
        }
    }

    /**
     * Show error state in documents list
     * @param {string} message - Error message
     */
    showError(message) {
        const ragDocumentsList = document.getElementById('ragDocumentsList');
        if (ragDocumentsList) {
            ragDocumentsList.innerHTML = `<div class="rag-documents-error">Ошибка загрузки: ${message}</div>`;
        }
    }

    /**
     * Render the documents list view
     * @param {Array} documents - Documents array to render
     */
    renderDocumentsList(documents) {
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
        addButton.addEventListener('click', () => {
            if (this.onAddClick) {
                this.onAddClick();
            }
        });
        ragDocumentsList.appendChild(addButton);

        // Show empty state if no documents
        if (!documents || documents.length === 0) {
            const emptyMessage = document.createElement('div');
            emptyMessage.className = 'rag-documents-empty';
            emptyMessage.textContent = 'Нет документов. Добавьте документ для использования RAG.';
            ragDocumentsList.appendChild(emptyMessage);
            return;
        }

        // Render each document
        documents.forEach(doc => {
            const docItem = this.createDocumentItem(doc);
            ragDocumentsList.appendChild(docItem);
        });
    }

    /**
     * Create a document item element
     * @param {Object} doc - Document object
     * @returns {HTMLElement} Document item element
     */
    createDocumentItem(doc) {
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
            if (this.onToggleClick) {
                await this.onToggleClick(doc.id, e.target.checked);
            }
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
            if (this.onEditClick) {
                await this.onEditClick(doc.id);
            }
        });

        // Delete button
        const deleteButton = document.createElement('button');
        deleteButton.className = 'rag-action-button delete-button';
        deleteButton.title = 'Удалить';
        deleteButton.textContent = '🗑️';
        deleteButton.addEventListener('click', async (e) => {
            e.stopPropagation();
            if (this.onDeleteClick) {
                await this.onDeleteClick(doc.id, doc.name);
            }
        });

        // Order: Toggle, Edit, Delete (right to left means we add in this order)
        actionsDiv.appendChild(toggleSwitch);
        actionsDiv.appendChild(editButton);
        actionsDiv.appendChild(deleteButton);

        docItem.appendChild(contentDiv);
        docItem.appendChild(actionsDiv);

        return docItem;
    }

    /**
     * Setup add document form
     * @param {Object} options - Form options
     * @param {Function} options.onSubmit - Form submit callback
     * @param {Function} options.onCancel - Cancel button callback
     */
    setupAddForm(options = {}) {
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

        // Show tabs
        const tabsContainer = document.getElementById('ragSourceTabs');
        if (tabsContainer) {
            tabsContainer.classList.remove('hidden');
        }

        // Setup form submission
        const form = document.getElementById('ragDocumentForm');
        if (form && options.onSubmit) {
            form.onsubmit = async (e) => {
                e.preventDefault();
                await options.onSubmit();
            };
        }

        // Setup cancel button
        const cancelButton = document.getElementById('cancelRagFormButton');
        if (cancelButton && options.onCancel) {
            cancelButton.onclick = options.onCancel;
        }
    }

    /**
     * Setup edit document form
     * @param {Object} doc - Document to edit
     * @param {Object} options - Form options
     * @param {Function} options.onSubmit - Form submit callback
     * @param {Function} options.onCancel - Cancel button callback
     */
    setupEditForm(doc, options = {}) {
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
        if (form && options.onSubmit) {
            form.onsubmit = async (e) => {
                e.preventDefault();
                await options.onSubmit();
            };
        }

        // Setup cancel button
        const cancelButton = document.getElementById('cancelRagFormButton');
        if (cancelButton && options.onCancel) {
            cancelButton.onclick = options.onCancel;
        }
    }

    /**
     * Get form values
     * @returns {Object} Form values
     */
    getFormValues() {
        const nameInput = document.getElementById('ragDocumentName');
        const contentInput = document.getElementById('ragDocumentContent');
        const strategySelect = document.getElementById('ragChunkingStrategy');
        const enabledCheckbox = document.getElementById('ragDocumentEnabled');

        return {
            name: nameInput?.value?.trim() || '',
            content: contentInput?.value?.trim() || '',
            strategy: strategySelect?.value || 'FIXED_SIZE',
            enabled: enabledCheckbox?.checked !== false
        };
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

    /**
     * Switch between document tabs
     * @param {string} tabName - Tab name ('text' or 'files')
     */
    switchDocTab(tabName) {
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
    }

    /**
     * Render the list of selected files
     * @param {File[]} files - Array of selected files
     * @param {Function} onRemove - Callback when remove is clicked
     */
    renderFilesList(files, onRemove) {
        const filesList = document.getElementById('ragFilesList');
        if (!filesList) return;

        filesList.innerHTML = '';

        if (!files || files.length === 0) return;

        files.forEach((file, index) => {
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

            const fileSizeEl = document.createElement('div');
            fileSizeEl.className = 'rag-file-size';
            fileSizeEl.textContent = formatFileSize(file.size);

            fileInfo.appendChild(fileName);
            fileInfo.appendChild(fileSizeEl);

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
            if (onRemove) {
                removeButton.onclick = () => onRemove(file);
            }

            fileItem.appendChild(fileIcon);
            fileItem.appendChild(fileInfo);
            fileItem.appendChild(removeButton);
            filesList.appendChild(fileItem);
        });
    }
}
