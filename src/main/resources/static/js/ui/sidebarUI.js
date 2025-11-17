// Sidebar UI module - manages sidebar display and interactions

// DOM element references
let sidebar = null;
let toggleButton = null;

// Sidebar state
const SIDEBAR_STATE_KEY = 'sidebarCollapsed';

/**
 * Initialize sidebar UI with DOM elements
 * @param {HTMLElement} sidebarElement - Sidebar element
 * @param {HTMLElement} toggleButtonElement - Toggle button element
 */
export function initSidebarUI(sidebarElement, toggleButtonElement) {
    sidebar = sidebarElement;
    toggleButton = toggleButtonElement;

    // Restore saved state
    restoreSidebarState();
}

/**
 * Restore sidebar state from localStorage
 */
function restoreSidebarState() {
    const savedState = localStorage.getItem(SIDEBAR_STATE_KEY);
    if (savedState === 'true') {
        sidebar?.classList.add('collapsed');
    }
}

/**
 * Sidebar UI module
 */
export const sidebarUI = {
    /**
     * Toggle sidebar visibility
     * @returns {boolean} New collapsed state
     */
    toggle() {
        if (!sidebar) return false;

        const isCollapsed = sidebar.classList.toggle('collapsed');

        // Save state to localStorage
        localStorage.setItem(SIDEBAR_STATE_KEY, isCollapsed.toString());

        return isCollapsed;
    },

    /**
     * Collapse sidebar
     */
    collapse() {
        if (!sidebar) return;

        sidebar.classList.add('collapsed');
        localStorage.setItem(SIDEBAR_STATE_KEY, 'true');
    },

    /**
     * Expand sidebar
     */
    expand() {
        if (!sidebar) return;

        sidebar.classList.remove('collapsed');
        localStorage.setItem(SIDEBAR_STATE_KEY, 'false');
    },

    /**
     * Check if sidebar is collapsed
     * @returns {boolean} True if collapsed
     */
    isCollapsed() {
        return sidebar?.classList.contains('collapsed') || false;
    },

    /**
     * Update user info in sidebar footer
     * @param {Object} user - User object with name and email
     */
    updateUserInfo(user) {
        const userNameElement = document.getElementById('userName');
        const userEmailElement = document.getElementById('userEmail');

        if (userNameElement && user.name) {
            userNameElement.textContent = user.name;
        }

        if (userEmailElement && user.email) {
            userEmailElement.textContent = user.email;
        }
    },

    /**
     * Update session counter in sidebar
     * @param {number} count - Number of sessions
     */
    updateSessionCount(count) {
        const sessionCountElement = document.getElementById('sessionCount');
        if (sessionCountElement) {
            sessionCountElement.textContent = count.toString();
        }
    }
};
