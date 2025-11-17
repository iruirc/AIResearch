// Agents API module
import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for agents management operations
 */
export const agentsApi = {
    /**
     * Load all available agents
     * @returns {Promise<Array>} Array of agent objects
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
     * Start a new session with an agent
     * @param {string} agentId - Agent identifier
     * @returns {Promise<Object>} New session data with sessionId
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
