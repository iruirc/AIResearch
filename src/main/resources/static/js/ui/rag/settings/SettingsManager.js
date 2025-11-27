/**
 * @fileoverview RAG Settings Manager
 * Handles loading and saving search preferences
 * @module ui/rag/settings/SettingsManager
 */

import { ragApi } from '../../../api/ragApi.js';
import { llmModelApi } from '../../../api/llmModelApi.js';
import { appState } from '../../../state/appState.js';

/**
 * Settings Manager class for managing RAG search preferences
 */
export class SettingsManager {
    /**
     * Create a SettingsManager
     * @param {Object} options - Configuration options
     * @param {Function} options.onSettingsLoaded - Callback when settings are loaded
     */
    constructor(options = {}) {
        this.searchPreferences = null;
        this.onSettingsLoaded = options.onSettingsLoaded || null;
    }

    /**
     * Get current search preferences
     * @returns {Object|null} Search preferences
     */
    getSearchPreferences() {
        return this.searchPreferences;
    }

    /**
     * Load search preferences from API
     */
    async loadSearchPreferences() {
        try {
            console.log('Loading RAG search preferences...');
            this.searchPreferences = await ragApi.getSearchPreferences();

            if (this.onSettingsLoaded) {
                this.onSettingsLoaded(this.searchPreferences);
            }

            return this.searchPreferences;
        } catch (error) {
            console.error('Error loading search preferences:', error);
            // Use defaults if loading fails
            try {
                this.searchPreferences = await ragApi.getDefaultPreferences();
                if (this.onSettingsLoaded) {
                    this.onSettingsLoaded(this.searchPreferences);
                }
                return this.searchPreferences;
            } catch (defaultError) {
                console.error('Error loading default preferences:', defaultError);
                throw defaultError;
            }
        }
    }

    /**
     * Save search preferences
     * @param {Object} preferences - Preferences to save
     */
    async handleSaveSettings(preferences) {
        try {
            console.log('Saving RAG search preferences:', preferences);
            await ragApi.saveSearchPreferences(preferences);
            this.searchPreferences = preferences;
            return true;
        } catch (error) {
            console.error('Error saving search preferences:', error);
            throw error;
        }
    }

    /**
     * Reset search preferences to defaults
     */
    async handleResetSettings() {
        try {
            console.log('Resetting RAG search preferences...');
            const defaults = await ragApi.resetSearchPreferences();
            this.searchPreferences = defaults;

            if (this.onSettingsLoaded) {
                this.onSettingsLoaded(defaults);
            }

            return defaults;
        } catch (error) {
            console.error('Error resetting search preferences:', error);
            throw error;
        }
    }

    /**
     * Load providers list for Cross-Encoder dropdown
     * @param {string} currentProvider - Currently selected provider ID
     * @param {string} currentModel - Currently selected model ID
     * @returns {Promise<Object>} Object with providers array and defaultProvider
     */
    async loadCrossEncoderProviders(currentProvider = null, currentModel = null) {
        try {
            // Get providers from appState or load from API
            let providers = appState.providers;
            if (!providers || providers.length === 0) {
                providers = await llmModelApi.loadProviders();
            }

            // Determine default provider: use provided, or current global, or first available
            let defaultProvider = currentProvider;
            if (!defaultProvider && appState.currentProvider) {
                defaultProvider = appState.currentProvider;
            }
            if (!defaultProvider && providers.length > 0) {
                defaultProvider = providers[0].id;
            }

            return { providers, defaultProvider, currentModel };
        } catch (error) {
            console.error('Error loading Cross-Encoder providers:', error);
            throw error;
        }
    }

    /**
     * Load models for selected provider
     * @param {string} providerId - Provider ID
     * @param {string} currentModel - Currently selected model ID
     * @returns {Promise<Object>} Object with models array and defaultModel
     */
    async loadCrossEncoderModels(providerId, currentModel = null) {
        try {
            const models = await llmModelApi.loadModels(providerId);

            // Determine default model
            let defaultModel = currentModel;

            // If no current model set, try to use the global model (if same provider)
            if (!defaultModel && appState.currentProvider === providerId && appState.currentSettings?.model) {
                defaultModel = appState.currentSettings.model;
            }

            return { models, defaultModel };
        } catch (error) {
            console.error('Error loading Cross-Encoder models:', error);
            throw error;
        }
    }
}
