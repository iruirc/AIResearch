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
     * @returns {Promise<Array>} Array of MCP server objects with name, connected status, enabled status, and tools
     * @throws {Error} If the HTTP request fails
     * @example
     * const servers = await mcpApi.loadServers();
     * console.log(servers); // [{ id: 'github', name: 'GitHub', connected: true, enabled: true, tools: [...] }]
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
    },

    /**
     * Enable a specific MCP server
     * @async
     * @param {string} serverId - ID of the server to enable
     * @returns {Promise<Object>} Response with success status and message
     * @throws {Error} If the HTTP request fails
     * @example
     * const result = await mcpApi.enableServer('github');
     * console.log(result); // { success: true, message: 'Server enabled successfully' }
     */
    async enableServer(serverId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.MCP_SERVERS}/${serverId}/enable`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to enable server: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Disable a specific MCP server
     * @async
     * @param {string} serverId - ID of the server to disable
     * @returns {Promise<Object>} Response with success status and message
     * @throws {Error} If the HTTP request fails
     * @example
     * const result = await mcpApi.disableServer('github');
     * console.log(result); // { success: true, message: 'Server disabled successfully' }
     */
    async disableServer(serverId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.MCP_SERVERS}/${serverId}/disable`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to disable server: ${response.status}`);
        }

        return await response.json();
    }
};
