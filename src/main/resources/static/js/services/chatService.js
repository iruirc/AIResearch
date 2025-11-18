// Chat Service - manages message sending and chat workflow
import { chatApi } from '../api/chatApi.js';
import { appState } from '../state/appState.js';

/**
 * Service for managing chat operations
 * Handles message sending, response processing, and state updates
 */
export const chatService = {
    /**
     * Send a message to the chat
     * @param {string} message - User message text
     * @returns {Promise<Object>} Response data with response text, sessionId, tokens info
     */
    async sendMessage(message) {
        if (!message || appState.isLoading) {
            return null;
        }

        const startTime = Date.now();
        const wasNewChat = !appState.currentSessionId;

        try {
            appState.setLoading(true);

            // Get current settings from state
            const settings = appState.currentSettings;
            const sessionId = appState.currentSessionId;

            // Send message via API
            const data = await chatApi.sendMessage(message, sessionId, settings);

            // Update session ID IMMEDIATELY if this was a new chat
            // This triggers the state change listener to reload sessions
            if (wasNewChat && data.sessionId) {
                appState.setCurrentSessionId(data.sessionId);
            }

            // Calculate elapsed time
            const elapsedTime = ((Date.now() - startTime) / 1000).toFixed(2);

            // Update session ID if returned (for existing sessions)
            if (!wasNewChat && data.sessionId) {
                appState.setCurrentSessionId(data.sessionId);
            }

            // Process token details
            let metadata = {
                time: elapsedTime,
                model: settings.model,
                tokens: data.tokensUsed || 'N/A'
            };

            if (data.tokenDetails) {
                metadata = {
                    ...metadata,
                    inputTokens: data.tokenDetails.inputTokens,
                    outputTokens: data.tokenDetails.outputTokens,
                    totalTokens: data.tokenDetails.totalTokens,
                    estimatedInputTokens: data.tokenDetails.estimatedInputTokens,
                    estimatedOutputTokens: data.tokenDetails.estimatedOutputTokens,
                    estimatedTotalTokens: data.tokenDetails.estimatedTotalTokens
                };

                // Update session total tokens
                appState.incrementSessionTotalTokens(data.tokenDetails.totalTokens);
            }

            // Add context window info
            metadata.contextWindow = appState.currentContextWindow;
            metadata.sessionTotalTokens = appState.sessionTotalTokens;

            return {
                response: data.response,
                sessionId: data.sessionId,
                metadata,
                wasNewChat
            };

        } catch (error) {
            console.error('Error sending message:', error);

            // Determine error type
            let errorMessage = 'Что-то пошло не так';

            if (error.message === 'AbortError') {
                errorMessage = 'Время ожидания ответа истекло';
            } else if (error.message.includes('Failed to fetch')) {
                errorMessage = 'Ошибка сети. Проверьте подключение';
            } else if (error.message.includes('HTTP error')) {
                errorMessage = 'Ошибка сервера: ' + error.message;
            }

            throw new Error(errorMessage);

        } finally {
            appState.setLoading(false);
        }
    },

    /**
     * Get error message for user display
     * @param {Error} error - Error object
     * @returns {string} User-friendly error message
     */
    getErrorMessage(error) {
        if (error.message === 'AbortError') {
            return 'Время ожидания ответа истекло';
        }
        if (error.message.includes('Failed to fetch')) {
            return 'Ошибка сети. Проверьте подключение';
        }
        if (error.message.includes('HTTP error')) {
            return 'Ошибка сервера: ' + error.message;
        }
        return error.message || 'Что-то пошло не так';
    }
};
