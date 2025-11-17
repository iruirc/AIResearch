// Compression Service - manages chat compression workflow
import { compressionApi } from '../api/compressionApi.js';
import { appState } from '../state/appState.js';

/**
 * Service for managing chat compression operations
 * Handles compression workflow, strategy selection, and result processing
 */
export const compressionService = {
    /**
     * Get compression configuration and statistics for a session
     * @param {string} sessionId - Session identifier
     * @returns {Promise<Object>} Compression config with stats
     */
    async getCompressionInfo(sessionId) {
        if (!sessionId) {
            throw new Error('No active session for compression');
        }

        try {
            const data = await compressionApi.getConfig(sessionId);
            return {
                currentMessageCount: data.currentMessageCount || 0,
                totalTokens: data.totalTokens || 0,
                config: data.config || {}
            };
        } catch (error) {
            console.error('Error loading compression info:', error);
            throw error;
        }
    },

    /**
     * Update compression strategy for a session
     * @param {string} sessionId - Session identifier
     * @param {string} strategy - Compression strategy name
     * @returns {Promise<void>}
     */
    async updateCompressionStrategy(sessionId, strategy) {
        if (!sessionId) {
            throw new Error('No active session');
        }

        try {
            // Get current config
            const currentInfo = await this.getCompressionInfo(sessionId);

            // Update only strategy, preserve other parameters
            const updatedConfig = {
                ...currentInfo.config,
                strategy
            };

            await compressionApi.updateConfig(sessionId, updatedConfig);
        } catch (error) {
            console.error('Error updating compression strategy:', error);
            throw error;
        }
    },

    /**
     * Compress session messages
     * @param {string} sessionId - Session identifier
     * @param {Object} options - Compression options
     * @param {string} options.strategy - Compression strategy
     * @param {string} options.providerId - Provider ID (default: 'CLAUDE')
     * @param {string} options.model - Model to use
     * @param {number} options.contextWindowSize - Context window size
     * @returns {Promise<Object>} Compression result
     */
    async compressSession(sessionId, options = {}) {
        if (!sessionId) {
            throw new Error('No active session for compression');
        }

        try {
            // First, update compression strategy if provided
            if (options.strategy) {
                await this.updateCompressionStrategy(sessionId, options.strategy);
            }

            // Perform compression
            const result = await compressionApi.compress(sessionId, {
                providerId: options.providerId || appState.currentProvider || 'CLAUDE',
                model: options.model || appState.currentSettings.model,
                contextWindowSize: options.contextWindowSize || appState.currentContextWindow
            });

            // Reset session total tokens after compression
            if (result.success && result.compressionPerformed) {
                appState.setSessionTotalTokens(0);
            }

            return result;
        } catch (error) {
            console.error('Error compressing session:', error);
            throw error;
        }
    },

    /**
     * Get archived messages for a session
     * @param {string} sessionId - Session identifier
     * @returns {Promise<Object>} Archived messages data
     */
    async getArchivedMessages(sessionId) {
        if (!sessionId) {
            throw new Error('No active session');
        }

        try {
            return await compressionApi.getArchivedMessages(sessionId);
        } catch (error) {
            console.error('Error loading archived messages:', error);
            throw error;
        }
    },

    /**
     * Check if compression is recommended for a session
     * @param {number} messageCount - Current message count
     * @param {number} tokenCount - Current token count
     * @param {number} contextWindow - Context window size
     * @returns {boolean} True if compression is recommended
     */
    shouldCompress(messageCount, tokenCount, contextWindow = 200000) {
        // Compress if message count exceeds threshold
        if (messageCount >= 10) {
            return true;
        }

        // Compress if token usage exceeds 80% of context window
        if (tokenCount >= contextWindow * 0.8) {
            return true;
        }

        return false;
    }
};
