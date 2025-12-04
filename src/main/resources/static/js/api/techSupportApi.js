/**
 * API client for Tech Support service
 */
export const techSupportApi = {
    /**
     * Create a new Tech Support session
     * @returns {Promise<object>} { sessionId: string }
     */
    async createSession() {
        const response = await fetch('/api/v2/tech-support/sessions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({ error: 'Unknown error' }));
            throw new Error(error.error || `Request failed: ${response.status}`);
        }

        return response.json();
    },

    /**
     * Mark existing session as Tech Support
     * @param {string} sessionId - Session ID to mark
     * @returns {Promise<object>} { success: boolean, sessionId: string }
     */
    async markSessionAsTechSupport(sessionId) {
        const response = await fetch(`/api/v2/tech-support/sessions/${sessionId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' }
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({ error: 'Unknown error' }));
            throw new Error(error.error || `Request failed: ${response.status}`);
        }

        return response.json();
    },

    /**
     * Send a tech support query
     * @param {string} query - The user's question
     * @param {object} options - Optional parameters
     * @returns {Promise<object>} Tech support response
     */
    async sendQuery(query, options = {}) {
        const response = await fetch('/api/v2/tech-support', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                query,
                sessionId: options.sessionId || null,
                customerId: options.customerId || null,
                trelloBoardId: options.trelloBoardId || null,
                includeRag: options.includeRag !== false,
                includeTrello: options.includeTrello !== false,
                maxRagResults: options.maxRagResults || 5,
                maxTrelloResults: options.maxTrelloResults || 3,
                providerId: options.providerId || 'CLAUDE',
                model: options.model || null,
                existingAnswer: options.existingAnswer || null  // Skip AI call if answer provided
            })
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({ error: 'Unknown error' }));
            throw new Error(error.error || `Request failed: ${response.status}`);
        }

        return response.json();
    },

    /**
     * Create a support ticket in Trello
     * @param {string} title - Ticket title
     * @param {string} description - Ticket description
     * @param {object} options - Optional parameters
     * @returns {Promise<object>} Create ticket response
     */
    async createTicket(title, description, options = {}) {
        const response = await fetch('/api/v2/tech-support/tickets', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                title,
                description,
                listName: options.listName || 'Inbox',
                labels: options.labels || [],
                boardId: options.boardId || null
            })
        });

        return response.json();
    },

    /**
     * Check tech support service health
     * @returns {Promise<object>} Health status
     */
    async getHealth() {
        const response = await fetch('/api/v2/tech-support/health');
        return response.json();
    },

    /**
     * Add FAQ entry to RAG knowledge base
     * @param {string} question - The question
     * @param {string} answer - The answer
     * @param {string} category - Optional category (default: 'general')
     * @returns {Promise<object>} Add FAQ response
     */
    async addFaq(question, answer, category = 'general') {
        const response = await fetch('/api/v2/tech-support/faq', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                question,
                answer,
                category
            })
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({ error: 'Unknown error' }));
            throw new Error(error.error || `Request failed: ${response.status}`);
        }

        return response.json();
    }
};

export default techSupportApi;
