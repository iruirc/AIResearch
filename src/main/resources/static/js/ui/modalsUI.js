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
     * Render agents list in modal
     * @param {Array} agents - Array of agent objects
     * @param {Function} onAgentClick - Callback for agent selection
     */
    renderAgentsList(agents, onAgentClick) {
        const agentsListElement = document.getElementById('agentsList');
        if (!agentsListElement) return;

        if (!agents || agents.length === 0) {
            agentsListElement.innerHTML = '<div class="agents-loading">Нет доступных агентов</div>';
            return;
        }

        agentsListElement.innerHTML = '';
        agents.forEach(agent => {
            const agentItem = document.createElement('div');
            agentItem.className = 'agent-item';
            agentItem.innerHTML = `
                <div class="agent-name">${agent.name}</div>
                <div class="agent-description">${agent.description}</div>
            `;
            agentItem.addEventListener('click', () => onAgentClick(agent.id));
            agentsListElement.appendChild(agentItem);
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
     */
    renderMcpServersList(servers) {
        const mcpServersListElement = document.getElementById('mcpServersList');
        if (!mcpServersListElement) return;

        if (!servers || servers.length === 0) {
            mcpServersListElement.innerHTML = '<div class="mcp-servers-empty">Нет подключенных MCP серверов</div>';
            return;
        }

        mcpServersListElement.innerHTML = '';
        servers.forEach(server => {
            const serverItem = this._createMcpServerItem(server);
            mcpServersListElement.appendChild(serverItem);
        });
    },

    /**
     * Create MCP server item element
     * @private
     * @param {Object} server - Server object
     * @returns {HTMLElement} Server item element
     */
    _createMcpServerItem(server) {
        const serverItem = document.createElement('div');
        serverItem.className = 'mcp-server-item';

        const statusClass = server.connected ? 'connected' : 'disconnected';
        const statusText = server.connected ? 'Подключен' : 'Отключен';

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
                <div class="mcp-server-name">${server.name}</div>
                <div class="mcp-server-status ${statusClass}">${statusText}</div>
            </div>
            ${toolsHtml}
        `;

        // Add event listener for toggle button
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
    }
};
