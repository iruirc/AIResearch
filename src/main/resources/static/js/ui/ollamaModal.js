/**
 * @fileoverview Ollama Modal UI module
 * Handles Ollama connection management interface
 * @module ui/ollamaModal
 */

import { ollamaService } from '../services/ollamaService.js';
import { appState } from '../state/appState.js';

/**
 * Current connection being edited (null for create mode)
 * @type {Object|null}
 */
let currentConnection = null;

/**
 * Testing status cache
 * @type {Map<string, {testing: boolean, result: Object|null}>}
 */
const testingStatus = new Map();

/**
 * Initialize Ollama modal
 * @returns {Promise<void>}
 */
export async function initializeOllamaModal() {
    console.log('🔌 Initializing Ollama modal...');

    try {
        // Setup event listeners
        setupEventListeners();

        // Subscribe to state changes
        appState.subscribe('ollamaConnections', renderConnectionsList);

        console.log('✅ Ollama modal initialized');
    } catch (error) {
        console.error('❌ Failed to initialize Ollama modal:', error);
    }
}

/**
 * Setup event listeners for modal controls
 */
function setupEventListeners() {
    // Open modal button
    const ollamaButton = document.getElementById('ollamaButton');
    if (ollamaButton) {
        ollamaButton.addEventListener('click', openOllamaModal);
    }

    // Close buttons
    const closeOllamaModal = document.getElementById('closeOllamaModal');
    if (closeOllamaModal) {
        closeOllamaModal.addEventListener('click', closeModal);
    }

    const closeOllamaFormModal = document.getElementById('closeOllamaFormModal');
    if (closeOllamaFormModal) {
        closeOllamaFormModal.addEventListener('click', closeFormModal);
    }

    // Cancel buttons
    const cancelOllamaFormButton = document.getElementById('cancelOllamaFormButton');
    if (cancelOllamaFormButton) {
        cancelOllamaFormButton.addEventListener('click', closeFormModal);
    }

    // Form submit
    const ollamaConnectionForm = document.getElementById('ollamaConnectionForm');
    if (ollamaConnectionForm) {
        ollamaConnectionForm.addEventListener('submit', handleFormSubmit);
    }

    // Test connection button
    const testConnectionButton = document.getElementById('testConnectionButton');
    if (testConnectionButton) {
        testConnectionButton.addEventListener('click', handleTestConnection);
    }

    // Click outside to close
    // Track where mousedown started to prevent accidental closes during text selection
    const modal = document.getElementById('ollamaModal');
    if (modal) {
        let modalMouseDownTarget = null;

        modal.addEventListener('mousedown', (e) => {
            modalMouseDownTarget = e.target;
        });

        modal.addEventListener('click', (e) => {
            // Only close if both mousedown and click happened on the modal backdrop
            if (e.target === modal && modalMouseDownTarget === modal) {
                closeModal();
            }
            modalMouseDownTarget = null;
        });
    }

    const formModal = document.getElementById('ollamaFormModal');
    if (formModal) {
        let formModalMouseDownTarget = null;

        formModal.addEventListener('mousedown', (e) => {
            formModalMouseDownTarget = e.target;
        });

        formModal.addEventListener('click', (e) => {
            // Only close if both mousedown and click happened on the modal backdrop
            if (e.target === formModal && formModalMouseDownTarget === formModal) {
                closeFormModal();
            }
            formModalMouseDownTarget = null;
        });
    }
}

/**
 * Open main Ollama connections modal
 */
async function openOllamaModal() {
    try {
        console.log('📋 Opening Ollama modal...');

        // Load connections from service
        await ollamaService.loadConnections();

        // Render connections list
        renderConnectionsList(appState.ollamaConnections);

        // Show modal
        const modal = document.getElementById('ollamaModal');
        if (modal) {
            modal.classList.add('active');
        }

        console.log('✅ Ollama modal opened');
    } catch (error) {
        console.error('❌ Failed to open Ollama modal:', error);
        showError('Ошибка загрузки подключений: ' + error.message);
    }
}

/**
 * Close main modal
 */
function closeModal() {
    const modal = document.getElementById('ollamaModal');
    if (modal) {
        modal.classList.remove('active');
    }
}

/**
 * Close form modal
 */
function closeFormModal() {
    const modal = document.getElementById('ollamaFormModal');
    if (modal) {
        modal.classList.remove('active');
    }

    // Reset form
    const form = document.getElementById('ollamaConnectionForm');
    if (form) {
        form.reset();
    }

    // Clear test result
    clearTestResult();

    // Reset current connection
    currentConnection = null;

    // Reopen LLM model modal on the Ollama tab
    const llmModelModal = document.getElementById('llmModelModal');
    if (llmModelModal) {
        llmModelModal.classList.add('active');
        // Switch to add-model tab
        import('./llmModelModal.js').then(module => {
            module.switchTab('add-model');
        });
    }
}

/**
 * Render connections list in modal
 * @param {Array} connections - Array of connection objects
 */
function renderConnectionsList(connections) {
    const listElement = document.getElementById('ollamaConnectionsList');
    if (!listElement) return;

    listElement.innerHTML = '';

    // Create connection button
    const createButton = document.createElement('button');
    createButton.className = 'create-connection-button';
    createButton.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>Добавить подключение</span>
    `;
    createButton.addEventListener('click', () => openFormModal(null));
    listElement.appendChild(createButton);

    // Connection items
    if (connections.length === 0) {
        const emptyState = document.createElement('div');
        emptyState.className = 'empty-state';
        emptyState.textContent = 'Нет подключений. Создайте первое подключение для работы с Ollama.';
        listElement.appendChild(emptyState);
        return;
    }

    const activeConnectionId = appState.activeOllamaConnectionId;

    connections.forEach(connection => {
        const item = createConnectionItem(connection, activeConnectionId);
        listElement.appendChild(item);
    });
}

/**
 * Create connection item element
 * @param {Object} connection - Connection object
 * @param {string|null} activeConnectionId - Active connection ID
 * @returns {HTMLElement}
 */
function createConnectionItem(connection, activeConnectionId) {
    const item = document.createElement('div');
    item.className = 'connection-item';

    if (connection.id === activeConnectionId) {
        item.classList.add('active');
    }

    const contentDiv = document.createElement('div');
    contentDiv.className = 'connection-item-content';

    const nameDiv = document.createElement('div');
    nameDiv.className = 'connection-name';
    nameDiv.textContent = connection.name;

    const statusDiv = document.createElement('div');
    statusDiv.className = 'connection-status';
    const statusLabel = document.createElement('strong');
    statusLabel.textContent = 'Статус: ';
    statusDiv.appendChild(statusLabel);
    if (connection.id === activeConnectionId) {
        statusDiv.appendChild(document.createTextNode('Активно'));
    } else {
        statusDiv.appendChild(document.createTextNode('Неактивно'));
    }

    const urlDiv = document.createElement('div');
    urlDiv.className = 'connection-url';
    const urlLabel = document.createElement('strong');
    urlLabel.textContent = 'URL: ';
    urlDiv.appendChild(urlLabel);
    urlDiv.appendChild(document.createTextNode(connection.baseUrl));

    const metaDiv = document.createElement('div');
    metaDiv.className = 'connection-meta';
    const metaLabel = document.createElement('strong');
    metaLabel.textContent = 'Keep-alive: ';
    metaDiv.appendChild(metaLabel);
    metaDiv.appendChild(document.createTextNode(connection.keepAlive));

    contentDiv.appendChild(nameDiv);
    contentDiv.appendChild(statusDiv);
    contentDiv.appendChild(urlDiv);
    contentDiv.appendChild(metaDiv);

    const actionsDiv = document.createElement('div');
    actionsDiv.className = 'connection-item-actions';

    // Activate button (only if not already active)
    if (connection.id !== activeConnectionId) {
        const activateButton = document.createElement('button');
        activateButton.className = 'connection-action-button activate-button';
        activateButton.title = 'Активировать';
        activateButton.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
                <path d="M12 6v6l4 2" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
        `;
        activateButton.addEventListener('click', (e) => {
            e.stopPropagation();
            handleActivateConnection(connection.id);
        });
        actionsDiv.appendChild(activateButton);
    }

    // Test button
    const testButton = document.createElement('button');
    testButton.className = 'connection-action-button test-button';
    testButton.title = 'Проверить подключение';
    testButton.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <polyline points="22 4 12 14.01 9 11.01" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    `;
    testButton.addEventListener('click', (e) => {
        e.stopPropagation();
        handleTestConnectionInline(connection.id, testButton);
    });
    actionsDiv.appendChild(testButton);

    // Edit button
    const editButton = document.createElement('button');
    editButton.className = 'connection-action-button edit-button';
    editButton.title = 'Редактировать';
    editButton.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    `;
    editButton.addEventListener('click', (e) => {
        e.stopPropagation();
        openFormModal(connection);
    });
    actionsDiv.appendChild(editButton);

    // Delete button (only if not active)
    if (connection.id !== activeConnectionId) {
        const deleteButton = document.createElement('button');
        deleteButton.className = 'connection-action-button delete-button';
        deleteButton.title = 'Удалить';
        deleteButton.textContent = '🗑️';
        deleteButton.addEventListener('click', (e) => {
            e.stopPropagation();
            handleDeleteConnection(connection.id, connection.name);
        });
        actionsDiv.appendChild(deleteButton);
    }

    item.appendChild(contentDiv);
    item.appendChild(actionsDiv);

    return item;
}

/**
 * Open form modal for create or edit
 * @param {Object|null} connection - Connection to edit (null for create)
 */
export function openFormModal(connection) {
    currentConnection = connection;

    const modal = document.getElementById('ollamaFormModal');
    const form = document.getElementById('ollamaConnectionForm');
    const title = document.getElementById('ollamaFormTitle');
    const submitButton = form.querySelector('button[type="submit"]');

    if (connection) {
        // Edit mode
        title.textContent = 'Редактировать подключение';
        submitButton.textContent = 'Сохранить';

        // Fill form
        document.getElementById('ollamaConnectionName').value = connection.name;
        document.getElementById('ollamaConnectionUrl').value = connection.baseUrl;
        document.getElementById('ollamaConnectionKeepAlive').value = connection.keepAlive || '5m';
    } else {
        // Create mode
        title.textContent = 'Новое подключение';
        submitButton.textContent = 'Создать';

        // Set defaults
        form.reset();
        document.getElementById('ollamaConnectionUrl').value = 'http://127.0.0.1:11434';
        document.getElementById('ollamaConnectionKeepAlive').value = '5m';
    }

    // Clear test result
    clearTestResult();

    // Show modal
    modal.classList.add('active');
}

/**
 * Handle form submission (create or update)
 * @param {Event} e - Submit event
 */
async function handleFormSubmit(e) {
    e.preventDefault();

    const form = e.target;
    const submitButton = form.querySelector('button[type="submit"]');
    const originalText = submitButton.textContent;

    try {
        // Get form data
        const formData = {
            name: document.getElementById('ollamaConnectionName').value.trim(),
            baseUrl: document.getElementById('ollamaConnectionUrl').value.trim(),
            keepAlive: document.getElementById('ollamaConnectionKeepAlive').value.trim() || '5m'
        };

        // Show loading state
        submitButton.disabled = true;
        submitButton.textContent = currentConnection ? 'Сохранение...' : 'Создание...';

        if (currentConnection) {
            // Update existing connection
            await ollamaService.updateConnection(currentConnection.id, formData);
            showSuccess('Подключение обновлено успешно');
        } else {
            // Create new connection
            await ollamaService.createConnection(formData);
            showSuccess('Подключение создано успешно');
        }

        // Close form modal and reopen settings
        closeFormModal();

    } catch (error) {
        console.error('Failed to save connection:', error);
        showError(error.message || 'Ошибка при сохранении подключения');
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = originalText;
    }
}

/**
 * Handle connection testing from form
 */
async function handleTestConnection() {
    const button = document.getElementById('testConnectionButton');
    const resultDiv = document.getElementById('testConnectionResult');

    try {
        // Get form data
        const baseUrl = document.getElementById('ollamaConnectionUrl').value.trim();

        if (!baseUrl) {
            showTestResult(false, 'Укажите URL подключения');
            return;
        }

        // Show loading
        button.disabled = true;
        button.textContent = 'Проверка...';
        resultDiv.className = 'test-result';
        resultDiv.textContent = 'Проверка подключения...';
        resultDiv.style.display = 'block';

        // For new connections, we need to test the URL directly
        // This is a simplified test - just check if URL is valid
        if (!currentConnection) {
            // Validate URL format
            if (!baseUrl.startsWith('http://') && !baseUrl.startsWith('https://')) {
                throw new Error('URL должен начинаться с http:// или https://');
            }

            // Simple validation - in real scenario, would call a test endpoint
            showTestResult(true, 'URL выглядит корректно. Сохраните подключение для полной проверки.');
        } else {
            // Test existing connection
            const result = await ollamaService.testConnection(currentConnection.id);
            showTestResult(result.success, result.message);
        }

    } catch (error) {
        showTestResult(false, error.message || 'Ошибка при проверке подключения');
    } finally {
        button.disabled = false;
        button.textContent = 'Проверить подключение';
    }
}

/**
 * Handle inline connection testing from list
 * @param {string} connectionId - Connection ID
 * @param {HTMLElement} button - Test button element
 */
async function handleTestConnectionInline(connectionId, button) {
    try {
        // Show loading
        const originalHTML = button.innerHTML;
        button.disabled = true;
        button.innerHTML = '⏳';

        // Test connection
        const result = await ollamaService.testConnection(connectionId);

        // Show result
        if (result.success) {
            button.innerHTML = '✅';
            setTimeout(() => {
                button.innerHTML = originalHTML;
                button.disabled = false;
            }, 2000);
        } else {
            button.innerHTML = '❌';
            showError('Ошибка: ' + result.message);
            setTimeout(() => {
                button.innerHTML = originalHTML;
                button.disabled = false;
            }, 2000);
        }

    } catch (error) {
        console.error('Test failed:', error);
        showError('Ошибка при проверке подключения: ' + error.message);
        button.disabled = false;
    }
}

/**
 * Handle connection activation
 * @param {string} connectionId - Connection ID
 */
async function handleActivateConnection(connectionId) {
    try {
        await ollamaService.activateConnection(connectionId);
        showSuccess('Подключение активировано');

        // Re-render list to show new active connection
        renderConnectionsList(appState.ollamaConnections);

    } catch (error) {
        console.error('Activation failed:', error);
        showError('Ошибка активации: ' + error.message);
    }
}

/**
 * Handle connection deletion
 * @param {string} connectionId - Connection ID
 * @param {string} connectionName - Connection name
 */
async function handleDeleteConnection(connectionId, connectionName) {
    if (!confirm(`Вы уверены, что хотите удалить подключение "${connectionName}"?`)) {
        return;
    }

    try {
        await ollamaService.deleteConnection(connectionId);
        showSuccess('Подключение удалено');

        // Re-render list
        renderConnectionsList(appState.ollamaConnections);

    } catch (error) {
        console.error('Deletion failed:', error);
        showError('Ошибка удаления: ' + error.message);
    }
}

/**
 * Show test result in form
 * @param {boolean} success - Test success status
 * @param {string} message - Result message
 */
function showTestResult(success, message) {
    const resultDiv = document.getElementById('testConnectionResult');
    if (!resultDiv) return;

    resultDiv.className = 'test-result ' + (success ? 'success' : 'error');
    resultDiv.textContent = message;
    resultDiv.style.display = 'block';
}

/**
 * Clear test result
 */
function clearTestResult() {
    const resultDiv = document.getElementById('testConnectionResult');
    if (resultDiv) {
        resultDiv.style.display = 'none';
        resultDiv.textContent = '';
    }
}

/**
 * Show success message
 * @param {string} message - Success message
 */
function showSuccess(message) {
    // Could use a toast/notification system here
    console.log('✅ Success:', message);
    alert(message);
}

/**
 * Show error message
 * @param {string} message - Error message
 */
function showError(message) {
    console.error('❌ Error:', message);
    alert(message);
}
