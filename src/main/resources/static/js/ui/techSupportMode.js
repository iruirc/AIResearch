import { techSupportApi } from '../api/techSupportApi.js';

/**
 * Tech Support Mode UI controller
 * Provides tech support functionality integrated with the chat interface
 */
export class TechSupportMode {
    constructor(chatApp) {
        this.chatApp = chatApp;
        this.isEnabled = false;
        this.panel = null;
        this.lastResponse = null;
    }

    /**
     * Initialize the tech support UI
     */
    init() {
        this.createPanel();
        this.checkServiceHealth();
    }

    /**
     * Create the tech support panel
     */
    createPanel() {
        const panel = document.createElement('div');
        panel.id = 'tech-support-panel';
        panel.className = 'tech-support-panel';
        panel.style.display = 'none';
        panel.innerHTML = `
            <div class="panel-header">
                <h3><span class="icon">&#x1F3A7;</span> Tech Support</h3>
                <div class="panel-status">
                    <span id="ts-rag-status" class="status-indicator">RAG</span>
                    <span id="ts-trello-status" class="status-indicator">Trello</span>
                </div>
                <button class="close-btn" onclick="techSupportMode.hide()">&times;</button>
            </div>
            <div class="panel-body">
                <div class="panel-section" id="ts-related-tickets">
                    <h4><span class="icon">&#x1F3AB;</span> Related Tickets</h4>
                    <div class="tickets-list">
                        <p class="empty-state">No related tickets found</p>
                    </div>
                </div>
                <div class="panel-section" id="ts-suggested-actions">
                    <h4><span class="icon">&#x1F4A1;</span> Suggested Actions</h4>
                    <div class="actions-list">
                        <p class="empty-state">No suggestions available</p>
                    </div>
                </div>
                <div class="panel-section" id="ts-sources">
                    <h4><span class="icon">&#x1F4DA;</span> Sources Used</h4>
                    <div class="sources-list">
                        <p class="empty-state">No sources available</p>
                    </div>
                </div>
            </div>
        `;

        document.body.appendChild(panel);
        this.panel = panel;

        // Make techSupportMode accessible globally for onclick handlers
        window.techSupportMode = this;
    }

    /**
     * Toggle tech support mode
     * Creates a new Tech Support session when enabling
     */
    async toggle() {
        this.isEnabled = !this.isEnabled;

        const button = document.getElementById('techSupportButton');
        if (button) {
            button.classList.toggle('active', this.isEnabled);
        }

        document.body.classList.toggle('tech-support-mode', this.isEnabled);

        if (this.isEnabled) {
            // Create a new Tech Support session
            try {
                const result = await techSupportApi.createSession();
                console.log('Created Tech Support session:', result.sessionId);

                // Dispatch event for main.js to switch to this session
                window.dispatchEvent(new CustomEvent('techSupportSessionCreated', {
                    detail: { sessionId: result.sessionId }
                }));
            } catch (error) {
                console.error('Failed to create Tech Support session:', error);
            }
            this.show();
        } else {
            this.hide();
        }
    }

    /**
     * Show the tech support panel
     */
    show() {
        if (this.panel) {
            this.panel.style.display = 'flex';
        }
        this.checkServiceHealth();
    }

    /**
     * Hide the tech support panel
     */
    hide() {
        if (this.panel) {
            this.panel.style.display = 'none';
        }
    }

    /**
     * Check service health and update status indicators
     */
    async checkServiceHealth() {
        try {
            const health = await techSupportApi.getHealth();

            const ragStatus = document.getElementById('ts-rag-status');
            const trelloStatus = document.getElementById('ts-trello-status');

            if (ragStatus) {
                ragStatus.classList.toggle('active', health.ragEnabled);
                ragStatus.title = health.ragEnabled ? 'RAG is enabled' : 'RAG is disabled';
            }

            if (trelloStatus) {
                trelloStatus.classList.toggle('active', health.trelloConnected);
                trelloStatus.title = health.trelloConnected ? 'Trello is connected' : 'Trello is not connected';
            }
        } catch (error) {
            console.error('Failed to check tech support health:', error);
        }
    }

    /**
     * Send a tech support query
     * @param {string} query - The user's question
     * @returns {Promise<object>} The response
     */
    async sendQuery(query, options = {}) {
        if (!this.isEnabled) {
            return null;
        }

        try {
            const response = await techSupportApi.sendQuery(query, options);
            this.lastResponse = response;
            this.updatePanel(response);
            return response;
        } catch (error) {
            console.error('Tech support query failed:', error);
            throw error;
        }
    }

    /**
     * Update the panel with response data
     * @param {object} response - Tech support response
     */
    updatePanel(response) {
        this.updateTickets(response.relatedTickets);
        this.updateActions(response.suggestedActions);
        this.updateSources(response.sourcesUsed);
    }

    /**
     * Update related tickets section
     */
    updateTickets(tickets) {
        const container = document.querySelector('#ts-related-tickets .tickets-list');
        if (!container) return;

        if (!tickets || tickets.length === 0) {
            container.innerHTML = '<p class="empty-state">No related tickets found</p>';
            return;
        }

        container.innerHTML = tickets.map(ticket => `
            <div class="ticket-card">
                <div class="ticket-status">${this.escapeHtml(ticket.listName)}</div>
                <div class="ticket-title">
                    ${ticket.url
                        ? `<a href="${this.escapeHtml(ticket.url)}" target="_blank">${this.escapeHtml(ticket.cardName)}</a>`
                        : this.escapeHtml(ticket.cardName)
                    }
                </div>
                ${ticket.labels && ticket.labels.length > 0
                    ? `<div class="ticket-labels">${ticket.labels.map(l => `<span class="label">${this.escapeHtml(l)}</span>`).join('')}</div>`
                    : ''
                }
            </div>
        `).join('');
    }

    /**
     * Update suggested actions section
     */
    updateActions(actions) {
        const container = document.querySelector('#ts-suggested-actions .actions-list');
        if (!container) return;

        if (!actions || actions.length === 0) {
            container.innerHTML = '<p class="empty-state">No suggestions available</p>';
            return;
        }

        container.innerHTML = actions.map(action => {
            if (action.actionType === 'CREATE_TICKET' && action.createTicket) {
                return `
                    <button class="action-btn create-ticket"
                            onclick="techSupportMode.createTicket('${this.escapeHtml(action.createTicket.title)}', '${this.escapeHtml(action.createTicket.description)}')">
                        <span class="icon">&#x1F4DD;</span> Create Ticket: ${this.escapeHtml(action.createTicket.title)}
                    </button>
                `;
            } else if (action.actionType === 'VIEW_TICKET' && action.viewTicket) {
                return `
                    <div class="action-item view-ticket">
                        <span class="icon">&#x1F440;</span> View: ${this.escapeHtml(action.viewTicket.cardName)}
                        <small>${this.escapeHtml(action.viewTicket.reason)}</small>
                    </div>
                `;
            }
            return '';
        }).join('');
    }

    /**
     * Update sources section
     */
    updateSources(sources) {
        const container = document.querySelector('#ts-sources .sources-list');
        if (!container) return;

        if (!sources || (sources.ragSourceCount === 0 && sources.trelloTicketCount === 0)) {
            container.innerHTML = '<p class="empty-state">No sources available</p>';
            return;
        }

        let html = '';

        if (sources.ragSources && sources.ragSources.length > 0) {
            html += `<div class="source-group">
                <strong>Documentation (${sources.ragSourceCount}):</strong>
                <ul>${sources.ragSources.map(s => `<li>${this.escapeHtml(s)}</li>`).join('')}</ul>
            </div>`;
        }

        if (sources.trelloSources && sources.trelloSources.length > 0) {
            html += `<div class="source-group">
                <strong>Tickets (${sources.trelloTicketCount}):</strong>
                <ul>${sources.trelloSources.map(s => `<li>${this.escapeHtml(s)}</li>`).join('')}</ul>
            </div>`;
        }

        container.innerHTML = html;
    }

    /**
     * Create a ticket from suggested action
     */
    async createTicket(title, description) {
        try {
            const result = await techSupportApi.createTicket(title, description);

            if (result.success) {
                alert(`Ticket created successfully!\n\nURL: ${result.cardUrl || 'N/A'}`);
            } else {
                alert(`Failed to create ticket: ${result.error || 'Unknown error'}`);
            }
        } catch (error) {
            alert(`Failed to create ticket: ${error.message}`);
        }
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

export default TechSupportMode;
