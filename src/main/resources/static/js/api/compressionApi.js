/**
 * @fileoverview Compression API module
 * Handles communication with chat compression API endpoints
 * @module api/compressionApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for chat compression operations
 * @namespace
 */
export const compressionApi = {
    /**
     * Get compression configuration and statistics for a session
     * @async
     * @param {string} sessionId - The session identifier
     * @returns {Promise<Object>} Compression config with currentMessageCount, totalTokens, and config object
     * @throws {Error} If the HTTP request fails
     * @example
     * const config = await compressionApi.getConfig('session-123');
     * console.log(config.currentMessageCount); // 15
     */
    async getConfig(sessionId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.COMPRESSION}/config/${sessionId}`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load compression config: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Update compression configuration for a session
     * @async
     * @param {string} sessionId - The session identifier
     * @param {Object} config - Compression configuration object with strategy and parameters
     * @returns {Promise<Object>} Updated configuration response
     * @throws {Error} If the HTTP request fails
     * @example
     * await compressionApi.updateConfig('session-123', {
     *   strategy: 'SLIDING_WINDOW',
     *   slidingWindowMessageThreshold: 12
     * });
     */
    async updateConfig(sessionId, config) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.COMPRESSION}/config`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    sessionId,
                    config,
                }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to update compression config: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Compress chat history for a session using AI summarization
     * @async
     * @param {string} sessionId - The session identifier
     * @param {Object} [options={}] - Compression options
     * @param {string} [options.providerId='CLAUDE'] - AI provider ID for compression
     * @param {string} [options.model] - Specific model to use for compression
     * @param {number} [options.contextWindowSize] - Context window size for token-based compression
     * @returns {Promise<Object>} Compression result with success flag, compressionPerformed, and newMessages array
     * @throws {Error} If the HTTP request fails
     * @example
     * const result = await compressionApi.compress('session-123', {
     *   providerId: 'CLAUDE',
     *   model: 'claude-haiku-4-5-20251001',
     *   contextWindowSize: 200000
     * });
     */
    async compress(sessionId, options = {}) {
        const requestBody = {
            sessionId,
            providerId: options.providerId || 'CLAUDE',
            model: options.model,
            contextWindowSize: options.contextWindowSize,
        };

        const response = await fetchWithTimeout(
            `${API_CONFIG.COMPRESSION}/compress`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(requestBody),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to compress session: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Get archived (compressed) messages for a session
     * @async
     * @param {string} sessionId - The session identifier
     * @returns {Promise<Object>} Archived messages data containing old message history
     * @throws {Error} If the HTTP request fails
     * @example
     * const archived = await compressionApi.getArchivedMessages('session-123');
     */
    async getArchivedMessages(sessionId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.COMPRESSION}/archived/${sessionId}`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load archived messages: ${response.status}`);
        }

        return await response.json();
    }
};
