/**
 * @fileoverview Sessions API module
 * Handles communication with session management API endpoints
 * @module api/sessionsApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for session management operations
 * @namespace
 */
export const sessionsApi = {
    /**
     * Create a new empty chat session
     * @async
     * @returns {Promise<Object>} Object containing the new sessionId
     * @throws {Error} If the HTTP request fails
     * @example
     * const { sessionId } = await sessionsApi.createSession();
     */
    async createSession() {
        const response = await fetchWithTimeout(
            API_CONFIG.SESSIONS,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorText = await response.text();
            console.error('Create session error:', errorText);
            throw new Error(`Failed to create session: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Load all chat sessions from the server
     * @async
     * @returns {Promise<Array>} Array of session objects with id, title, messageCount, etc.
     * @throws {Error} If the HTTP request fails
     * @example
     * const sessions = await sessionsApi.loadSessions();
     */
    async loadSessions() {
        const response = await fetchWithTimeout(
            API_CONFIG.SESSIONS,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load sessions: ${response.status}`);
        }

        const data = await response.json();
        return data.sessions || [];
    },

    /**
     * Get a specific session's details and message history
     * @async
     * @param {string} sessionId - The session identifier
     * @returns {Promise<Object>} Session data with messages array
     * @throws {Error} If the HTTP request fails
     * @example
     * const session = await sessionsApi.getSession('session-123');
     * console.log(session.messages);
     */
    async getSession(sessionId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.SESSIONS}/${sessionId}`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load session history: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Delete a session permanently
     * @async
     * @param {string} sessionId - The session identifier to delete
     * @returns {Promise<Object>} Deletion confirmation response
     * @throws {Error} If the HTTP request fails
     * @example
     * await sessionsApi.deleteSession('session-123');
     */
    async deleteSession(sessionId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.SESSIONS}/${sessionId}`,
            {
                method: 'DELETE',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to delete session: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Copy an existing session to create a duplicate
     * @async
     * @param {string} sessionId - The session identifier to copy
     * @returns {Promise<Object>} New session data with new sessionId
     * @throws {Error} If the HTTP request fails
     * @example
     * const newSession = await sessionsApi.copySession('session-123');
     * console.log(newSession.sessionId);
     */
    async copySession(sessionId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.SESSIONS}/${sessionId}/copy`,
            {
                method: 'POST',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to copy session: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Rename a session with a new title
     * @async
     * @param {string} sessionId - The session identifier
     * @param {string} title - The new title for the session
     * @returns {Promise<Object>} Updated session data
     * @throws {Error} If the HTTP request fails
     * @example
     * await sessionsApi.renameSession('session-123', 'My Research Chat');
     */
    async renameSession(sessionId, title) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.SESSIONS}/${sessionId}/title`,
            {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ title }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to rename session: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Clear all messages from a session
     * @async
     * @param {string} sessionId - The session identifier
     * @returns {Promise<Object>} Confirmation response
     * @throws {Error} If the HTTP request fails
     * @example
     * await sessionsApi.clearSession('session-123');
     */
    async clearSession(sessionId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.SESSIONS}/${sessionId}/clear`,
            {
                method: 'POST',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to clear session: ${response.status}`);
        }

        return await response.json();
    }
};
