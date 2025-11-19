// Sessions UI module - manages sessions list display and interactions
import { getTimeAgo } from '../utils/helpers.js';
import { appState } from '../state/appState.js';

// DOM element references
let sessionsListElement = null;

// Current filter state
let currentFilter = 'all'; // 'all', 'simple', 'agents'

/**
 * Initialize sessions UI with DOM element
 * @param {HTMLElement} element - Sessions list element
 */
export function initSessionsUI(element) {
    sessionsListElement = element;
}

/**
 * Sessions UI module
 */
export const sessionsUI = {
    /**
     * Render sessions list
     * @param {Array} sessions - Array of session objects
     * @param {string} currentSessionId - ID of currently active session
     * @param {Object} callbacks - Callback functions for session interactions
     */
    renderSessionsList(sessions, currentSessionId, callbacks = {}) {
        if (!sessions || sessions.length === 0) {
            sessionsListElement.innerHTML = '<div class="sessions-empty">Нет активных чатов</div>';
            return;
        }

        sessionsListElement.innerHTML = '';
        sessions.forEach(session => {
            const sessionItem = this._createSessionItem(session, currentSessionId, callbacks);
            sessionsListElement.appendChild(sessionItem);
        });
    },

    /**
     * Show context menu for a session
     * @param {HTMLElement} sessionItem - Session item element
     * @param {HTMLElement} menuElement - Context menu element
     */
    showContextMenu(sessionItem, menuElement) {
        // Hide all other context menus
        this.hideAllContextMenus();

        // Add menu-active class to session item for higher z-index
        sessionItem.classList.add('menu-active');

        // Show menu with transition
        requestAnimationFrame(() => {
            menuElement.classList.add('show');
        });
    },

    /**
     * Hide all context menus
     */
    hideAllContextMenus() {
        const menus = document.querySelectorAll('.session-context-menu');
        menus.forEach(menu => {
            menu.classList.remove('show');
        });

        // Remove menu-active class from all session items
        const sessionItems = document.querySelectorAll('.session-item.menu-active');
        sessionItems.forEach(item => {
            item.classList.remove('menu-active');
        });
    },

    /**
     * Create session item element
     * @private
     * @param {Object} session - Session object
     * @param {string} currentSessionId - Current session ID
     * @param {Object} callbacks - Callback functions
     * @returns {HTMLElement} Session item element
     */
    _createSessionItem(session, currentSessionId, callbacks) {
        const sessionItem = document.createElement('div');
        sessionItem.className = 'session-item';
        sessionItem.dataset.sessionId = session.id;
        sessionItem.dataset.agentId = session.agentId || 'null';

        if (session.id === currentSessionId) {
            sessionItem.classList.add('active');
        }

        const title = session.title || 'Новый чат';
        const timeAgo = getTimeAgo(session.lastAccessedAt);

        sessionItem.innerHTML = `
            <div class="session-item-content">
                <div class="session-item-header">
                    <span class="session-title">${title}</span>
                    <span class="session-message-count">${session.messageCount} сообщ.</span>
                </div>
                <div class="session-time">${timeAgo}</div>
            </div>
            <button class="session-menu-btn" title="Меню">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="1"></circle>
                    <circle cx="12" cy="5" r="1"></circle>
                    <circle cx="12" cy="19" r="1"></circle>
                </svg>
            </button>
            <div class="session-context-menu">
                <div class="context-menu-item" data-action="rename">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                    </svg>
                    Переименовать
                </div>
                <div class="context-menu-item" data-action="copy">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                    </svg>
                    Скопировать
                </div>
                <div class="context-menu-item" data-action="compress">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="4 14 10 14 10 20"></polyline>
                        <polyline points="20 10 14 10 14 4"></polyline>
                        <line x1="14" y1="10" x2="21" y2="3"></line>
                        <line x1="3" y1="21" x2="10" y2="14"></line>
                    </svg>
                    Сжать историю
                </div>
                <div class="context-menu-divider"></div>
                <div class="context-menu-item context-menu-item-danger" data-action="delete">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="3 6 5 6 21 6"></polyline>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                    </svg>
                    Удалить
                </div>
            </div>
        `;

        // Add click handler for session selection
        const sessionContent = sessionItem.querySelector('.session-item-content');
        sessionContent.addEventListener('click', () => {
            if (callbacks.onSessionClick) {
                callbacks.onSessionClick(session.id);
            }
        });

        // Add click handler for menu button
        const menuBtn = sessionItem.querySelector('.session-menu-btn');
        const contextMenu = sessionItem.querySelector('.session-context-menu');

        menuBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            this.showContextMenu(sessionItem, contextMenu);
        });

        // Add click handlers for menu items
        const menuItems = contextMenu.querySelectorAll('.context-menu-item');
        menuItems.forEach(item => {
            item.addEventListener('click', (e) => {
                e.stopPropagation();
                const action = item.dataset.action;
                this.hideAllContextMenus();

                if (callbacks[`on${action.charAt(0).toUpperCase() + action.slice(1)}`]) {
                    callbacks[`on${action.charAt(0).toUpperCase() + action.slice(1)}`](session.id, title);
                }
            });
        });

        return sessionItem;
    },

    /**
     * Filter sessions by category
     * @param {string} category - Category to filter by ('all', 'simple', 'agents')
     * @param {Array} sessions - Array of all sessions
     */
    filterSessions(category, sessions) {
        currentFilter = category;

        // Update active category button
        document.querySelectorAll('.category-item').forEach(item => {
            item.classList.toggle('active', item.dataset.category === category);
        });

        // Filter session items in the DOM
        const sessionItems = document.querySelectorAll('.session-item');
        let visibleCount = 0;

        sessionItems.forEach(item => {
            const agentId = item.dataset.agentId;
            let shouldShow = false;

            switch (category) {
                case 'all':
                    shouldShow = true;
                    break;
                case 'simple':
                    shouldShow = !agentId || agentId === 'null';
                    break;
                case 'agents':
                    shouldShow = agentId && agentId !== 'null';
                    break;
            }

            item.style.display = shouldShow ? 'flex' : 'none';
            if (shouldShow) visibleCount++;
        });

        // Update empty state
        this._updateEmptyState(visibleCount, category);
    },

    /**
     * Update category counts
     * @param {Array} sessions - Array of all sessions
     */
    updateCategoryCounts(sessions) {
        if (!sessions) return;

        const allCount = sessions.length;
        const simpleCount = sessions.filter(s => !s.agentId).length;
        const agentsCount = sessions.filter(s => s.agentId).length;

        const allCountEl = document.getElementById('allCount');
        const simpleCountEl = document.getElementById('simpleCount');
        const agentsCountEl = document.getElementById('agentsCount');

        if (allCountEl) allCountEl.textContent = allCount;
        if (simpleCountEl) simpleCountEl.textContent = simpleCount;
        if (agentsCountEl) agentsCountEl.textContent = agentsCount;
    },

    /**
     * Update empty state message
     * @private
     * @param {number} visibleCount - Number of visible sessions
     * @param {string} category - Current category filter
     */
    _updateEmptyState(visibleCount, category) {
        let emptyMessage = sessionsListElement.querySelector('.sessions-empty');

        if (visibleCount === 0) {
            const message = this._getEmptyMessage(category);

            if (!emptyMessage) {
                emptyMessage = document.createElement('div');
                emptyMessage.className = 'sessions-empty';
                sessionsListElement.appendChild(emptyMessage);
            }

            emptyMessage.textContent = message;
            emptyMessage.style.display = 'block';
        } else if (emptyMessage) {
            emptyMessage.style.display = 'none';
        }
    },

    /**
     * Get empty state message based on category
     * @private
     * @param {string} category - Current category
     * @returns {string} Empty message
     */
    _getEmptyMessage(category) {
        switch (category) {
            case 'simple':
                return 'Нет простых чатов';
            case 'agents':
                return 'Нет чатов с агентами';
            default:
                return 'Нет активных чатов';
        }
    },

    /**
     * Get current filter
     * @returns {string} Current filter category
     */
    getCurrentFilter() {
        return currentFilter;
    }
};

// Hide context menus when clicking outside
document.addEventListener('click', () => {
    sessionsUI.hideAllContextMenus();
});
