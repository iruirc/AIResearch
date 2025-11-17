// API Configuration
export const API_CONFIG = {
    CHAT: '/chat',
    SESSIONS: '/sessions',
    AGENTS: '/agents',
    MODELS: '/models',
    PROVIDERS: '/providers',
    CONFIG: '/config',
    COMPRESSION: '/compression',
    MCP_SERVERS: '/mcp/servers',
    REQUEST_TIMEOUT: 300000, // 5 minutes
};

// Default Settings
export const DEFAULT_SETTINGS = {
    model: 'claude-haiku-4-5-20251001',
    temperature: 1.0,
    maxTokens: 4096,
    format: 'PLAIN_TEXT',
    contextWindow: 200000,
};

// UI Configuration
export const UI_CONFIG = {
    TIMER_UPDATE_INTERVAL: 100, // ms
    STATUS_DISPLAY_DURATION: 3000, // ms
    MAX_MESSAGE_INPUT_HEIGHT: 120, // px
};

// Compression Configuration
export const COMPRESSION_CONFIG = {
    STRATEGIES: {
        FULL_REPLACEMENT: 'FULL_REPLACEMENT',
        SLIDING_WINDOW: 'SLIDING_WINDOW',
        TOKEN_BASED: 'TOKEN_BASED',
    },
};
