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
            switch (action.actionType) {
                case 'CREATE_TICKET':
                    if (action.createTicket) {
                        const title = this.escapeJs(action.createTicket.title);
                        const titleHtml = this.escapeHtml(action.createTicket.title);
                        const desc = this.escapeJs(action.createTicket.description);
                        return `
                            <button class="action-btn create-ticket"
                                    onclick="techSupportMode.createTicket('${title}', '${desc}')">
                                <span class="icon">&#x1F4DD;</span> Trello: ${titleHtml}
                            </button>
                        `;
                    }
                    break;

                case 'VIEW_TICKET':
                    if (action.viewTicket) {
                        return `
                            <div class="action-item view-ticket">
                                <span class="icon">&#x1F440;</span>
                                <span class="action-title">${this.escapeHtml(action.viewTicket.cardName)}</span>
                                <small>${this.escapeHtml(action.viewTicket.reason)}</small>
                            </div>
                        `;
                    }
                    break;

                case 'ESCALATE':
                    if (action.escalate) {
                        const priorityIcon = this.getPriorityIcon(action.escalate.priority);
                        return `
                            <div class="action-item escalate priority-${action.escalate.priority}">
                                <span class="icon">${priorityIcon}</span>
                                <span class="action-title">Escalate to Support</span>
                                <small>${this.escapeHtml(action.escalate.reason)}</small>
                            </div>
                        `;
                    }
                    break;

                case 'ADD_TO_FAQ':
                    if (action.addToFaq) {
                        const question = this.escapeJs(action.addToFaq.question);
                        return `
                            <button class="action-btn add-to-faq"
                                    onclick="techSupportMode.addToFaq('${question}')">
                                <span class="icon">&#x1F4D6;</span> Add to FAQ
                            </button>
                        `;
                    }
                    break;

                case 'CONTACT_SUPPORT':
                    if (action.contactSupport) {
                        const channelIcon = this.getChannelIcon(action.contactSupport.suggestedChannel);
                        return `
                            <div class="action-item contact-support">
                                <span class="icon">${channelIcon}</span>
                                <span class="action-title">Contact Support</span>
                                <small>${this.escapeHtml(action.contactSupport.reason)}</small>
                            </div>
                        `;
                    }
                    break;
            }
            return '';
        }).join('');
    }

    /**
     * Get icon for priority level
     */
    getPriorityIcon(priority) {
        switch (priority) {
            case 'urgent': return '&#x1F6A8;'; // 🚨
            case 'high': return '&#x26A0;';    // ⚠
            case 'normal': return '&#x1F4E2;'; // 📢
            case 'low': return '&#x1F4AC;';    // 💬
            default: return '&#x1F4E2;';
        }
    }

    /**
     * Get icon for support channel
     */
    getChannelIcon(channel) {
        switch (channel) {
            case 'email': return '&#x1F4E7;';  // 📧
            case 'chat': return '&#x1F4AC;';   // 💬
            case 'phone': return '&#x1F4DE;';  // 📞
            default: return '&#x1F4E7;';
        }
    }

    /**
     * Add question to FAQ
     * @param {string} question - The question to add
     */
    async addToFaq(question) {
        // Get the answer from the last response
        if (!this.lastResponse || !this.lastResponse.answer) {
            alert('No answer available to add to FAQ');
            return;
        }

        const answer = this.lastResponse.answer;
        const category = this.lastResponse.queryType?.toLowerCase() || 'general';

        // Confirm with user
        const confirmed = confirm(
            `Add to FAQ?\n\nQuestion:\n${question.substring(0, 100)}...\n\nAnswer will be added to RAG knowledge base.`
        );

        if (!confirmed) {
            return;
        }

        try {
            const result = await techSupportApi.addFaq(question, answer, category);

            if (result.success) {
                alert(`FAQ added successfully!\n\nDocument: ${result.documentName}\nThis FAQ will now be used to answer similar questions.`);
            } else {
                alert(`Failed to add FAQ: ${result.error || 'Unknown error'}`);
            }
        } catch (error) {
            alert(`Failed to add FAQ: ${error.message}`);
        }
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

    /**
     * Escape string for use in JavaScript onclick handlers with single quotes
     * Uses JSON.stringify for proper escaping, then converts for single-quote context
     */
    escapeJs(text) {
        if (!text) return '';
        // JSON.stringify adds quotes, so we slice them off
        let escaped = JSON.stringify(text).slice(1, -1);
        // JSON.stringify escapes double quotes as \", but we use single quotes in onclick
        // So we need to:
        // 1. Unescape double quotes: \" -> "
        // 2. Escape single quotes: ' -> \'
        escaped = escaped.replace(/\\"/g, '"').replace(/'/g, "\\'");
        return escaped;
    }
}

export default TechSupportMode;
