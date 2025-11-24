/**
 * @fileoverview Ollama API module
 * Handles communication with the Ollama provider management API endpoints
 * @module api/ollamaApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * Base URL for Ollama API endpoints
 * @constant {string}
 */
const OLLAMA_BASE_URL = '/api/v2/providers/ollama';

/**
 * API module for Ollama connection management operations
 * @namespace
 */
export const ollamaApi = {
    /**
     * Fetch all Ollama connections from the API
     * @async
     * @returns {Promise<Object>} Object containing connections array and activeConnectionId
     * @returns {Promise<Object>} response.connections - Array of connection objects
     * @returns {Promise<Object>} response.activeConnectionId - ID of the active connection
     * @throws {Error} If the HTTP request fails
     * @example
     * const { connections, activeConnectionId } = await ollamaApi.fetchOllamaConnections();
     * console.log(connections); // [{ id: '...', name: 'Local Ollama', baseUrl: 'http://localhost:11434', ... }]
     */
    async fetchOllamaConnections() {
        const response = await fetchWithTimeout(
            `${OLLAMA_BASE_URL}/connections`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to fetch Ollama connections: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Get a specific Ollama connection by ID
     * @async
     * @param {string} connectionId - The connection identifier
     * @returns {Promise<Object>} Connection object with full details
     * @throws {Error} If the HTTP request fails
     * @example
     * const connection = await ollamaApi.getOllamaConnection('connection-id');
     * console.log(connection.name); // 'Local Ollama'
     */
    async getOllamaConnection(connectionId) {
        const response = await fetchWithTimeout(
            `${OLLAMA_BASE_URL}/connections/${connectionId}`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to get Ollama connection: ${response.status}`);
        }

        const data = await response.json();
        return data.connection;
    },

    /**
     * Create a new Ollama connection
     * @async
     * @param {Object} connectionData - Connection configuration
     * @param {string} connectionData.name - Display name for the connection
     * @param {string} connectionData.baseUrl - Base URL of the Ollama server (e.g., http://localhost:11434)
     * @param {string} [connectionData.keepAlive='5m'] - How long to keep models in memory (e.g., '5m', '1h')
     * @returns {Promise<Object>} Created connection object
     * @throws {Error} If the HTTP request fails or validation fails
     * @example
     * const connection = await ollamaApi.createOllamaConnection({
     *     name: 'Remote Ollama',
     *     baseUrl: 'http://127.0.0.1:11434',
     *     keepAlive: '10m'
     * });
     */
    async createOllamaConnection(connectionData) {
        const response = await fetchWithTimeout(
            `${OLLAMA_BASE_URL}/connections`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(connectionData),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to create Ollama connection: ${response.status}`);
        }

        const data = await response.json();
        return data.connection;
    },

    /**
     * Update an existing Ollama connection
     * @async
     * @param {string} connectionId - The connection identifier to update
     * @param {Object} connectionData - Updated connection configuration
     * @param {string} connectionData.name - Updated display name
     * @param {string} connectionData.baseUrl - Updated base URL
     * @param {string} [connectionData.keepAlive='5m'] - Updated keep-alive duration
     * @returns {Promise<Object>} Updated connection object
     * @throws {Error} If the HTTP request fails or validation fails
     * @example
     * const connection = await ollamaApi.updateOllamaConnection('connection-id', {
     *     name: 'Local Ollama (Updated)',
     *     baseUrl: 'http://localhost:11434',
     *     keepAlive: '15m'
     * });
     */
    async updateOllamaConnection(connectionId, connectionData) {
        const response = await fetchWithTimeout(
            `${OLLAMA_BASE_URL}/connections/${connectionId}`,
            {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(connectionData),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to update Ollama connection: ${response.status}`);
        }

        const data = await response.json();
        return data.connection;
    },

    /**
     * Delete an Ollama connection
     * @async
     * @param {string} connectionId - The connection identifier to delete
     * @returns {Promise<Object>} Deletion confirmation response
     * @throws {Error} If the HTTP request fails
     * @example
     * await ollamaApi.deleteOllamaConnection('connection-id');
     */
    async deleteOllamaConnection(connectionId) {
        const response = await fetchWithTimeout(
            `${OLLAMA_BASE_URL}/connections/${connectionId}`,
            {
                method: 'DELETE',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to delete Ollama connection: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Activate an Ollama connection (set as default)
     * @async
     * @param {string} connectionId - The connection identifier to activate
     * @returns {Promise<Object>} Activation confirmation response
     * @throws {Error} If the HTTP request fails
     * @example
     * await ollamaApi.activateOllamaConnection('connection-id');
     */
    async activateOllamaConnection(connectionId) {
        const response = await fetchWithTimeout(
            `${OLLAMA_BASE_URL}/connections/${connectionId}/activate`,
            {
                method: 'POST',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to activate Ollama connection: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Test an Ollama connection to verify it's working
     * @async
     * @param {string} connectionId - The connection identifier to test
     * @returns {Promise<Object>} Test result with success status and optional version info
     * @returns {Promise<Object>} response.success - Whether the test was successful
     * @returns {Promise<Object>} response.message - Result message
     * @returns {Promise<Object>} response.version - Optional version string if connected
     * @throws {Error} If the HTTP request fails
     * @example
     * const result = await ollamaApi.testOllamaConnection('connection-id');
     * if (result.success) {
     *     console.log('Connection test successful!');
     * }
     */
    async testOllamaConnection(connectionId) {
        const response = await fetchWithTimeout(
            `${OLLAMA_BASE_URL}/connections/${connectionId}/test`,
            {
                method: 'POST',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to test Ollama connection: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Fetch available models from the active Ollama connection
     * @async
     * @returns {Promise<Array>} Array of model objects
     * @returns {Promise<Array>} response.models - Array of models with name, size, modifiedAt, family
     * @throws {Error} If the HTTP request fails or no active connection exists
     * @example
     * const { models } = await ollamaApi.fetchOllamaModels();
     * console.log(models); // [{ name: 'llama3.2', size: 0, modifiedAt: '', family: null }]
     */
    async fetchOllamaModels() {
        const response = await fetchWithTimeout(
            `${OLLAMA_BASE_URL}/models`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to fetch Ollama models: ${response.status}`);
        }

        const data = await response.json();
        return data.models || [];
    }
};
