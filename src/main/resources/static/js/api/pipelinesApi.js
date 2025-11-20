/**
 * @fileoverview Pipelines API module
 * Handles communication with the pipeline management API endpoints
 * @module api/pipelinesApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for pipeline management operations
 * @namespace
 */
export const pipelinesApi = {
    /**
     * Load all available pipelines from the API
     * @async
     * @returns {Promise<Array>} Array of pipeline objects
     * @throws {Error} If the HTTP request fails
     */
    async loadPipelines() {
        const response = await fetchWithTimeout(
            `${API_CONFIG.PIPELINES}/configs`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load pipelines: ${response.status}`);
        }

        const data = await response.json();
        return data.pipelines || [];
    },

    /**
     * Get a specific pipeline by ID
     * @async
     * @param {string} id - The pipeline identifier
     * @returns {Promise<Object>} Pipeline object with full details
     * @throws {Error} If the HTTP request fails
     */
    async getPipeline(id) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.PIPELINES}/config/${id}`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to get pipeline: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Create a new pipeline configuration
     * @async
     * @param {string} name - Pipeline name
     * @param {string} description - Pipeline description
     * @param {Array<string>} assistantIds - Array of assistant IDs in execution order
     * @param {string} providerId - AI provider (e.g., 'CLAUDE')
     * @param {string} model - Optional model override
     * @returns {Promise<Object>} Created pipeline data
     * @throws {Error} If the HTTP request fails
     */
    async createPipeline(name, description, assistantIds, providerId = 'CLAUDE', model = null) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.PIPELINES}/config`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    name,
                    description,
                    assistantIds,
                    providerId,
                    model,
                }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to create pipeline: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Update an existing pipeline configuration
     * @async
     * @param {string} id - Pipeline ID
     * @param {string} name - Pipeline name
     * @param {string} description - Pipeline description
     * @param {Array<string>} assistantIds - Array of assistant IDs in execution order
     * @param {string} providerId - AI provider
     * @param {string} model - Optional model override
     * @returns {Promise<Object>} Updated pipeline data
     * @throws {Error} If the HTTP request fails
     */
    async updatePipeline(id, name, description, assistantIds, providerId = 'CLAUDE', model = null) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.PIPELINES}/config`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    id,
                    name,
                    description,
                    assistantIds,
                    providerId,
                    model,
                }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to update pipeline: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Delete a pipeline
     * @async
     * @param {string} id - The pipeline identifier
     * @returns {Promise<Object>} Deletion confirmation
     * @throws {Error} If the HTTP request fails
     */
    async deletePipeline(id) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.PIPELINES}/config/${id}`,
            {
                method: 'DELETE',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to delete pipeline: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Execute a saved pipeline
     * @async
     * @param {string} pipelineId - Pipeline ID to execute
     * @param {string} initialMessage - Initial user message
     * @param {string} model - Optional model override
     * @returns {Promise<Object>} Execution result
     * @throws {Error} If the HTTP request fails
     */
    async executePipeline(pipelineId, initialMessage, model = null) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.PIPELINES}/execute/${pipelineId}`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    initialMessage,
                    model,
                }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to execute pipeline: ${response.status}`);
        }

        return await response.json();
    }
};
