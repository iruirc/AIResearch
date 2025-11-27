/**
 * @fileoverview RAG Settings Renderer
 * Handles rendering of settings form and UI updates
 * @module ui/rag/settings/SettingsRenderer
 */

/**
 * Settings Renderer class for rendering settings UI
 */
export class SettingsRenderer {
    constructor() {
        // Nothing to initialize
    }

    /**
     * Populate settings form with preferences data
     * @param {Object} prefs - Preferences object
     * @param {Function} updateStrategyCallback - Callback to update strategy params
     */
    async populateSettingsForm(prefs, updateStrategyCallback = null) {
        if (!prefs) return;

        // Enable reranking toggle
        const enableReranking = document.getElementById('ragEnableReranking');
        if (enableReranking) {
            enableReranking.checked = prefs.enableReranking || false;
            // Show/hide options
            const options = document.getElementById('ragRerankingOptions');
            if (options) {
                options.classList.toggle('hidden', !prefs.enableReranking);
            }
        }

        // Strategy - pass saved provider and model for CROSS_ENCODER
        const strategy = document.getElementById('ragStrategy');
        if (strategy) {
            strategy.value = prefs.strategy || 'SCORE_THRESHOLD';
            if (updateStrategyCallback) {
                await updateStrategyCallback(
                    prefs.strategy || 'SCORE_THRESHOLD',
                    prefs.crossEncoderProvider,
                    prefs.crossEncoderModel
                );
            }
        }

        // SCORE_THRESHOLD params
        const scoreThreshold = document.getElementById('ragScoreThreshold');
        if (scoreThreshold) {
            scoreThreshold.value = prefs.scoreThreshold ?? 0.75;
        }

        // STATISTICAL params
        const stdDevMultiplier = document.getElementById('ragStdDevMultiplier');
        if (stdDevMultiplier) {
            stdDevMultiplier.value = prefs.stdDevMultiplier ?? 1.0;
        }

        const minResultsToKeep = document.getElementById('ragMinResultsToKeep');
        if (minResultsToKeep) {
            minResultsToKeep.value = prefs.minResultsToKeep ?? 1;
        }

        // CROSS_ENCODER params - model is populated dynamically via updateStrategyParams
        const crossEncoderMinScore = document.getElementById('ragCrossEncoderMinScore');
        if (crossEncoderMinScore) {
            crossEncoderMinScore.value = prefs.crossEncoderMinScore ?? 6.0;
        }

        // General search params
        const searchTopK = document.getElementById('ragSearchTopK');
        if (searchTopK) {
            searchTopK.value = prefs.searchTopK ?? 5;
        }

        const searchMinScore = document.getElementById('ragSearchMinScore');
        if (searchMinScore) {
            searchMinScore.value = prefs.searchMinScore ?? 0.7;
        }

        // Debug mode
        const debugMode = document.getElementById('ragDebugMode');
        if (debugMode) {
            debugMode.checked = prefs.debugMode || false;
        }
    }

    /**
     * Update visibility of strategy-specific parameter sections
     * @param {string} strategy - Selected strategy
     */
    updateStrategyParamsVisibility(strategy) {
        const scoreThresholdParams = document.getElementById('ragScoreThresholdParams');
        const statisticalParams = document.getElementById('ragStatisticalParams');
        const crossEncoderParams = document.getElementById('ragCrossEncoderParams');

        // Hide all
        if (scoreThresholdParams) scoreThresholdParams.classList.add('hidden');
        if (statisticalParams) statisticalParams.classList.add('hidden');
        if (crossEncoderParams) crossEncoderParams.classList.add('hidden');

        // Show relevant section
        switch (strategy) {
            case 'SCORE_THRESHOLD':
                if (scoreThresholdParams) scoreThresholdParams.classList.remove('hidden');
                break;
            case 'STATISTICAL':
                if (statisticalParams) statisticalParams.classList.remove('hidden');
                break;
            case 'CROSS_ENCODER':
                if (crossEncoderParams) crossEncoderParams.classList.remove('hidden');
                break;
        }
    }

    /**
     * Populate Cross-Encoder provider dropdown
     * @param {Array} providers - Providers array
     * @param {string} defaultProvider - Default provider ID
     */
    populateProviderDropdown(providers, defaultProvider = null) {
        const providerSelect = document.getElementById('ragCrossEncoderProvider');
        if (!providerSelect) return;

        // Clear existing options
        providerSelect.innerHTML = '';

        // Populate provider dropdown
        providers.forEach(provider => {
            const option = document.createElement('option');
            option.value = provider.id;
            option.textContent = provider.displayName || provider.name;
            providerSelect.appendChild(option);
        });

        // Select default provider
        if (defaultProvider) {
            providerSelect.value = defaultProvider;
        }
    }

    /**
     * Populate Cross-Encoder model dropdown
     * @param {Array} models - Models array
     * @param {string} defaultModel - Default model ID
     */
    populateModelDropdown(models, defaultModel = null) {
        const modelSelect = document.getElementById('ragCrossEncoderModel');
        if (!modelSelect) return;

        // Clear existing options
        modelSelect.innerHTML = '';

        // Populate model dropdown
        models.forEach(model => {
            const option = document.createElement('option');
            option.value = model.id;
            option.textContent = model.displayName || model.name || model.id;
            modelSelect.appendChild(option);
        });

        // Select default model if it exists in options
        if (defaultModel) {
            const modelExists = Array.from(modelSelect.options).some(opt => opt.value === defaultModel);
            if (modelExists) {
                modelSelect.value = defaultModel;
            }
        }
    }

    /**
     * Show model loading error
     */
    showModelLoadingError() {
        const modelSelect = document.getElementById('ragCrossEncoderModel');
        if (modelSelect) {
            modelSelect.innerHTML = '<option value="">Ошибка загрузки</option>';
        }
    }

    /**
     * Get form values
     * @returns {Object} Form values as preferences object
     */
    getFormValues() {
        return {
            enableReranking: document.getElementById('ragEnableReranking')?.checked || false,
            strategy: document.getElementById('ragStrategy')?.value || 'SCORE_THRESHOLD',
            scoreThreshold: parseFloat(document.getElementById('ragScoreThreshold')?.value) || 0.75,
            stdDevMultiplier: parseFloat(document.getElementById('ragStdDevMultiplier')?.value) || 1.0,
            minResultsToKeep: parseInt(document.getElementById('ragMinResultsToKeep')?.value) || 1,
            crossEncoderProvider: document.getElementById('ragCrossEncoderProvider')?.value || null,
            crossEncoderModel: document.getElementById('ragCrossEncoderModel')?.value || null,
            crossEncoderMinScore: parseFloat(document.getElementById('ragCrossEncoderMinScore')?.value) || 6.0,
            searchTopK: parseInt(document.getElementById('ragSearchTopK')?.value) || 5,
            searchMinScore: parseFloat(document.getElementById('ragSearchMinScore')?.value) || 0.7,
            debugMode: document.getElementById('ragDebugMode')?.checked || false
        };
    }

    /**
     * Show notification in settings tab
     * @param {string} message - Message to display
     * @param {string} type - 'success' or 'error'
     */
    showNotification(message, type) {
        // Create or reuse notification element
        let notification = document.querySelector('.rag-settings-notification');
        if (!notification) {
            notification = document.createElement('div');
            notification.className = 'rag-settings-notification';
            const settingsContent = document.querySelector('.rag-settings-content');
            if (settingsContent) {
                settingsContent.insertBefore(notification, settingsContent.firstChild);
            }
        }

        notification.textContent = message;
        notification.className = `rag-settings-notification ${type}`;
        notification.style.display = 'block';

        // Auto-hide after 3 seconds
        setTimeout(() => {
            notification.style.display = 'none';
        }, 3000);
    }
}
