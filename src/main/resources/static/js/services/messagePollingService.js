/**
 * @fileoverview Message Polling Service
 * Automatically polls for new messages in the active session
 * Used for scheduled tasks and other automated message generation
 * @module services/messagePollingService
 */

import { sessionsApi } from '../api/sessionsApi.js';
import { messagesUI } from '../ui/messagesUI.js';

/**
 * Service for polling new messages in active session
 * @namespace
 */
export const messagePollingService = {
    _pollingInterval: null,
    _currentSessionId: null,
    _lastMessageCount: 0,
    _pollingIntervalMs: 3000, // Poll every 3 seconds

    /**
     * Start polling for new messages in the specified session
     * @param {string} sessionId - Session ID to poll
     */
    startPolling(sessionId) {
        console.log('[MessagePolling] Starting polling for session:', sessionId);

        // Stop any existing polling
        this.stopPolling();

        if (!sessionId) {
            console.log('[MessagePolling] No session ID provided, not starting polling');
            return;
        }

        // Set current session
        this._currentSessionId = sessionId;
        this._lastMessageCount = 0;

        // Initial load to get current message count
        this._checkForNewMessages();

        // Start polling interval
        this._pollingInterval = setInterval(() => {
            this._checkForNewMessages();
        }, this._pollingIntervalMs);

        console.log('[MessagePolling] Polling started, interval:', this._pollingIntervalMs, 'ms');
    },

    /**
     * Stop polling
     */
    stopPolling() {
        if (this._pollingInterval) {
            console.log('[MessagePolling] Stopping polling for session:', this._currentSessionId);
            clearInterval(this._pollingInterval);
            this._pollingInterval = null;
            this._currentSessionId = null;
            this._lastMessageCount = 0;
        }
    },

    /**
     * Check for new messages and update UI if found
     * @private
     */
    async _checkForNewMessages() {
        if (!this._currentSessionId) {
            return;
        }

        try {
            // Get session data
            const sessionData = await sessionsApi.getSession(this._currentSessionId);

            if (!sessionData || !sessionData.messages) {
                return;
            }

            const currentMessageCount = sessionData.messages.length;

            // If this is the first check, just store the count
            if (this._lastMessageCount === 0) {
                this._lastMessageCount = currentMessageCount;
                console.log('[MessagePolling] Initial message count:', currentMessageCount);
                return;
            }

            // Check if there are new messages
            if (currentMessageCount > this._lastMessageCount) {
                console.log('[MessagePolling] New messages detected:', currentMessageCount - this._lastMessageCount);

                // Get only the new messages
                const newMessages = sessionData.messages.slice(this._lastMessageCount);

                // Add new messages to UI
                newMessages.forEach(message => {
                    messagesUI.addMessage(
                        message.content,
                        message.role,
                        message.metadata,
                        message.timestamp
                    );
                });

                // Update last message count
                this._lastMessageCount = currentMessageCount;

                console.log('[MessagePolling] UI updated with new messages');
            }
        } catch (error) {
            console.error('[MessagePolling] Error checking for new messages:', error);
            // Don't stop polling on error, just log it
        }
    },

    /**
     * Set polling interval in milliseconds
     * @param {number} intervalMs - Polling interval in milliseconds
     */
    setPollingInterval(intervalMs) {
        if (intervalMs < 1000) {
            console.warn('[MessagePolling] Polling interval too short, using minimum 1000ms');
            intervalMs = 1000;
        }

        this._pollingIntervalMs = intervalMs;

        // Restart polling if active
        if (this._pollingInterval) {
            const currentSession = this._currentSessionId;
            this.stopPolling();
            this.startPolling(currentSession);
        }

        console.log('[MessagePolling] Polling interval set to:', intervalMs, 'ms');
    },

    /**
     * Get current polling status
     * @returns {Object} Polling status
     */
    getStatus() {
        return {
            isPolling: this._pollingInterval !== null,
            sessionId: this._currentSessionId,
            intervalMs: this._pollingIntervalMs,
            lastMessageCount: this._lastMessageCount
        };
    }
};
