// Chat API module
import { API_CONFIG } from '../config.js';
import { fetchWithTimeout } from '../utils/helpers.js';

export const chatApi = {
    /**
     * Send a message to chat
     */
    async sendMessage(message, sessionId, settings) {
        const requestBody = {
            message,
            format: settings.format,
            model: settings.model,
            temperature: settings.temperature,
            maxTokens: settings.maxTokens
        };

        if (sessionId) {
            requestBody.sessionId = sessionId;
        }

        const response = await fetchWithTimeout(
            API_CONFIG.CHAT,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody),
            },
            API_CONFIG.REQUEST_TIMEOUT
        );

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        return await response.json();
    }
};
