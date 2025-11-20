/**
 * @fileoverview Application state management
 * Centralized state management with observer pattern for reactive updates
 * @module state/appState
 */

import { DEFAULT_SETTINGS } from '../config.js';

/**
 * Application state manager using observer pattern
 * Provides centralized state with subscription-based notifications
 * @class
 */
class AppState {
    /**
     * Creates a new AppState instance
     */
    constructor() {
        this.isLoading = false;
        this.loadingMessageId = null;
        this.currentSessionId = null;
        this.sessions = [];
        this.assistants = [];
        this.providers = [];
        this.models = [];
        this.mcpServers = [];
        this.currentProvider = null;
        this.requestStartTime = null;
        this.timerInterval = null;
        this.sessionTotalTokens = 0;
        this.currentContextWindow = DEFAULT_SETTINGS.contextWindow;
        this.isSidebarCollapsed = false;
        this.currentSettings = { ...DEFAULT_SETTINGS };

        /**
         * Event listeners for state changes
         * @type {Object.<string, Function[]>}
         * @private
         */
        this.listeners = {};
    }

    /**
     * Subscribe to state changes for a specific key
     * @param {string} key - The state property key to observe
     * @param {Function} callback - Function called when the state changes
     * @example
     * appState.subscribe('loading', (isLoading) => {
     *   console.log('Loading state:', isLoading);
     * });
     */
    subscribe(key, callback) {
        if (!this.listeners[key]) {
            this.listeners[key] = [];
        }
        this.listeners[key].push(callback);
    }

    /**
     * Notify all subscribers of a state change
     * @param {string} key - The state property key that changed
     * @private
     */
    notify(key) {
        // Map notification key to actual property name
        const propertyMap = {
            'loading': 'isLoading',
            'sessions': 'sessions',
            'assistants': 'assistants',
            'providers': 'providers',
            'models': 'models',
            'mcpServers': 'mcpServers',
            'currentSessionId': 'currentSessionId',
            'currentSettings': 'currentSettings',
            'sessionTotalTokens': 'sessionTotalTokens'
        };

        const propertyName = propertyMap[key] || key;
        const value = this[propertyName];

        if (this.listeners[key]) {
            this.listeners[key].forEach(callback => callback(value));
        }
    }

    /**
     * Get the current state snapshot
     * @returns {Object} Current state object with all properties
     */
    getState() {
        return {
            loading: this.isLoading,
            currentSessionId: this.currentSessionId,
            sessions: this.sessions,
            assistants: this.assistants,
            providers: this.providers,
            models: this.models,
            mcpServers: this.mcpServers,
            currentProvider: this.currentProvider,
            sessionTotalTokens: this.sessionTotalTokens,
            currentContextWindow: this.currentContextWindow,
            isSidebarCollapsed: this.isSidebarCollapsed,
            settings: this.currentSettings,
            loadingMessageId: this.loadingMessageId
        };
    }

    /**
     * Update state with partial updates (without triggering notifications)
     * @param {Object} updates - Object containing properties to update
     */
    setState(updates) {
        Object.keys(updates).forEach(key => {
            if (this.hasOwnProperty(key)) {
                this[key] = updates[key];
            }
        });
    }

    /**
     * Set loading state and notify subscribers
     * @param {boolean} value - Loading state
     */
    setLoading(value) {
        this.isLoading = value;
        this.notify('loading');
    }

    /**
     * Set current session ID and notify subscribers
     * @param {string|null} value - Session ID
     */
    setCurrentSessionId(value) {
        this.currentSessionId = value;
        this.notify('currentSessionId');
    }

    /**
     * Set sessions list and notify subscribers
     * @param {Array} value - Array of session objects
     */
    setSessions(value) {
        this.sessions = value;
        this.notify('sessions');
    }

    /**
     * Set assistants list and notify subscribers
     * @param {Array} value - Array of assistant objects
     */
    setAssistants(value) {
        this.assistants = value;
        this.notify('assistants');
    }

    /**
     * Set providers list and notify subscribers
     * @param {Array} value - Array of provider objects
     */
    setProviders(value) {
        this.providers = value;
        this.notify('providers');
    }

    /**
     * Set models list and notify subscribers
     * @param {Array} value - Array of model objects
     */
    setModels(value) {
        this.models = value;
        this.notify('models');
    }

    /**
     * Set MCP servers list and notify subscribers
     * @param {Array} value - Array of MCP server objects
     */
    setMcpServers(value) {
        this.mcpServers = value;
        this.notify('mcpServers');
    }

    /**
     * Update current settings (partial merge) and notify subscribers
     * @param {Object} value - Settings object to merge with current settings
     */
    setCurrentSettings(value) {
        this.currentSettings = { ...this.currentSettings, ...value };
        this.notify('currentSettings');
    }

    /**
     * Set session total tokens and notify subscribers
     * @param {number} value - Total token count for the session
     */
    setSessionTotalTokens(value) {
        this.sessionTotalTokens = value;
        this.notify('sessionTotalTokens');
    }

    /**
     * Increment session total tokens by amount and notify subscribers
     * @param {number} amount - Number of tokens to add
     */
    incrementSessionTotalTokens(amount) {
        this.sessionTotalTokens += amount;
        this.notify('sessionTotalTokens');
    }

    /**
     * Reset session state (clear current session and tokens)
     */
    resetSession() {
        this.currentSessionId = null;
        this.sessionTotalTokens = 0;
        this.notify('currentSessionId');
        this.notify('sessionTotalTokens');
    }
}

/**
 * Singleton instance of AppState
 * @type {AppState}
 */
export const appState = new AppState();
