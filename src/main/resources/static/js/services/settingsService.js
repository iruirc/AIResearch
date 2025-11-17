// Settings Service - manages application settings and configuration
import { settingsApi } from '../api/settingsApi.js';
import { agentsApi } from '../api/agentsApi.js';
import { mcpApi } from '../api/mcpApi.js';
import { appState } from '../state/appState.js';
import { detectProviderFromModel } from '../utils/helpers.js';

/**
 * Service for managing application settings
 * Handles configuration loading, provider/model management, and context window updates
 */
export const settingsService = {
    /**
     * Load and apply application configuration
     * @returns {Promise<void>}
     */
    async loadConfig() {
        try {
            const config = await settingsApi.loadConfig();

            // Update application state with config
            appState.setCurrentSettings({
                model: config.model,
                temperature: config.temperature,
                maxTokens: config.maxTokens,
                format: config.format
            });

            // Detect and set provider
            const provider = detectProviderFromModel(config.model);
            appState.currentProvider = provider;

            // Load context window for the current model
            await this.updateContextWindow(config.model);

            console.log('Configuration loaded:', config);
        } catch (error) {
            console.error('Error loading config:', error);
            // If config loading fails, use default settings (already in state)
        }
    },

    /**
     * Update context window size for a model
     * @param {string} modelId - Model identifier
     * @returns {Promise<void>}
     */
    async updateContextWindow(modelId) {
        try {
            const capabilities = await settingsApi.loadModelCapabilities(modelId);
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
            const providers = await settingsApi.loadProviders();
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
            const models = await settingsApi.loadModels(providerId);
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
            return await settingsApi.loadModelCapabilities(modelId);
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
     * Load all available agents
     * @returns {Promise<Array>} Array of agent objects
     */
    async loadAgents() {
        try {
            const agents = await agentsApi.loadAgents();
            appState.setAgents(agents);
            return agents;
        } catch (error) {
            console.error('Error loading agents:', error);
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
