// Sessions API module
import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for session management operations
 */
export const sessionsApi = {
    /**
     * Create a new empty session
     * @returns {Promise<Object>} Object with sessionId
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
     * Load all sessions
     * @returns {Promise<Array>} Array of session objects
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
     * Get session history by ID
     * @param {string} sessionId - Session identifier
     * @returns {Promise<Object>} Session data with messages
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
     * Delete a session
     * @param {string} sessionId - Session identifier
     * @returns {Promise<void>}
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
     * Copy a session
     * @param {string} sessionId - Session identifier to copy
     * @returns {Promise<Object>} New session data
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
     * Rename a session
     * @param {string} sessionId - Session identifier
     * @param {string} title - New title for the session
     * @returns {Promise<Object>} Updated session data
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
     * Clear session messages
     * @param {string} sessionId - Session identifier
     * @returns {Promise<void>}
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
