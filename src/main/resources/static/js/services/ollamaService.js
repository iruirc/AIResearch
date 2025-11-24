/**
 * @fileoverview Ollama Service - manages Ollama connections and models
 * Provides business logic layer for Ollama provider connection management
 * @module services/ollamaService
 */

import { ollamaApi } from '../api/ollamaApi.js';
import { appState } from '../state/appState.js';

/**
 * Service for managing Ollama connections and models
 * Handles connection CRUD operations, testing, activation, and model fetching
 * Integrates with application state for reactive UI updates
 */
export const ollamaService = {
    /**
     * Internal cache for connections
     * @private
     */
    _connections: [],

    /**
     * Active connection ID cache
     * @private
     */
    _activeConnectionId: null,

    /**
     * Load all Ollama connections from the API and update state
     * @returns {Promise<Object>} Object with connections and activeConnectionId
     * @example
     * const { connections, activeConnectionId } = await ollamaService.loadConnections();
     */
    async loadConnections() {
        try {
            const data = await ollamaApi.fetchOllamaConnections();

            this._connections = data.connections || [];
            this._activeConnectionId = data.activeConnectionId || null;

            // Update application state
            appState.setState({
                ollamaConnections: this._connections,
                activeOllamaConnectionId: this._activeConnectionId
            });

            console.log(`Loaded ${this._connections.length} Ollama connections`);
            return data;

        } catch (error) {
            console.error('Error loading Ollama connections:', error);
            // Set empty state on error
            this._connections = [];
            this._activeConnectionId = null;
            appState.setState({
                ollamaConnections: [],
                activeOllamaConnectionId: null
            });
            throw error;
        }
    },

    /**
     * Get a specific connection by ID
     * @param {string} connectionId - Connection identifier
     * @returns {Promise<Object>} Connection object
     * @example
     * const connection = await ollamaService.getConnection('connection-id');
     */
    async getConnection(connectionId) {
        try {
            // Try cache first
            const cached = this._connections.find(c => c.id === connectionId);
            if (cached) {
                return cached;
            }

            // Fetch from API if not in cache
            const connection = await ollamaApi.getOllamaConnection(connectionId);
            return connection;

        } catch (error) {
            console.error(`Error getting connection ${connectionId}:`, error);
            throw error;
        }
    },

    /**
     * Create a new Ollama connection
     * @param {Object} connectionData - Connection configuration
     * @param {string} connectionData.name - Display name for the connection
     * @param {string} connectionData.baseUrl - Base URL of the Ollama server
     * @param {string} [connectionData.keepAlive='5m'] - Keep-alive duration
     * @returns {Promise<Object>} Created connection object
     * @example
     * const connection = await ollamaService.createConnection({
     *     name: 'Local Ollama',
     *     baseUrl: 'http://localhost:11434',
     *     keepAlive: '5m'
     * });
     */
    async createConnection(connectionData) {
        try {
            // Validate input
            if (!connectionData.name || connectionData.name.trim() === '') {
                throw new Error('Connection name is required');
            }

            if (!connectionData.baseUrl || connectionData.baseUrl.trim() === '') {
                throw new Error('Base URL is required');
            }

            if (!connectionData.baseUrl.startsWith('http://') && !connectionData.baseUrl.startsWith('https://')) {
                throw new Error('Base URL must start with http:// or https://');
            }

            // Create via API
            const connection = await ollamaApi.createOllamaConnection(connectionData);

            // Update cache
            this._connections.push(connection);

            // If this is the first connection, set it as active
            if (this._activeConnectionId === null) {
                this._activeConnectionId = connection.id;
            }

            // Update application state
            appState.setState({
                ollamaConnections: this._connections,
                activeOllamaConnectionId: this._activeConnectionId
            });

            console.log(`Created connection: ${connection.name} (${connection.id})`);
            return connection;

        } catch (error) {
            console.error('Error creating Ollama connection:', error);
            throw error;
        }
    },

    /**
     * Update an existing Ollama connection
     * @param {string} connectionId - Connection identifier to update
     * @param {Object} connectionData - Updated connection configuration
     * @param {string} connectionData.name - Updated display name
     * @param {string} connectionData.baseUrl - Updated base URL
     * @param {string} [connectionData.keepAlive='5m'] - Updated keep-alive duration
     * @returns {Promise<Object>} Updated connection object
     * @example
     * const connection = await ollamaService.updateConnection('connection-id', {
     *     name: 'Updated Name',
     *     baseUrl: 'http://localhost:11434',
     *     keepAlive: '10m'
     * });
     */
    async updateConnection(connectionId, connectionData) {
        try {
            // Validate input
            if (!connectionData.name || connectionData.name.trim() === '') {
                throw new Error('Connection name is required');
            }

            if (!connectionData.baseUrl || connectionData.baseUrl.trim() === '') {
                throw new Error('Base URL is required');
            }

            if (!connectionData.baseUrl.startsWith('http://') && !connectionData.baseUrl.startsWith('https://')) {
                throw new Error('Base URL must start with http:// or https://');
            }

            // Update via API
            const connection = await ollamaApi.updateOllamaConnection(connectionId, connectionData);

            // Update cache
            const index = this._connections.findIndex(c => c.id === connectionId);
            if (index !== -1) {
                this._connections[index] = connection;
            }

            // Update application state
            appState.setState({
                ollamaConnections: this._connections
            });

            console.log(`Updated connection: ${connection.name} (${connection.id})`);
            return connection;

        } catch (error) {
            console.error(`Error updating connection ${connectionId}:`, error);
            throw error;
        }
    },

    /**
     * Delete an Ollama connection
     * @param {string} connectionId - Connection identifier to delete
     * @returns {Promise<void>}
     * @example
     * await ollamaService.deleteConnection('connection-id');
     */
    async deleteConnection(connectionId) {
        try {
            // Delete via API
            await ollamaApi.deleteOllamaConnection(connectionId);

            // Remove from cache
            this._connections = this._connections.filter(c => c.id !== connectionId);

            // If deleted connection was active, select another
            if (this._activeConnectionId === connectionId) {
                this._activeConnectionId = this._connections.length > 0
                    ? this._connections[0].id
                    : null;
            }

            // Update application state
            appState.setState({
                ollamaConnections: this._connections,
                activeOllamaConnectionId: this._activeConnectionId
            });

            console.log(`Deleted connection: ${connectionId}`);

        } catch (error) {
            console.error(`Error deleting connection ${connectionId}:`, error);
            throw error;
        }
    },

    /**
     * Activate an Ollama connection (set as default)
     * @param {string} connectionId - Connection identifier to activate
     * @returns {Promise<void>}
     * @example
     * await ollamaService.activateConnection('connection-id');
     */
    async activateConnection(connectionId) {
        try {
            // Validate connection exists
            const exists = this._connections.some(c => c.id === connectionId);
            if (!exists) {
                throw new Error(`Connection not found: ${connectionId}`);
            }

            // Activate via API
            await ollamaApi.activateOllamaConnection(connectionId);

            // Update cache
            this._activeConnectionId = connectionId;

            // Update application state
            appState.setState({
                activeOllamaConnectionId: this._activeConnectionId
            });

            console.log(`Activated connection: ${connectionId}`);

        } catch (error) {
            console.error(`Error activating connection ${connectionId}:`, error);
            throw error;
        }
    },

    /**
     * Test an Ollama connection to verify it's working
     * @param {string} connectionId - Connection identifier to test
     * @returns {Promise<Object>} Test result with success status
     * @returns {Promise<Object>} result.success - Whether the test was successful
     * @returns {Promise<Object>} result.message - Result message
     * @returns {Promise<Object>} result.version - Optional version string
     * @example
     * const result = await ollamaService.testConnection('connection-id');
     * if (result.success) {
     *     console.log('Connection test successful!');
     * }
     */
    async testConnection(connectionId) {
        try {
            const result = await ollamaApi.testOllamaConnection(connectionId);
            console.log(`Connection test for ${connectionId}: ${result.success ? 'SUCCESS' : 'FAILED'}`);
            return result;

        } catch (error) {
            console.error(`Error testing connection ${connectionId}:`, error);
            return {
                success: false,
                message: error.message || 'Connection test failed',
                version: null
            };
        }
    },

    /**
     * Fetch available models from the active Ollama connection
     * @returns {Promise<Array>} Array of model objects
     * @example
     * const models = await ollamaService.fetchModels();
     * console.log(models); // [{ name: 'llama3.2', size: 0, ... }]
     */
    async fetchModels() {
        try {
            if (!this._activeConnectionId) {
                console.warn('No active Ollama connection');
                return [];
            }

            const models = await ollamaApi.fetchOllamaModels();

            // Update application state with Ollama models
            appState.setState({
                ollamaModels: models
            });

            console.log(`Fetched ${models.length} models from Ollama`);
            return models;

        } catch (error) {
            console.error('Error fetching Ollama models:', error);
            appState.setState({
                ollamaModels: []
            });
            throw error;
        }
    },

    /**
     * Get all connections from cache (without API call)
     * @returns {Array} Array of connection objects
     * @example
     * const connections = ollamaService.getConnectionsFromCache();
     */
    getConnectionsFromCache() {
        return [...this._connections];
    },

    /**
     * Get active connection ID from cache
     * @returns {string|null} Active connection ID
     * @example
     * const activeId = ollamaService.getActiveConnectionId();
     */
    getActiveConnectionId() {
        return this._activeConnectionId;
    },

    /**
     * Get active connection object from cache
     * @returns {Object|null} Active connection object
     * @example
     * const activeConnection = ollamaService.getActiveConnection();
     */
    getActiveConnection() {
        if (!this._activeConnectionId) return null;
        return this._connections.find(c => c.id === this._activeConnectionId) || null;
    },

    /**
     * Initialize the service (load connections on startup)
     * @returns {Promise<void>}
     * @example
     * await ollamaService.initialize();
     */
    async initialize() {
        try {
            await this.loadConnections();
            console.log('OllamaService initialized successfully');
        } catch (error) {
            console.error('Error initializing OllamaService:', error);
            // Don't throw - allow app to continue without Ollama
        }
    },

    /**
     * Clear all cached data (useful for testing or logout)
     * @example
     * ollamaService.clearCache();
     */
    clearCache() {
        this._connections = [];
        this._activeConnectionId = null;
        appState.setState({
            ollamaConnections: [],
            activeOllamaConnectionId: null,
            ollamaModels: []
        });
        console.log('OllamaService cache cleared');
    }
};
