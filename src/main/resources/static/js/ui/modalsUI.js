// Modals UI module - manages modal windows
import { appState } from '../state/appState.js';

/**
 * Modals UI module
 */
export const modalsUI = {
    /**
     * Open a modal by ID
     * @param {string} modalId - Modal element ID
     */
    openModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.add('active');
        }
    },

    /**
     * Close a modal by ID
     * @param {string} modalId - Modal element ID
     */
    closeModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.remove('active');
        }
    },

    /**
     * Close all modals
     */
    closeAllModals() {
        const modals = document.querySelectorAll('.modal');
        modals.forEach(modal => {
            modal.classList.remove('active');
        });
    },

    /**
     * Render assistants list in modal
     * @param {Array} assistants - Array of assistant objects
     * @param {Function} onAssistantClick - Callback for assistant selection
     * @param {Function} onCreateClick - Callback for create button click
     * @param {Function} onEditClick - Callback for edit button click
     * @param {Function} onDeleteClick - Callback for delete button click
     */
    renderAssistantsList(assistants, onAssistantClick, onCreateClick, onEditClick, onDeleteClick) {
        const assistantsListElement = document.getElementById('assistantsList');
        if (!assistantsListElement) return;

        assistantsListElement.innerHTML = '';

        // Add create button at the top
        const createButton = document.createElement('button');
        createButton.className = 'create-assistant-button';
        createButton.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>Создать ассистента</span>
        `;
        createButton.addEventListener('click', (e) => {
            e.stopPropagation();
            onCreateClick();
        });
        assistantsListElement.appendChild(createButton);

        if (!assistants || assistants.length === 0) {
            const emptyMessage = document.createElement('div');
            emptyMessage.className = 'assistants-loading';
            emptyMessage.textContent = 'Нет доступных ассистентов';
            assistantsListElement.appendChild(emptyMessage);
            return;
        }

        assistants.forEach(assistant => {
            const assistantItem = document.createElement('div');
            assistantItem.className = 'assistant-item';

            // Debug logging
            console.log(`Assistant: ${assistant.name}, isSystem: ${assistant.isSystem}`);

            const contentDiv = document.createElement('div');
            contentDiv.className = 'assistant-item-content';
            contentDiv.innerHTML = `
                <div class="assistant-name">${assistant.name}</div>
                <div class="assistant-description">${assistant.description}</div>
            `;
            contentDiv.addEventListener('click', () => onAssistantClick(assistant.id));

            assistantItem.appendChild(contentDiv);

            // Add action buttons for custom (non-system) assistants
            if (!assistant.isSystem) {
                const actionsDiv = document.createElement('div');
                actionsDiv.className = 'assistant-item-actions';

                const editButton = document.createElement('button');
                editButton.className = 'assistant-action-button edit-button';
                editButton.title = 'Редактировать';
                editButton.innerHTML = `
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                `;
                editButton.addEventListener('click', (e) => {
                    e.stopPropagation();
                    onEditClick(assistant);
                });

                const deleteButton = document.createElement('button');
                deleteButton.className = 'assistant-action-button delete-button';
                deleteButton.title = 'Удалить';
                deleteButton.innerHTML = `
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                `;
                deleteButton.addEventListener('click', (e) => {
                    e.stopPropagation();
                    onDeleteClick(assistant);
                });

                actionsDiv.appendChild(editButton);
                actionsDiv.appendChild(deleteButton);
                assistantItem.appendChild(actionsDiv);
            }

            assistantsListElement.appendChild(assistantItem);
        });
    },

    /**
     * Render providers list in settings modal
     * @param {Array} providers - Array of provider objects
     * @param {string} currentProvider - Current selected provider
     * @param {Function} onProviderChange - Callback for provider change
     */
    renderProvidersList(providers, currentProvider, onProviderChange) {
        const providerSelect = document.getElementById('modalProviderSelect');
        if (!providerSelect) return;

        // Remove all existing event listeners by cloning and replacing the element
        const newProviderSelect = providerSelect.cloneNode(false);
        providerSelect.parentNode.replaceChild(newProviderSelect, providerSelect);

        // Populate options
        providers.forEach(provider => {
            const option = document.createElement('option');
            option.value = provider.id;
            option.textContent = provider.name;
            if (provider.id === currentProvider) {
                option.selected = true;
            }
            newProviderSelect.appendChild(option);
        });

        // Add new event listener
        if (onProviderChange) {
            newProviderSelect.addEventListener('change', (e) => onProviderChange(e.target.value));
        }
    },

    /**
     * Render models list in settings modal
     * @param {Array} models - Array of model objects
     * @param {string} currentModel - Current selected model
     */
    renderModelsList(models, currentModel) {
        const modelSelect = document.getElementById('modalModelSelect');
        if (!modelSelect) return;

        modelSelect.innerHTML = '';
        models.forEach(model => {
            const option = document.createElement('option');
            option.value = model.id;
            option.textContent = model.displayName || model.name || model.id;
            if (model.id === currentModel) {
                option.selected = true;
            }
            modelSelect.appendChild(option);
        });
    },

    /**
     * Update settings modal with current values
     * @param {Object} settings - Current settings object
     */
    updateSettingsModal(settings) {
        const temperatureSlider = document.getElementById('modalTemperatureSlider');
        const temperatureValue = document.getElementById('modalTemperatureValue');
        const maxTokensSlider = document.getElementById('modalMaxTokensSlider');
        const maxTokensValue = document.getElementById('modalMaxTokensValue');
        const formatSelect = document.getElementById('modalFormatSelect');

        if (temperatureSlider && temperatureValue) {
            temperatureSlider.value = settings.temperature;
            temperatureValue.textContent = settings.temperature.toFixed(1);
        }

        if (maxTokensSlider && maxTokensValue) {
            maxTokensSlider.value = settings.maxTokens;
            maxTokensValue.textContent = settings.maxTokens;
        }

        if (formatSelect) {
            formatSelect.value = settings.format;
        }
    },

    /**
     * Get settings from modal inputs
     * @returns {Object} Settings object
     */
    getSettingsFromModal() {
        const modelSelect = document.getElementById('modalModelSelect');
        const temperatureSlider = document.getElementById('modalTemperatureSlider');
        const maxTokensSlider = document.getElementById('modalMaxTokensSlider');
        const formatSelect = document.getElementById('modalFormatSelect');

        return {
            model: modelSelect?.value,
            temperature: parseFloat(temperatureSlider?.value),
            maxTokens: parseInt(maxTokensSlider?.value),
            format: formatSelect?.value
        };
    },

    /**
     * Update compression modal with session info
     * @param {Object} compressionInfo - Compression info object
     */
    updateCompressionModal(compressionInfo) {
        const messageCount = document.getElementById('currentMessageCount');
        const tokenCount = document.getElementById('currentTokenCount');

        if (messageCount) {
            messageCount.textContent = compressionInfo.currentMessageCount || 0;
        }

        if (tokenCount) {
            tokenCount.textContent = compressionInfo.totalTokens || 0;
        }
    },

    /**
     * Get selected compression strategy
     * @returns {string} Selected strategy
     */
    getSelectedCompressionStrategy() {
        const selected = document.querySelector('input[name="compressionStrategy"]:checked');
        return selected ? selected.value : 'FULL_REPLACEMENT';
    },

    /**
     * Show compression indicator overlay
     */
    showCompressionIndicator() {
        const indicator = document.getElementById('compressionIndicator');
        if (indicator) {
            indicator.style.display = 'flex';
        }
    },

    /**
     * Hide compression indicator overlay
     */
    hideCompressionIndicator() {
        const indicator = document.getElementById('compressionIndicator');
        if (indicator) {
            indicator.style.display = 'none';
        }
    },

    /**
     * Render MCP servers list in modal
     * @param {Array} servers - Array of MCP server objects
     * @param {Function} onToggle - Callback for server toggle (serverId, enabled)
     */
    renderMcpServersList(servers, onToggle) {
        const mcpServersListElement = document.getElementById('mcpServersList');
        if (!mcpServersListElement) return;

        if (!servers || servers.length === 0) {
            mcpServersListElement.innerHTML = '<div class="mcp-servers-empty">Нет подключенных MCP серверов</div>';
            return;
        }

        mcpServersListElement.innerHTML = '';
        servers.forEach(server => {
            const serverItem = this._createMcpServerItem(server, onToggle);
            mcpServersListElement.appendChild(serverItem);
        });
    },

    /**
     * Create MCP server item element
     * @private
     * @param {Object} server - Server object
     * @param {Function} onToggle - Callback for toggle switch
     * @returns {HTMLElement} Server item element
     */
    _createMcpServerItem(server, onToggle) {
        const serverItem = document.createElement('div');
        serverItem.className = 'mcp-server-item';

        const statusClass = server.connected ? 'connected' : 'disconnected';
        const statusText = server.connected ? 'ПОДКЛЮЧЕН' : 'НЕ ПОДКЛЮЧЕН';

        let toolsHtml = '';
        if (server.tools && server.tools.length > 0) {
            toolsHtml = `
                <div class="mcp-server-tools">
                    <div class="mcp-tools-header">
                        <span>Инструменты (${server.tools.length}):</span>
                        <button class="mcp-tools-toggle collapsed" aria-label="Развернуть/свернуть инструменты">
                            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M4 6L8 10L12 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                            </svg>
                        </button>
                    </div>
                    <ul class="mcp-tools-list collapsed">
                        ${server.tools.map(tool => `
                            <li class="mcp-tool-item">
                                <div class="mcp-tool-name">${tool.name}</div>
                                <div class="mcp-tool-description">${tool.description || ''}</div>
                            </li>
                        `).join('')}
                    </ul>
                </div>
            `;
        }

        serverItem.innerHTML = `
            <div class="mcp-server-header">
                <div class="mcp-server-info">
                    <div class="mcp-server-name">${server.name}</div>
                    <div class="mcp-server-status ${statusClass}">${statusText}</div>
                </div>
                <label class="mcp-server-toggle">
                    <input type="checkbox" ${server.enabled ? 'checked' : ''} data-server-id="${server.id}">
                    <span class="toggle-slider"></span>
                </label>
            </div>
            ${toolsHtml}
        `;

        // Add event listener for toggle switch
        const toggleInput = serverItem.querySelector('.mcp-server-toggle input');
        if (toggleInput && onToggle) {
            toggleInput.addEventListener('change', (e) => {
                onToggle(server.id, e.target.checked);
            });
        }

        // Add event listener for tools toggle button
        if (server.tools && server.tools.length > 0) {
            const toggleButton = serverItem.querySelector('.mcp-tools-toggle');
            const toolsList = serverItem.querySelector('.mcp-tools-list');

            if (toggleButton && toolsList) {
                toggleButton.addEventListener('click', () => {
                    const isCollapsed = toolsList.classList.toggle('collapsed');
                    toggleButton.classList.toggle('collapsed', isCollapsed);
                });
            }
        }

        return serverItem;
    },

    /**
     * Open assistant form modal for create or edit
     * @param {Object|null} assistant - Assistant object for editing, null for creating
     */
    openAssistantFormModal(assistant = null) {
        const modal = document.getElementById('assistantFormModal');
        const title = document.getElementById('assistantFormTitle');
        const nameInput = document.getElementById('assistantName');
        const descriptionInput = document.getElementById('assistantDescription');
        const systemPromptInput = document.getElementById('assistantSystemPrompt');

        if (assistant) {
            // Edit mode
            title.textContent = 'Редактировать ассистента';
            nameInput.value = assistant.name;
            descriptionInput.value = assistant.description || '';
            systemPromptInput.value = assistant.systemPrompt;
            // Store assistant ID for editing
            modal.dataset.assistantId = assistant.id;
            modal.dataset.mode = 'edit';
        } else {
            // Create mode
            title.textContent = 'Создать ассистента';
            nameInput.value = '';
            descriptionInput.value = '';
            systemPromptInput.value = '';
            delete modal.dataset.assistantId;
            modal.dataset.mode = 'create';
        }

        this.openModal('assistantFormModal');
    },

    /**
     * Get assistant form data
     * @returns {Object} Form data with name, description, systemPrompt, and mode
     */
    getAssistantFormData() {
        const modal = document.getElementById('assistantFormModal');
        const nameInput = document.getElementById('assistantName');
        const descriptionInput = document.getElementById('assistantDescription');
        const systemPromptInput = document.getElementById('assistantSystemPrompt');

        return {
            name: nameInput.value.trim(),
            description: descriptionInput.value.trim(),
            systemPrompt: systemPromptInput.value.trim(),
            mode: modal.dataset.mode || 'create',
            assistantId: modal.dataset.assistantId || null
        };
    },

    /**
     * Open delete confirmation modal
     * @param {Object} assistant - Assistant to delete
     * @param {Function} onConfirm - Callback when deletion is confirmed
     */
    openDeleteAssistantModal(assistant, onConfirm) {
        const modal = document.getElementById('deleteAssistantModal');
        const nameElement = document.getElementById('deleteAssistantName');
        const confirmButton = document.getElementById('confirmDeleteAssistantButton');

        nameElement.textContent = assistant.name;

        // Remove old event listeners by cloning and replacing
        const newConfirmButton = confirmButton.cloneNode(true);
        confirmButton.parentNode.replaceChild(newConfirmButton, confirmButton);

        // Add new event listener
        newConfirmButton.addEventListener('click', () => {
            onConfirm(assistant.id);
            this.closeModal('deleteAssistantModal');
        });

        this.openModal('deleteAssistantModal');
    }
};
