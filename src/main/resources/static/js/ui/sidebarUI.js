// Sidebar UI module - manages sidebar display and interactions

// DOM element references
let sidebar = null;
let toggleButton = null;
let settingsDropdownButton = null;
let settingsDropdownMenu = null;

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

    // Initialize settings dropdown
    initSettingsDropdown();

    // Restore saved state
    restoreSidebarState();
}

/**
 * Initialize settings dropdown
 */
function initSettingsDropdown() {
    settingsDropdownButton = document.getElementById('settingsDropdownButton');
    settingsDropdownMenu = document.getElementById('settingsDropdownMenu');

    if (settingsDropdownButton && settingsDropdownMenu) {
        // Toggle dropdown on button click
        settingsDropdownButton.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleSettingsDropdown();
        });

        // Close dropdown when clicking outside
        document.addEventListener('click', (e) => {
            if (!settingsDropdownButton.contains(e.target) && !settingsDropdownMenu.contains(e.target)) {
                closeSettingsDropdown();
            }
        });

        // Close dropdown when clicking on menu items (after modal opens)
        settingsDropdownMenu.querySelectorAll('.dropdown-item').forEach(item => {
            item.addEventListener('click', () => {
                closeSettingsDropdown();
            });
        });
    }
}

/**
 * Toggle settings dropdown visibility
 */
function toggleSettingsDropdown() {
    if (!settingsDropdownButton || !settingsDropdownMenu) return;

    const isOpen = settingsDropdownMenu.classList.contains('open');
    if (isOpen) {
        closeSettingsDropdown();
    } else {
        openSettingsDropdown();
    }
}

/**
 * Open settings dropdown
 */
function openSettingsDropdown() {
    if (!settingsDropdownButton || !settingsDropdownMenu) return;

    settingsDropdownButton.classList.add('active');
    settingsDropdownMenu.classList.add('open');
}

/**
 * Close settings dropdown
 */
function closeSettingsDropdown() {
    if (!settingsDropdownButton || !settingsDropdownMenu) return;

    settingsDropdownButton.classList.remove('active');
    settingsDropdownMenu.classList.remove('open');
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
