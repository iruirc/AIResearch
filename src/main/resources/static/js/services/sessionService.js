// Session Service - orchestrates session operations
import { sessionsApi } from '../api/sessionsApi.js';
import { assistantsApi } from '../api/assistantsApi.js';
import { appState } from '../state/appState.js';

/**
 * Service for managing chat sessions
 * Handles session lifecycle, loading, switching, and CRUD operations
 */
export const sessionService = {
    /**
     * Load all sessions and update state
     * @returns {Promise<Array>} Array of sessions
     */
    async loadSessions() {
        try {
            const sessions = await sessionsApi.loadSessions();
            appState.setSessions(sessions);
            return sessions;
        } catch (error) {
            console.error('Error loading sessions:', error);
            throw error;
        }
    },

    /**
     * Switch to a different session
     * @param {string} sessionId - Session ID to switch to
     * @returns {Promise<Object>} Session data with messages
     */
    async switchSession(sessionId) {
        if (appState.isLoading) {
            return null;
        }

        try {
            const sessionData = await sessionsApi.getSession(sessionId);

            // Update state only if switching to different session
            if (sessionId !== appState.currentSessionId) {
                appState.setCurrentSessionId(sessionId);
            }

            appState.setSessionTotalTokens(0);

            // Calculate total tokens from history
            if (sessionData.messages && sessionData.messages.length > 0) {
                let totalTokens = 0;
                sessionData.messages.forEach(msg => {
                    if (msg.metadata && msg.metadata.totalTokens) {
                        totalTokens += msg.metadata.totalTokens;
                    }
                });
                appState.setSessionTotalTokens(totalTokens);
            }

            return sessionData;
        } catch (error) {
            console.error('Error switching session:', error);
            throw error;
        }
    },

    /**
     * Create a new empty session on the server
     * @returns {Promise<Object>} Created session data with sessionId
     */
    async createSession() {
        try {
            const result = await sessionsApi.createSession();

            // Update current session ID in state
            if (result.sessionId) {
                appState.setCurrentSessionId(result.sessionId);
            }

            return result;
        } catch (error) {
            console.error('Error creating session:', error);
            throw error;
        }
    },

    /**
     * Create a new chat session (client-side only, clears state)
     * @returns {Promise<void>}
     */
    async createNewSession() {
        // Reset current session
        appState.resetSession();
    },

    /**
     * Delete a session
     * @param {string} sessionId - Session ID to delete
     * @returns {Promise<void>}
     */
    async deleteSession(sessionId) {
        if (appState.isLoading || !sessionId) {
            return;
        }

        try {
            await sessionsApi.deleteSession(sessionId);

            // If deleting current session, reset it
            if (sessionId === appState.currentSessionId) {
                appState.resetSession();
            }

            // Reload sessions list
            await this.loadSessions();
        } catch (error) {
            console.error('Error deleting session:', error);
            throw error;
        }
    },

    /**
     * Copy a session
     * @param {string} sessionId - Session ID to copy
     * @returns {Promise<Object>} New session data
     */
    async copySession(sessionId) {
        if (appState.isLoading) {
            return null;
        }

        try {
            const result = await sessionsApi.copySession(sessionId);

            // Reload sessions list
            await this.loadSessions();

            return result;
        } catch (error) {
            console.error('Error copying session:', error);
            throw error;
        }
    },

    /**
     * Rename a session
     * @param {string} sessionId - Session ID to rename
     * @param {string} newTitle - New title for the session
     * @returns {Promise<void>}
     */
    async renameSession(sessionId, newTitle) {
        if (appState.isLoading || !newTitle.trim()) {
            throw new Error('Invalid session title');
        }

        try {
            await sessionsApi.renameSession(sessionId, newTitle.trim());

            // Reload sessions list
            await this.loadSessions();
        } catch (error) {
            console.error('Error renaming session:', error);
            throw error;
        }
    },

    /**
     * Start a new session with an assistant
     * @param {string} assistantId - Assistant ID to start session with
     * @returns {Promise<string>} New session ID
     */
    async startAssistantSession(assistantId) {
        if (appState.isLoading) {
            return null;
        }

        try {
            appState.setLoading(true);

            const result = await assistantsApi.startAssistantSession(assistantId);

            // Update current session
            appState.setCurrentSessionId(result.sessionId);
            appState.setSessionTotalTokens(0);

            // Reload sessions list
            await this.loadSessions();

            return result.sessionId;
        } catch (error) {
            console.error('Error starting assistant session:', error);
            throw error;
        } finally {
            appState.setLoading(false);
        }
    }
};
