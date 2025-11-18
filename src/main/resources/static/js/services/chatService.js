/**
 * @fileoverview Chat Service
 * Manages message sending workflow, response processing, and state updates
 * @module services/chatService
 */

import { chatApi } from '../api/chatApi.js';
import { appState } from '../state/appState.js';

/**
 * Service for managing chat operations
 * Orchestrates message sending, processes responses, updates state, and handles errors
 * @namespace
 */
export const chatService = {
    /**
     * Send a message to the chat and process the response
     * @async
     * @param {string} message - User's message text
     * @returns {Promise<Object|null>} Response data with response text, sessionId, metadata, and wasNewChat flag
     * @throws {Error} Throws user-friendly error messages for different failure scenarios
     * @example
     * try {
     *   const response = await chatService.sendMessage('Hello, AI!');
     *   console.log(response.response); // AI's response text
     *   console.log(response.metadata); // Token usage, timing, etc.
     * } catch (error) {
     *   console.error(error.message); // User-friendly error message
     * }
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
     * Convert an error object into a user-friendly error message (in Russian)
     * @param {Error} error - The error object to process
     * @returns {string} Localized, user-friendly error message
     * @example
     * const message = chatService.getErrorMessage(new Error('Failed to fetch'));
     * console.log(message); // 'Ошибка сети. Проверьте подключение'
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
