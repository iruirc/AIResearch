/**
 * @fileoverview Settings API module
 * Handles communication with settings and configuration API endpoints
 * @module api/settingsApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for settings and configuration operations
 * @namespace
 */
export const settingsApi = {
    /**
     * Load application configuration from the server
     * @async
     * @returns {Promise<Object>} Configuration object with model, temperature, maxTokens, and format
     * @throws {Error} If the HTTP request fails
     * @example
     * const config = await settingsApi.loadConfig();
     * console.log(config.model); // 'claude-haiku-4-5-20251001'
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
     * Load all available AI providers
     * @async
     * @returns {Promise<Array>} Array of provider objects with id and name
     * @throws {Error} If the HTTP request fails
     * @example
     * const providers = await settingsApi.loadProviders();
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
     * Load all models available for a specific provider
     * @async
     * @param {string} providerId - The provider identifier (e.g., 'claude', 'openai')
     * @returns {Promise<Array>} Array of model objects with id, name, and displayName
     * @throws {Error} If the HTTP request fails
     * @example
     * const models = await settingsApi.loadModels('claude');
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
     * Load capabilities and limits for a specific model
     * @async
     * @param {string} modelId - The model identifier
     * @returns {Promise<Object>} Model capabilities including maxTokens, contextWindow, supportsVision, etc.
     * @throws {Error} If the HTTP request fails
     * @example
     * const caps = await settingsApi.loadModelCapabilities('claude-3-opus-20240229');
     * console.log(caps.contextWindow); // 200000
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
