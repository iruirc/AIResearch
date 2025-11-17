// Settings API module
import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for settings and configuration operations
 */
export const settingsApi = {
    /**
     * Load application configuration
     * @returns {Promise<Object>} Configuration object with model, temperature, maxTokens, format
     */
    async loadConfig() {
        const response = await fetchWithTimeout(
            API_CONFIG.CONFIG,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load config: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Load all available providers
     * @returns {Promise<Array>} Array of provider objects
     */
    async loadProviders() {
        const response = await fetchWithTimeout(
            API_CONFIG.PROVIDERS,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load providers: ${response.status}`);
        }

        const data = await response.json();
        return data.providers || [];
    },

    /**
     * Load models for a specific provider
     * @param {string} providerId - Provider identifier
     * @returns {Promise<Array>} Array of model objects
     */
    async loadModels(providerId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.MODELS}?provider=${providerId}`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load models: ${response.status}`);
        }

        const data = await response.json();
        return data.models || [];
    },

    /**
     * Load capabilities for a specific model
     * @param {string} modelId - Model identifier
     * @returns {Promise<Object>} Model capabilities (maxTokens, contextWindow, etc.)
     */
    async loadModelCapabilities(modelId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.MODELS}/${encodeURIComponent(modelId)}/capabilities`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load model capabilities: ${response.status}`);
        }

        return await response.json();
    }
};
