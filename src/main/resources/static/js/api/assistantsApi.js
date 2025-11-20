/**
 * @fileoverview Assistants API module
 * Handles communication with the assistants management API endpoints
 * @module api/assistantsApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for assistants management operations
 * @namespace
 */
export const assistantsApi = {
    /**
     * Load all available assistants from the API
     * @async
     * @returns {Promise<Array>} Array of assistant objects with id, name, and description
     * @throws {Error} If the HTTP request fails
     * @example
     * const assistants = await assistantsApi.loadAssistants();
     * console.log(assistants); // [{ id: 'assistant1', name: 'AI Tutor', description: '...' }]
     */
    async loadAssistants() {
        const response = await fetchWithTimeout(
            API_CONFIG.ASSISTANTS,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load assistants: ${response.status}`);
        }

        const data = await response.json();
        return data.assistants || [];
    },

    /**
     * Start a new chat session with a specific assistant
     * @async
     * @param {string} assistantId - The assistant identifier
     * @returns {Promise<Object>} New session data containing sessionId
     * @throws {Error} If the HTTP request fails
     * @example
     * const session = await assistantsApi.startAssistantSession('ai-tutor');
     * console.log(session.sessionId); // 'new-session-id'
     */
    async startAssistantSession(assistantId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.ASSISTANTS}/start`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ assistantId }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to start assistant session: ${response.status}`);
        }

        return await response.json();
    }
};
