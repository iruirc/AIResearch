/**
 * @fileoverview Preferences API module
 * Handles communication with user preferences API endpoints
 * @module api/preferencesApi
 */

import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

/**
 * API module for user preferences operations
 * @namespace
 */
export const preferencesApi = {
    /**
     * Get current user preferences
     * @async
     * @returns {Promise<Object>} Preferences object
     * @throws {Error} If the HTTP request fails
     */
    async getPreferences() {
        const response = await fetchWithTimeout(
            API_CONFIG.PREFERENCES,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to get preferences: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Save user preferences
     * @async
     * @param {Object} preferences - Preferences object to save
     * @param {string} preferences.providerId - Provider ID
     * @param {string} preferences.model - Model ID
     * @param {number} preferences.temperature - Temperature setting
     * @param {number} preferences.maxTokens - Max tokens setting
     * @param {string} preferences.format - Response format
     * @returns {Promise<Object>} Save result with preferences
     * @throws {Error} If the HTTP request fails
     */
    async savePreferences(preferences) {
        const response = await fetchWithTimeout(
            API_CONFIG.PREFERENCES,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(preferences),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.error || `Failed to save preferences: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Reset preferences to defaults
     * @async
     * @returns {Promise<Object>} Reset result with default preferences
     * @throws {Error} If the HTTP request fails
     */
    async resetToDefaults() {
        const response = await fetchWithTimeout(
            API_CONFIG.PREFERENCES,
            {
                method: 'DELETE',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.error || `Failed to reset preferences: ${response.status}`);
        }

        return await response.json();
    },

    /**
     * Get default preferences without loading from storage
     * @async
     * @returns {Promise<Object>} Default preferences
     * @throws {Error} If the HTTP request fails
     */
    async getDefaults() {
        const response = await fetchWithTimeout(
            `${API_CONFIG.PREFERENCES}/defaults`,
            {
                method: 'GET',
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`Failed to get default preferences: ${response.status}`);
        }

        return await response.json();
    }
};
