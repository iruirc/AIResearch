// Compression API module
import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for chat compression operations
 */
export const compressionApi = {
    /**
     * Get compression configuration for a session
     * @param {string} sessionId - Session identifier
     * @returns {Promise<Object>} Compression config with currentMessageCount, totalTokens, config
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
     * Update compression configuration
     * @param {string} sessionId - Session identifier
     * @param {Object} config - Compression configuration object
     * @returns {Promise<Object>} Updated config response
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
     * Compress session messages
     * @param {string} sessionId - Session identifier
     * @param {Object} options - Compression options
     * @param {string} options.providerId - Provider ID (default: 'CLAUDE')
     * @param {string} options.model - Model to use for compression
     * @param {number} options.contextWindowSize - Context window size
     * @returns {Promise<Object>} Compression result with success, compressionPerformed, newMessages
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
     * Get archived messages for a session
     * @param {string} sessionId - Session identifier
     * @returns {Promise<Object>} Archived messages data
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
