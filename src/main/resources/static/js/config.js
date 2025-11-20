/**
 * @fileoverview Application configuration module
 * Contains API endpoints, default settings, UI configuration, and compression strategies
 * @module config
 */

/**
 * API endpoint configuration
 * @typedef {Object} ApiConfig
 * @property {string} CHAT - Chat endpoint for sending messages
 * @property {string} SESSIONS - Sessions endpoint for managing chat sessions
 * @property {string} ASSISTANTS - Assistants endpoint for AI assistants
 * @property {string} MODELS - Models endpoint for available AI models
 * @property {string} PROVIDERS - Providers endpoint for AI providers
 * @property {string} CONFIG - Configuration endpoint
 * @property {string} COMPRESSION - Compression endpoint for chat history compression
 * @property {string} MCP_SERVERS - Model Context Protocol servers endpoint
 * @property {string} PIPELINES - Pipeline configurations endpoint
 * @property {number} REQUEST_TIMEOUT - Request timeout in milliseconds (5 minutes)
 */
export const API_CONFIG = {
    CHAT: '/chat',
    SESSIONS: '/sessions',
    ASSISTANTS: '/assistants',
    MODELS: '/models',
    PROVIDERS: '/providers',
    CONFIG: '/config',
    COMPRESSION: '/compression',
    MCP_SERVERS: '/mcp/servers',
    PIPELINES: '/api/v2/pipeline',
    REQUEST_TIMEOUT: 300000, // 5 minutes
};

/**
 * Default application settings
 * @typedef {Object} DefaultSettings
 * @property {string} model - Default AI model identifier
 * @property {number} temperature - Default temperature for generation (0.0-2.0)
 * @property {number} maxTokens - Default maximum tokens for responses
 * @property {string} format - Default response format (PLAIN_TEXT, MARKDOWN, etc.)
 * @property {number} contextWindow - Default context window size in tokens
 * @property {string} providerId - Default AI provider (lowercase to match backend API)
 */
export const DEFAULT_SETTINGS = {
    model: 'claude-haiku-4-5-20251001',
    temperature: 1.0,
    maxTokens: 4096,
    format: 'PLAIN_TEXT',
    contextWindow: 200000,
    providerId: 'claude', // Default provider (lowercase to match backend API)
};

/**
 * UI behavior configuration
 * @typedef {Object} UiConfig
 * @property {number} TIMER_UPDATE_INTERVAL - Loading timer update interval in milliseconds
 * @property {number} STATUS_DISPLAY_DURATION - Status message display duration in milliseconds
 * @property {number} MAX_MESSAGE_INPUT_HEIGHT - Maximum height for message input in pixels
 */
export const UI_CONFIG = {
    TIMER_UPDATE_INTERVAL: 100, // ms
    STATUS_DISPLAY_DURATION: 3000, // ms
    MAX_MESSAGE_INPUT_HEIGHT: 120, // px
};

/**
 * Compression strategy configuration
 * @typedef {Object} CompressionConfig
 * @property {Object} STRATEGIES - Available compression strategies
 * @property {string} STRATEGIES.FULL_REPLACEMENT - Replace all messages with AI summary
 * @property {string} STRATEGIES.SLIDING_WINDOW - Keep recent messages, summarize old ones
 * @property {string} STRATEGIES.TOKEN_BASED - Compress based on token count thresholds
 */
export const COMPRESSION_CONFIG = {
    STRATEGIES: {
        FULL_REPLACEMENT: 'FULL_REPLACEMENT',
        SLIDING_WINDOW: 'SLIDING_WINDOW',
        TOKEN_BASED: 'TOKEN_BASED',
    },
};
