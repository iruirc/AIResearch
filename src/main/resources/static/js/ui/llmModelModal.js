/**
 * @fileoverview LLM Model Modal UI module with tabs
 * Handles LLM model modal with Model Selection and Add Model (Ollama) tabs
 * @module ui/llmModelModal
 */

import { ollamaService } from '../services/ollamaService.js';
import { appState } from '../state/appState.js';
import { openFormModal } from './ollamaModal.js';

/**
 * Current active tab
 * @type {string}
 */
let currentTab = 'model-selection';

/**
 * Initialize LLM model modal with tabs
 * @returns {Promise<void>}
 */
export async function initializeLlmModelModal() {
    console.log('⚙️ Initializing LLM model modal...');

    try {
        // Setup tab event listeners
        setupTabEventListeners();

        // Subscribe to Ollama state changes
        appState.subscribe('ollamaConnections', renderOllamaTab);

        console.log('✅ LLM model modal initialized');
    } catch (error) {
        console.error('❌ Failed to initialize LLM model modal:', error);
    }
}

/**
 * Setup tab event listeners
 */
function setupTabEventListeners() {
    const tabs = document.querySelectorAll('.llm-model-tab');

    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const tabName = tab.dataset.tab;
            switchTab(tabName);
        });
    });
}

/**
 * Switch to a different tab
 * @param {string} tabName - Tab name (e.g., 'model-selection', 'add-model')
 */
export function switchTab(tabName) {
    // Update current tab
    currentTab = tabName;

    // Update tab buttons
    const tabs = document.querySelectorAll('.llm-model-tab');
    tabs.forEach(tab => {
        if (tab.dataset.tab === tabName) {
            tab.classList.add('active');
        } else {
            tab.classList.remove('active');
        }
    });

    // Update tab content
    const tabContents = document.querySelectorAll('.llm-model-tab-content');
    tabContents.forEach(content => {
        if (content.id === `tab-${tabName}`) {
            content.classList.add('active');
        } else {
            content.classList.remove('active');
        }
    });

    // Load data for the tab if needed
    if (tabName === 'add-model') {
        loadOllamaTab();
    }
}

/**
 * Load Ollama tab data
 */
async function loadOllamaTab() {
    try {
        // Load connections from service
        await ollamaService.loadConnections();

        // Render connections list
        renderOllamaTab(appState.ollamaConnections);
    } catch (error) {
        console.error('❌ Failed to load Ollama tab:', error);
        showError('Ошибка загрузки подключений: ' + error.message);
    }
}

/**
 * Render Ollama tab content
 * @param {Array} connections - Array of connection objects
 */
function renderOllamaTab(connections) {
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
    createButton.addEventListener('click', () => openOllamaFormModal(null));
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
        openOllamaFormModal(connection);
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
 * Open Ollama form modal for create or edit (wrapper)
 * @param {Object|null} connection - Connection to edit (null for create)
 */
function openOllamaFormModal(connection) {
    // Close LLM model modal first
    const llmModelModal = document.getElementById('llmModelModal');
    if (llmModelModal) {
        llmModelModal.classList.remove('active');
    }

    // Use the exported function from ollamaModal.js
    openFormModal(connection);
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
        renderOllamaTab(appState.ollamaConnections);
    } catch (error) {
        console.error('Activation failed:', error);
        showError('Ошибка активации: ' + error.message);
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
        renderOllamaTab(appState.ollamaConnections);
    } catch (error) {
        console.error('Deletion failed:', error);
        showError('Ошибка удаления: ' + error.message);
    }
}

/**
 * Show success message
 * @param {string} message - Success message
 */
function showSuccess(message) {
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
