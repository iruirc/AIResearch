/**
 * @fileoverview RAG Settings Event Handler
 * Handles event listeners for settings form
 * @module ui/rag/settings/SettingsEventHandler
 */

/**
 * Settings Event Handler class
 */
export class SettingsEventHandler {
    /**
     * Create a SettingsEventHandler
     * @param {Object} callbacks - Event callbacks
     * @param {Function} callbacks.onStrategyChange - Called when strategy changes
     * @param {Function} callbacks.onProviderChange - Called when provider changes
     * @param {Function} callbacks.onSave - Called when save is requested
     * @param {Function} callbacks.onReset - Called when reset is requested
     */
    constructor(callbacks = {}) {
        this.callbacks = callbacks;
    }

    /**
     * Setup all event handlers
     */
    setup() {
        this.setupEnableRerankingToggle();
        this.setupStrategySelector();
        this.setupFormSubmission();
        this.setupResetButton();
        this.setupProviderSelector();
    }

    /**
     * Setup enable reranking toggle
     */
    setupEnableRerankingToggle() {
        const enableRerankingToggle = document.getElementById('ragEnableReranking');
        if (enableRerankingToggle) {
            enableRerankingToggle.addEventListener('change', (e) => {
                const options = document.getElementById('ragRerankingOptions');
                if (options) {
                    options.classList.toggle('hidden', !e.target.checked);
                }
            });
        }
    }

    /**
     * Setup strategy selector
     */
    setupStrategySelector() {
        const strategySelect = document.getElementById('ragStrategy');
        if (strategySelect && this.callbacks.onStrategyChange) {
            strategySelect.addEventListener('change', async (e) => {
                await this.callbacks.onStrategyChange(e.target.value);
            });
        }
    }

    /**
     * Setup form submission
     */
    setupFormSubmission() {
        const settingsForm = document.getElementById('ragSearchSettingsForm');
        if (settingsForm && this.callbacks.onSave) {
            settingsForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                await this.callbacks.onSave();
            });
        }
    }

    /**
     * Setup reset button
     */
    setupResetButton() {
        const resetButton = document.getElementById('ragResetSettingsButton');
        if (resetButton && this.callbacks.onReset) {
            resetButton.addEventListener('click', async () => {
                if (confirm('Сбросить настройки поиска к значениям по умолчанию?')) {
                    await this.callbacks.onReset();
                }
            });
        }
    }

    /**
     * Setup provider selector
     */
    setupProviderSelector() {
        const crossEncoderProviderSelect = document.getElementById('ragCrossEncoderProvider');
        if (crossEncoderProviderSelect && this.callbacks.onProviderChange) {
            crossEncoderProviderSelect.addEventListener('change', async (e) => {
                await this.callbacks.onProviderChange(e.target.value);
            });
        }
    }
}
