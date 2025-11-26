/**
 * @fileoverview RAG Test API module
 * Handles communication with the RAG test management API endpoints
 * @module api/ragTestApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for RAG test management operations
 * @namespace
 */
export const ragTestApi = {
    /**
     * Load all RAG tests from the API
     * @async
     * @returns {Promise<Array>} Array of test objects
     * @throws {Error} If the HTTP request fails
     * @example
     * const tests = await ragTestApi.loadTests();
     */
    async loadTests() {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/tests`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load RAG tests: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Get a specific RAG test by ID
     * @async
     * @param {string} testId - The test identifier
     * @returns {Promise<Object>} Test object with full details
     * @throws {Error} If the HTTP request fails
     * @example
     * const test = await ragTestApi.getTest('test-123');
     */
    async getTest(testId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/tests/${testId}`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to get test: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Add a new RAG test
     * @async
     * @param {string} name - Test name
     * @param {Array<Object>} queries - Array of test query objects
     * @param {Object|null} evaluationMetrics - Optional evaluation metrics
     * @returns {Promise<Object>} Created test data
     * @throws {Error} If the HTTP request fails
     * @example
     * const queries = [{ id: 'q1', query: 'Test query', explanation: 'Why this test' }];
     * const test = await ragTestApi.addTest('My Test', queries);
     */
    async addTest(name, queries, evaluationMetrics = null) {
        const body = { name, queries };
        if (evaluationMetrics) {
            body.evaluationMetrics = evaluationMetrics;
        }

        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/tests`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(body),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            if (response.status === 409) {
                throw new Error(`DUPLICATE_NAME:${errorData.error || 'Тест с таким именем уже существует'}`);
            }
            if (response.status === 400 && errorData.details?.invalidQueries) {
                const invalidInfo = errorData.details.invalidQueries
                    .map(q => `Query ${q.index + 1}: missing ${q.missingFields.join(', ')}`)
                    .join('\n');
                throw new Error(`VALIDATION_ERROR:${errorData.error}\n${invalidInfo}`);
            }
            throw new Error(errorData.error || `Failed to add test: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Update an existing RAG test
     * @async
     * @param {string} testId - The test identifier
     * @param {string|null} name - Updated test name (null to keep unchanged)
     * @param {Array<Object>|null} queries - Updated test queries (null to keep unchanged)
     * @param {Object|null} evaluationMetrics - Updated evaluation metrics (null to keep unchanged)
     * @returns {Promise<Object>} Updated test data
     * @throws {Error} If the HTTP request fails
     * @example
     * const queries = [{ id: 'q1', query: 'Updated query', explanation: 'Why' }];
     * const test = await ragTestApi.updateTest('test-123', 'New Name', queries);
     */
    async updateTest(testId, name = null, queries = null, evaluationMetrics = null) {
        const body = {};
        if (name !== null) body.name = name;
        if (queries !== null) body.queries = queries;
        if (evaluationMetrics !== null) body.evaluationMetrics = evaluationMetrics;

        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/tests/${testId}`,
            {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(body),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            if (response.status === 409) {
                throw new Error(`DUPLICATE_NAME:${errorData.error || 'Тест с таким именем уже существует'}`);
            }
            if (response.status === 400 && errorData.details?.invalidQueries) {
                const invalidInfo = errorData.details.invalidQueries
                    .map(q => `Query ${q.index + 1}: missing ${q.missingFields.join(', ')}`)
                    .join('\n');
                throw new Error(`VALIDATION_ERROR:${errorData.error}\n${invalidInfo}`);
            }
            throw new Error(errorData.error || `Failed to update test: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Delete a RAG test
     * @async
     * @param {string} testId - The test identifier
     * @returns {Promise<void>}
     * @throws {Error} If the HTTP request fails
     * @example
     * await ragTestApi.deleteTest('test-123');
     */
    async deleteTest(testId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/tests/${testId}`,
            {
                method: 'DELETE',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to delete test: ${response.status}`);
        }
    },
};
