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
    },

    /**
     * Create a new custom assistant
     * @async
     * @param {string} id - The assistant identifier (slug)
     * @param {string} name - Display name of the assistant
     * @param {string} description - Brief description of the assistant
     * @param {string} systemPrompt - System prompt defining assistant behavior
     * @returns {Promise<Object>} Created assistant data
     * @throws {Error} If the HTTP request fails
     * @example
     * const assistant = await assistantsApi.createAssistant('code-reviewer', 'Code Reviewer', 'Reviews code', 'You are...');
     */
    async createAssistant(id, name, description, systemPrompt) {
        const response = await fetchWithTimeout(
            API_CONFIG.ASSISTANTS,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ id, name, description, systemPrompt }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to create assistant: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Update an existing custom assistant
     * @async
     * @param {string} id - The assistant identifier
     * @param {string} name - Updated display name
     * @param {string} description - Updated description
     * @param {string} systemPrompt - Updated system prompt
     * @returns {Promise<Object>} Updated assistant data
     * @throws {Error} If the HTTP request fails
     * @example
     * const assistant = await assistantsApi.updateAssistant('code-reviewer', 'Senior Code Reviewer', 'Reviews code...', 'You are...');
     */
    async updateAssistant(id, name, description, systemPrompt) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.ASSISTANTS}/${id}`,
            {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ id, name, description, systemPrompt }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to update assistant: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Get a specific assistant by ID
     * @async
     * @param {string} id - The assistant identifier
     * @returns {Promise<Object>} Assistant object with full details
     * @throws {Error} If the HTTP request fails
     * @example
     * const assistant = await assistantsApi.getAssistant('code-reviewer');
     */
    async getAssistant(id) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.ASSISTANTS}/${id}`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to get assistant: ${response.status}`);
        }

        const data = await response.json();
        return data.assistant;
    },

    /**
     * Delete a custom assistant
     * @async
     * @param {string} id - The assistant identifier
     * @returns {Promise<Object>} Deletion confirmation
     * @throws {Error} If the HTTP request fails
     * @example
     * await assistantsApi.deleteAssistant('code-reviewer');
     */
    async deleteAssistant(id) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.ASSISTANTS}/${id}`,
            {
                method: 'DELETE',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Failed to delete assistant: ${response.status}`);
        }

        return await response.json();
    }
};
