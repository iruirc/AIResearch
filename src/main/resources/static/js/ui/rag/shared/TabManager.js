/**
 * @fileoverview RAG Tab Manager
 * Handles tab switching functionality for various tab groups
 * @module ui/rag/shared/TabManager
 */

/**
 * Tab Manager class for managing tab switching
 */
export class TabManager {
    constructor() {
        this.activeTab = 'text';
        this.activeModalTab = 'documents';
        this.activeTestTab = 'text';
        this.onModalTabChanged = null;
    }

    /**
     * Set callback for modal tab changed
     * @param {Function} callback - Callback function(tabName)
     */
    setOnModalTabChanged(callback) {
        this.onModalTabChanged = callback;
    }

    /**
     * Get active modal tab
     * @returns {string} Active modal tab name
     */
    getActiveModalTab() {
        return this.activeModalTab;
    }

    /**
     * Get active document form tab
     * @returns {string} Active form tab name
     */
    getActiveTab() {
        return this.activeTab;
    }

    /**
     * Get active test form tab
     * @returns {string} Active test form tab name
     */
    getActiveTestTab() {
        return this.activeTestTab;
    }

    /**
     * Setup document form tab switching (text/files)
     */
    setupDocumentTabs() {
        const tabs = document.querySelectorAll('.rag-tab');
        tabs.forEach(tab => {
            tab.onclick = () => {
                const tabName = tab.dataset.tab;
                this.switchDocumentTab(tabName);
            };
        });
    }

    /**
     * Switch between document form text and files tabs
     * @param {string} tabName - Tab name ('text' or 'files')
     */
    switchDocumentTab(tabName) {
        this.activeTab = tabName;

        // Update tab buttons
        const tabs = document.querySelectorAll('.rag-tab');
        tabs.forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tab === tabName);
        });

        // Update tab content
        const textContent = document.getElementById('ragTabText');
        const filesContent = document.getElementById('ragTabFiles');

        if (textContent) {
            textContent.classList.toggle('active', tabName === 'text');
        }
        if (filesContent) {
            filesContent.classList.toggle('active', tabName === 'files');
        }
    }

    /**
     * Setup modal tab switching (Documents / Settings / Testing)
     */
    setupModalTabs() {
        const tabs = document.querySelectorAll('.rag-modal-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const tabName = tab.dataset.tab;
                this.switchModalTab(tabName);
            });
        });
    }

    /**
     * Switch between modal tabs
     * @param {string} tabName - Tab name ('documents', 'settings', or 'testing')
     */
    async switchModalTab(tabName) {
        this.activeModalTab = tabName;

        // Update tab buttons
        const tabs = document.querySelectorAll('.rag-modal-tab');
        tabs.forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tab === tabName);
        });

        // Update tab content
        const documentsTab = document.getElementById('ragTabDocuments');
        const settingsTab = document.getElementById('ragTabSettings');
        const testingTab = document.getElementById('ragTabTesting');

        if (documentsTab) {
            documentsTab.classList.toggle('active', tabName === 'documents');
        }
        if (settingsTab) {
            settingsTab.classList.toggle('active', tabName === 'settings');
        }
        if (testingTab) {
            testingTab.classList.toggle('active', tabName === 'testing');
        }

        // Notify callback about tab change
        if (this.onModalTabChanged) {
            await this.onModalTabChanged(tabName);
        }
    }

    /**
     * Setup test form tab switching (text/files)
     */
    setupTestTabs() {
        const tabs = document.querySelectorAll('#ragTestSourceTabs .rag-tab');
        tabs.forEach(tab => {
            tab.onclick = () => {
                const tabName = tab.dataset.tab;
                this.switchTestTab(tabName);
            };
        });
    }

    /**
     * Switch between test form text and files tabs
     * @param {string} tabName - Tab name ('text' or 'files')
     */
    switchTestTab(tabName) {
        this.activeTestTab = tabName;

        // Update tab buttons
        const tabs = document.querySelectorAll('#ragTestSourceTabs .rag-tab');
        tabs.forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tab === tabName);
        });

        // Update tab content
        const textContent = document.getElementById('ragTestTabText');
        const filesContent = document.getElementById('ragTestTabFiles');

        if (textContent) {
            textContent.classList.toggle('active', tabName === 'text');
        }
        if (filesContent) {
            filesContent.classList.toggle('active', tabName === 'files');
        }
    }

    /**
     * Reset document form tab to text
     */
    resetDocumentTab() {
        this.switchDocumentTab('text');
    }

    /**
     * Reset test form tab to text
     */
    resetTestTab() {
        this.switchTestTab('text');
    }
}
