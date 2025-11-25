/**
 * @fileoverview RAG (Retrieval-Augmented Generation) API module
 * Handles communication with the RAG document management API endpoints
 * @module api/ragApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for RAG document management operations
 * @namespace
 */
export const ragApi = {
    /**
     * Load all RAG documents from the API
     * @async
     * @returns {Promise<Array>} Array of document objects
     * @throws {Error} If the HTTP request fails
     * @example
     * const documents = await ragApi.loadDocuments();
     */
    async loadDocuments() {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/documents`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to load RAG documents: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Get a specific RAG document by ID
     * @async
     * @param {string} documentId - The document identifier
     * @returns {Promise<Object>} Document object with full details
     * @throws {Error} If the HTTP request fails
     * @example
     * const document = await ragApi.getDocument('doc-123');
     */
    async getDocument(documentId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/documents/${documentId}`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to get document: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Add a new RAG document
     * @async
     * @param {string} name - Document name
     * @param {string} content - Document content (text)
     * @param {string} chunkingStrategy - Chunking strategy (FIXED_SIZE, RECURSIVE, SEMANTIC)
     * @param {boolean} enabled - Whether the document should be enabled
     * @returns {Promise<Object>} Created document data
     * @throws {Error} If the HTTP request fails
     * @example
     * const document = await ragApi.addDocument('My Document', 'Content...', 'FIXED_SIZE', true);
     */
    async addDocument(name, content, chunkingStrategy = 'FIXED_SIZE', enabled = true) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/documents`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ name, content, chunkingStrategy, enabled }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            if (response.status === 409) {
                throw new Error(`DUPLICATE_NAME:${errorData.error || 'Знание с таким именем уже существует'}`);
            }
            throw new Error(errorData.error || `Failed to add document: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Update an existing RAG document
     * @async
     * @param {string} documentId - The document identifier
     * @param {string|null} name - Updated document name (null to keep unchanged)
     * @param {boolean|null} enabled - Updated enabled state (null to keep unchanged)
     * @param {string|null} chunkingStrategy - Updated chunking strategy (null to keep unchanged)
     * @returns {Promise<Object>} Updated document data
     * @throws {Error} If the HTTP request fails
     * @example
     * const document = await ragApi.updateDocument('doc-123', 'New Name', true, null);
     */
    async updateDocument(documentId, name = null, enabled = null, chunkingStrategy = null) {
        const body = {};
        if (name !== null) body.name = name;
        if (enabled !== null) body.enabled = enabled;
        if (chunkingStrategy !== null) body.chunkingStrategy = chunkingStrategy;

        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/documents/${documentId}`,
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
                throw new Error(`DUPLICATE_NAME:${errorData.error || 'Знание с таким именем уже существует'}`);
            }
            throw new Error(errorData.error || `Failed to update document: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Delete a RAG document
     * @async
     * @param {string} documentId - The document identifier
     * @returns {Promise<void>}
     * @throws {Error} If the HTTP request fails
     * @example
     * await ragApi.deleteDocument('doc-123');
     */
    async deleteDocument(documentId) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/documents/${documentId}`,
            {
                method: 'DELETE',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to delete document: ${response.status}`);
        }
    },

    /**
     * Search for relevant context using RAG
     * @async
     * @param {string} query - Search query
     * @param {number} topK - Number of results to return
     * @param {number} minScore - Minimum similarity score (0.0-1.0)
     * @returns {Promise<Array>} Array of relevant chunks with scores
     * @throws {Error} If the HTTP request fails
     * @example
     * const results = await ragApi.search('What is RAG?', 5, 0.7);
     */
    async search(query, topK = 5, minScore = 0.7) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/search`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ query, topK, minScore }),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Failed to search: ${response.status}`);
        }

        return await response.json();
    }
};
