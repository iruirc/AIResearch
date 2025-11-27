/**
 * @fileoverview RAG Test Renderer
 * Handles rendering of test list and forms
 * @module ui/rag/tests/TestRenderer
 */

import { escapeHtml, formatFileSize } from '../utils/RagUtils.js';

/**
 * Test Renderer class for rendering test UI
 */
export class TestRenderer {
    /**
     * Create a TestRenderer
     * @param {Object} callbacks - Event callbacks
     * @param {Function} callbacks.onAddTest - Called when add test is clicked
     * @param {Function} callbacks.onEditTest - Called when edit test is clicked
     * @param {Function} callbacks.onDeleteTest - Called when delete test is clicked
     * @param {Function} callbacks.onExecuteTest - Called when execute test is clicked
     */
    constructor(callbacks = {}) {
        this.callbacks = callbacks;
    }

    /**
     * Render the tests list view
     * @param {Array} tests - Array of test objects
     */
    renderTestsList(tests) {
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
        addButton.addEventListener('click', () => {
            if (this.callbacks.onAddTest) {
                this.callbacks.onAddTest();
            }
        });
        ragTestsList.appendChild(addButton);

        // Show empty state if no tests
        if (!tests || tests.length === 0) {
            const emptyMessage = document.createElement('div');
            emptyMessage.className = 'rag-tests-empty';
            emptyMessage.textContent = 'Нет тестов. Добавьте тест для проверки качества RAG.';
            ragTestsList.appendChild(emptyMessage);
            return;
        }

        // Render each test
        tests.forEach(test => {
            const testItem = this.createTestItem(test);
            ragTestsList.appendChild(testItem);
        });
    }

    /**
     * Create a test item element
     * @param {Object} test - Test object
     * @returns {HTMLElement} Test item element
     */
    createTestItem(test) {
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
        const actionsDiv = this.createTestActions(test);

        testItem.appendChild(contentDiv);
        testItem.appendChild(actionsDiv);

        return testItem;
    }

    /**
     * Create test action buttons
     * @param {Object} test - Test object
     * @returns {HTMLElement} Actions div element
     */
    createTestActions(test) {
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'rag-test-actions';

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
            if (this.callbacks.onExecuteTest) {
                await this.callbacks.onExecuteTest(test);
            }
        });

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
            if (this.callbacks.onEditTest) {
                await this.callbacks.onEditTest(test.id);
            }
        });

        // Delete button
        const deleteButton = document.createElement('button');
        deleteButton.className = 'rag-test-action-button delete-button';
        deleteButton.title = 'Удалить';
        deleteButton.textContent = '🗑️';
        deleteButton.addEventListener('click', async (e) => {
            e.stopPropagation();
            if (this.callbacks.onDeleteTest) {
                await this.callbacks.onDeleteTest(test.id, test.name);
            }
        });

        actionsDiv.appendChild(executeButton);
        actionsDiv.appendChild(editButton);
        actionsDiv.appendChild(deleteButton);

        return actionsDiv;
    }

    /**
     * Show loading state in tests list
     */
    showLoading() {
        const ragTestsList = document.getElementById('ragTestsList');
        if (ragTestsList) {
            ragTestsList.innerHTML = '<div class="rag-tests-loading">Загрузка тестов...</div>';
        }
    }

    /**
     * Show error state in tests list
     * @param {string} message - Error message
     */
    showError(message) {
        const ragTestsList = document.getElementById('ragTestsList');
        if (ragTestsList) {
            ragTestsList.innerHTML = `<div class="rag-tests-error">Ошибка загрузки: ${message}</div>`;
        }
    }

    /**
     * Render the list of selected test file (only one)
     * @param {File|null} selectedFile - Selected file object
     * @param {Function} onRemove - Callback when remove is clicked
     */
    renderTestFilesList(selectedFile, onRemove) {
        const filesList = document.getElementById('ragTestFilesList');
        if (!filesList) return;

        filesList.innerHTML = '';

        if (!selectedFile) return;

        const file = selectedFile;
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
        removeButton.onclick = onRemove;

        fileItem.appendChild(fileIcon);
        fileItem.appendChild(fileInfo);
        fileItem.appendChild(removeButton);
        filesList.appendChild(fileItem);
    }

    /**
     * Switch between test text and files tabs
     * @param {string} tabName - Tab name ('text' or 'files')
     */
    switchTestTab(tabName) {
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
     * Setup form for add mode
     * @param {Object} options - Form options
     * @param {Function} options.onSubmit - Submit callback
     * @param {Function} options.onCancel - Cancel callback
     */
    setupAddForm(options = {}) {
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

        // Show tabs
        const tabsContainer = document.getElementById('ragTestSourceTabs');
        if (tabsContainer) {
            tabsContainer.classList.remove('hidden');
        }

        // Setup form submission
        const form = document.getElementById('ragTestForm');
        if (form && options.onSubmit) {
            form.onsubmit = async (e) => {
                e.preventDefault();
                await options.onSubmit();
            };
        }

        this.setupFormButtons(options.onCancel);
    }

    /**
     * Setup form for edit mode
     * @param {Object} test - Test object to edit
     * @param {Object} options - Form options
     * @param {Function} options.onSubmit - Submit callback
     * @param {Function} options.onCancel - Cancel callback
     */
    setupEditForm(test, options = {}) {
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
        if (form && options.onSubmit) {
            form.onsubmit = async (e) => {
                e.preventDefault();
                await options.onSubmit();
            };
        }

        this.setupFormButtons(options.onCancel);
    }

    /**
     * Setup form cancel and close buttons
     * @param {Function} onCancel - Cancel callback
     */
    setupFormButtons(onCancel) {
        // Setup cancel button
        const cancelButton = document.getElementById('cancelRagTestFormButton');
        if (cancelButton && onCancel) {
            cancelButton.onclick = onCancel;
        }

        // Setup close button
        const closeButton = document.getElementById('closeRagTestFormModal');
        if (closeButton && onCancel) {
            closeButton.onclick = onCancel;
        }
    }

    /**
     * Get form values
     * @returns {{name: string, content: string}} Form values
     */
    getFormValues() {
        const nameInput = document.getElementById('ragTestName');
        const contentInput = document.getElementById('ragTestContent');

        return {
            name: nameInput?.value?.trim() || '',
            content: contentInput?.value?.trim() || ''
        };
    }
}
