/**
 * @fileoverview RAG Test Manager
 * Handles CRUD operations for tests
 * @module ui/rag/tests/TestManager
 */

import { ragTestApi } from '../../../api/ragTestApi.js';

/**
 * Test Manager class for managing RAG tests
 */
export class TestManager {
    /**
     * Create a TestManager
     * @param {Object} options - Configuration options
     * @param {Function} options.onTestsLoaded - Callback when tests are loaded
     */
    constructor(options = {}) {
        this.tests = [];
        this.onTestsLoaded = options.onTestsLoaded || null;
    }

    /**
     * Get all tests
     * @returns {Array} Tests array
     */
    getTests() {
        return this.tests;
    }

    /**
     * Load all RAG tests from the API
     */
    async loadTests() {
        try {
            console.log('Loading RAG tests...');
            this.tests = await ragTestApi.loadTests();
            console.log(`Loaded ${this.tests.length} tests`);

            if (this.onTestsLoaded) {
                this.onTestsLoaded(this.tests);
            }

            return this.tests;
        } catch (error) {
            console.error('Error loading RAG tests:', error);
            throw error;
        }
    }

    /**
     * Get a specific test by ID
     * @param {string} testId - Test ID
     * @returns {Promise<Object>} Test object
     */
    async getTest(testId) {
        try {
            return await ragTestApi.getTest(testId);
        } catch (error) {
            console.error('Error loading test:', error);
            throw error;
        }
    }

    /**
     * Add a new test
     * @param {string} name - Test name
     * @param {Array} queries - Test queries
     * @param {Object|null} evaluationMetrics - Optional evaluation metrics
     */
    async addTest(name, queries, evaluationMetrics = null) {
        try {
            console.log(`Adding test: ${name} with ${queries.length} queries`);
            await ragTestApi.addTest(name, queries, evaluationMetrics);
            console.log('Test added successfully');
            await this.loadTests();
        } catch (error) {
            console.error('Error adding test:', error);
            throw error;
        }
    }

    /**
     * Update an existing test
     * @param {string} testId - Test ID to update
     * @param {string} name - New test name
     * @param {Array|null} queries - New queries (null to keep existing)
     * @param {Object|null} evaluationMetrics - New evaluation metrics
     */
    async updateTest(testId, name, queries = null, evaluationMetrics = null) {
        try {
            console.log(`Updating test: ${testId}`);
            await ragTestApi.updateTest(testId, name, queries, evaluationMetrics);
            console.log('Test updated successfully');
            await this.loadTests();
        } catch (error) {
            console.error('Error updating test:', error);
            throw error;
        }
    }

    /**
     * Delete a test
     * @param {string} testId - Test ID to delete
     */
    async deleteTest(testId) {
        try {
            console.log(`Deleting test: ${testId}`);
            await ragTestApi.deleteTest(testId);
            console.log('Test deleted successfully');
            await this.loadTests();
        } catch (error) {
            console.error('Error deleting test:', error);
            throw error;
        }
    }

    /**
     * Execute a test
     * @param {string} testId - Test ID to execute
     * @param {Object} callbacks - SSE event callbacks
     * @returns {Object} Execution handle with cancel method
     */
    executeTest(testId, callbacks) {
        return ragTestApi.executeTest(testId, callbacks);
    }
}
