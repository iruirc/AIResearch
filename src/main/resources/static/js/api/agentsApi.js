/**
 * @fileoverview Agents API module
 * Handles communication with the agents management API endpoints
 * @module api/agentsApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for agents management operations
 * @namespace
 */
export const agentsApi = {
    /**
     * Load all available agents from the API
     * @async
     * @returns {Promise<Array>} Array of agent objects with id, name, and description
     * @throws {Error} If the HTTP request fails
     * @example
     * const agents = await agentsApi.loadAgents();
     * console.log(agents); // [{ id: 'agent1', name: 'Assistant', description: '...' }]
     */
    async loadAgents() {
        const response = await fetchWithTimeout(
            API_CONFIG.AGENTS,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load agents: ${response.status}`);
        }

        const data = await response.json();
        return data.agents || [];
    },

    /**
     * Start a new chat session with a specific agent
     * @async
     * @param {string} agentId - The agent identifier
     * @returns {Promise<Object>} New session data containing sessionId
     * @throws {Error} If the HTTP request fails
     * @example
     * const session = await agentsApi.startAgentSession('research-agent');
     * console.log(session.sessionId); // 'new-session-id'
     */
    async startAgentSession(agentId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.AGENTS}/start`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ agentId }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to start agent session: ${response.status}`);
        }

        return await response.json();
    }
};
