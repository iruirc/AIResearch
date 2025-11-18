/**
 * @fileoverview Chat API module
 * Handles communication with the chat API endpoint
 * @module api/chatApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * Chat API interface object
 * @namespace
 */
export const chatApi = {
    /**
     * Send a message to the chat API
     * @async
     * @param {string} message - The user's message text
     * @param {string|null} sessionId - Optional session ID for continuing a conversation
     * @param {Object} settings - Chat settings
     * @param {string} settings.format - Response format (PLAIN_TEXT, MARKDOWN, etc.)
     * @param {string} settings.model - AI model identifier
     * @param {number} settings.temperature - Temperature for generation (0.0-2.0)
     * @param {number} settings.maxTokens - Maximum tokens for response
     * @returns {Promise<Object>} Response object with response text, sessionId, and token usage
     * @throws {Error} If the HTTP request fails
     * @example
     * const response = await chatApi.sendMessage(
     *   'Hello!',
     *   'session-123',
     *   { model: 'claude-3-opus', temperature: 1.0, maxTokens: 4096, format: 'PLAIN_TEXT' }
     * );
     */
    async sendMessage(message, sessionId, settings) {
        const requestBody = {
            message,
            format: settings.format,
            model: settings.model,
            temperature: settings.temperature,
            maxTokens: settings.maxTokens
        };

        if (sessionId) {
            requestBody.sessionId = sessionId;
        }

        const response = await fetchWithTimeout(
            API_CONFIG.CHAT,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        return await response.json();
    }
};
