/**
 * @fileoverview RAG Preview Data Manager
 * Handles data loading and state management for RAG preview modal
 * @module ui/ragPreview/RagPreviewDataManager
 */

import { ragApi } from '../../api/ragApi.js';

/**
 * RAG Preview Data Manager class
 * Responsible for loading preview data and managing state
 */
export class RagPreviewDataManager {
    constructor() {
        this.currentQuery = '';
        this.previewData = null;
    }

    /**
     * Get current query
     * @returns {string} Current query text
     */
    getCurrentQuery() {
        return this.currentQuery;
    }

    /**
     * Get preview data
     * @returns {Object|null} Preview data
     */
    getPreviewData() {
        return this.previewData;
    }

    /**
     * Set current query
     * @param {string} query - Query text
     */
    setCurrentQuery(query) {
        this.currentQuery = query;
    }

    /**
     * Load preview data from API
     * @param {string} query - Query text
     * @returns {Promise<Object>} Preview data
     * @throws {Error} If loading fails
     */
    async loadPreviewData(query) {
        this.currentQuery = query;

        try {
            const data = await ragApi.previewContext(query);
            this.previewData = data;
            return data;
        } catch (error) {
            console.error('Failed to load preview data:', error);
            throw error;
        }
    }

    /**
     * Get query from message input
     * @returns {string|null} Query text or null if empty
     */
    getQueryFromInput() {
        const messageInput = document.getElementById('messageInput');
        if (!messageInput) return null;

        const query = messageInput.value.trim();
        return query || null;
    }

    /**
     * Clear cached data
     */
    clearData() {
        this.currentQuery = '';
        this.previewData = null;
    }
}
