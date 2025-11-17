// Application state management
import { DEFAULT_SETTINGS } from '../config.js';

class AppState {
    constructor() {
        this.isLoading = false;
        this.loadingMessageId = null;
        this.currentSessionId = null;
        this.sessions = [];
        this.agents = [];
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

        // Event listeners for state changes
        this.listeners = {};
    }

    // Subscribe to state changes
    subscribe(key, callback) {
        if (!this.listeners[key]) {
            this.listeners[key] = [];
        }
        this.listeners[key].push(callback);
    }

    // Notify listeners
    notify(key) {
        // Map notification key to actual property name
        const propertyMap = {
            'loading': 'isLoading',
            'sessions': 'sessions',
            'agents': 'agents',
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

    // Get current state
    getState() {
        return {
            loading: this.isLoading,
            currentSessionId: this.currentSessionId,
            sessions: this.sessions,
            agents: this.agents,
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

    // Set state (for partial updates)
    setState(updates) {
        Object.keys(updates).forEach(key => {
            if (this.hasOwnProperty(key)) {
                this[key] = updates[key];
            }
        });
    }

    // Setters with notifications
    setLoading(value) {
        this.isLoading = value;
        this.notify('loading');
    }

    setCurrentSessionId(value) {
        this.currentSessionId = value;
        this.notify('currentSessionId');
    }

    setSessions(value) {
        this.sessions = value;
        this.notify('sessions');
    }

    setAgents(value) {
        this.agents = value;
        this.notify('agents');
    }

    setProviders(value) {
        this.providers = value;
        this.notify('providers');
    }

    setModels(value) {
        this.models = value;
        this.notify('models');
    }

    setMcpServers(value) {
        this.mcpServers = value;
        this.notify('mcpServers');
    }

    setCurrentSettings(value) {
        this.currentSettings = { ...this.currentSettings, ...value };
        this.notify('currentSettings');
    }

    setSessionTotalTokens(value) {
        this.sessionTotalTokens = value;
        this.notify('sessionTotalTokens');
    }

    incrementSessionTotalTokens(amount) {
        this.sessionTotalTokens += amount;
        this.notify('sessionTotalTokens');
    }

    resetSession() {
        this.currentSessionId = null;
        this.sessionTotalTokens = 0;
        this.notify('currentSessionId');
        this.notify('sessionTotalTokens');
    }
}

// Export singleton instance
export const appState = new AppState();
