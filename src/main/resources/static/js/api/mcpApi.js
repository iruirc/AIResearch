// MCP Servers API module
import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for MCP (Model Context Protocol) servers operations
 */
export const mcpApi = {
    /**
     * Load all available MCP servers
     * @returns {Promise<Array>} Array of MCP server objects with connection status and tools
     */
    async loadServers() {
        const response = await fetchWithTimeout(
            API_CONFIG.MCP_SERVERS,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load MCP servers: ${response.status}`);
        }

        const data = await response.json();
        return data.servers || [];
    }
};
