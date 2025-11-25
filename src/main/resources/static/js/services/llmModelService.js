// LLM Model Service - manages LLM model settings and configuration
import { llmModelApi } from '../api/llmModelApi.js';
import { preferencesApi } from '../api/preferencesApi.js';
import { assistantsApi } from '../api/assistantsApi.js';
import { mcpApi } from '../api/mcpApi.js';
import { appState } from '../state/appState.js';
import { detectProviderFromModel } from '../utils/helpers.js';

/**
 * Service for managing LLM model settings
 * Handles configuration loading, provider/model management, and context window updates
 */
export const llmModelService = {
    /**
     * Load and apply application configuration (from preferences or defaults)
     * @returns {Promise<void>}
     */
    async loadConfig() {
        try {
            // Try to load user preferences first
            const preferences = await preferencesApi.getPreferences();

            // Update application state with preferences
            appState.setCurrentSettings({
                model: preferences.model,
                temperature: preferences.temperature,
                maxTokens: preferences.maxTokens,
                format: preferences.format,
                providerId: preferences.providerId
            });

            appState.currentProvider = preferences.providerId;

            // Load context window for the current model
            await this.updateContextWindow(preferences.model);

            console.log('Preferences loaded:', preferences);
        } catch (error) {
            console.error('Error loading preferences:', error);
            // If preferences loading fails, use default settings (already in state)
        }
    },

    /**
     * Save current settings to preferences
     * @param {Object} settings - Settings to save
     * @param {boolean} showIndicator - Show success indicator
     * @returns {Promise<boolean>} Success status
     */
    async savePreferences(settings, showIndicator = true) {
        try {
            const result = await preferencesApi.savePreferences({
                providerId: settings.providerId,
                model: settings.model,
                temperature: settings.temperature,
                maxTokens: settings.maxTokens,
                format: settings.format
            });

            if (showIndicator) {
                this.showSuccessIndicator();
            }

            console.log('Preferences saved successfully');
            return true;
        } catch (error) {
            console.error('Error saving preferences:', error);
            return false;
        }
    },

    /**
     * Reset preferences to defaults
     * @returns {Promise<Object>} Default preferences
     */
    async resetToDefaults() {
        try {
            const result = await preferencesApi.resetToDefaults();

            if (result.preferences) {
                // Update application state with defaults
                appState.setCurrentSettings({
                    model: result.preferences.model,
                    temperature: result.preferences.temperature,
                    maxTokens: result.preferences.maxTokens,
                    format: result.preferences.format,
                    providerId: result.preferences.providerId
                });

                appState.currentProvider = result.preferences.providerId;
                await this.updateContextWindow(result.preferences.model);

                this.showSuccessIndicator();
                console.log('Preferences reset to defaults');
                return result.preferences;
            }
        } catch (error) {
            console.error('Error resetting preferences:', error);
            throw error;
        }
    },

    /**
     * Show success indicator
     */
    showSuccessIndicator() {
        const indicator = document.getElementById('llmModelStatus');
        if (!indicator) return;

        indicator.style.display = 'flex';
        setTimeout(() => {
            indicator.style.display = 'none';
        }, 3000);
    },

    /**
     * Update context window size for a model
     * @param {string} modelId - Model identifier
     * @returns {Promise<void>}
     */
    async updateContextWindow(modelId) {
        try {
            const capabilities = await llmModelApi.loadModelCapabilities(modelId);
            appState.currentContextWindow = capabilities.contextWindow || 200000;
            console.log(`Context window for ${modelId}: ${appState.currentContextWindow}`);
        } catch (error) {
            console.error('Error updating context window:', error);
            // Use default value on error
            appState.currentContextWindow = 200000;
        }
    },

    /**
     * Load all available providers
     * @returns {Promise<Array>} Array of provider objects
     */
    async loadProviders() {
        try {
            const providers = await llmModelApi.loadProviders();
            appState.setProviders(providers);
            return providers;
        } catch (error) {
            console.error('Error loading providers:', error);
            throw error;
        }
    },

    /**
     * Load models for a specific provider
     * @param {string} providerId - Provider identifier
     * @returns {Promise<Array>} Array of model objects
     */
    async loadModels(providerId) {
        try {
            const models = await llmModelApi.loadModels(providerId);
            appState.setModels(models);
            return models;
        } catch (error) {
            console.error('Error loading models:', error);
            throw error;
        }
    },

    /**
     * Get capabilities for a specific model
     * @param {string} modelId - Model identifier
     * @returns {Promise<Object>} Model capabilities
     */
    async getModelCapabilities(modelId) {
        try {
            return await llmModelApi.loadModelCapabilities(modelId);
        } catch (error) {
            console.error('Error loading model capabilities:', error);
            // Return default capabilities on error
            return {
                maxTokens: 4096,
                contextWindow: 200000,
                supportsVision: false,
                supportsStreaming: true
            };
        }
    },

    /**
     * Update application settings
     * @param {Object} settings - New settings object
     * @returns {Promise<void>}
     */
    async updateSettings(settings) {
        // Update state
        appState.setCurrentSettings(settings);

        // Update provider if model changed
        if (settings.model) {
            const provider = detectProviderFromModel(settings.model);
            appState.currentProvider = provider;

            // Update context window for new model
            await this.updateContextWindow(settings.model);
        }
    },

    /**
     * Load all available assistants
     * @returns {Promise<Array>} Array of assistant objects
     */
    async loadAssistants() {
        try {
            const assistants = await assistantsApi.loadAssistants();
            appState.setAssistants(assistants);
            return assistants;
        } catch (error) {
            console.error('Error loading assistants:', error);
            throw error;
        }
    },

    /**
     * Load MCP servers
     * @returns {Promise<Array>} Array of MCP server objects
     */
    async loadMcpServers() {
        try {
            const servers = await mcpApi.loadServers();
            appState.setMcpServers(servers);
            return servers;
        } catch (error) {
            console.error('Error loading MCP servers:', error);
            // Don't throw - MCP servers might not be configured
            appState.setMcpServers([]);
            return [];
        }
    }
};
