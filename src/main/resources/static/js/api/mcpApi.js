/**
 * @fileoverview MCP Servers API module
 * Handles communication with Model Context Protocol (MCP) servers API
 * @module api/mcpApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for MCP (Model Context Protocol) servers operations
 * @namespace
 */
export const mcpApi = {
    /**
     * Load all available MCP servers and their status
     * @async
     * @returns {Promise<Array>} Array of MCP server objects with name, connected status, and tools
     * @throws {Error} If the HTTP request fails
     * @example
     * const servers = await mcpApi.loadServers();
     * console.log(servers); // [{ name: 'server1', connected: true, tools: [...] }]
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
