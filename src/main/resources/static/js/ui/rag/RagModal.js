/**
 * @fileoverview RAG Modal Coordinator
 * Main coordinator class that orchestrates all RAG modal functionality
 * @module ui/rag/RagModal
 */

import { modalsUI } from '../modalsUI.js';

// Documents module
import { DocumentManager, DocumentRenderer, DocumentEventHandler } from './documents/index.js';

// Settings module
import { SettingsManager, SettingsRenderer, SettingsEventHandler } from './settings/index.js';

// Tests module
import { TestManager, TestRenderer, TestEventHandler, TestValidator, TestExecutor } from './tests/index.js';

// Shared components
import { TabManager } from './shared/TabManager.js';

/**
 * RAGModal class - Main coordinator for RAG functionality
 */
export class RagModal {
    /**
     * Create a RagModal
     */
    constructor() {
        // Tab Manager
        this.tabManager = new TabManager({
            tabsContainerId: 'ragModalTabs',
            onTabChange: (tabName) => this.handleTabChange(tabName)
        });

        // Documents
        this.documentManager = new DocumentManager({
            onDocumentsLoaded: (docs) => this.documentRenderer.renderDocumentsList(docs)
        });
        this.documentRenderer = new DocumentRenderer({
            onAddDocument: () => this.openAddDocumentForm(),
            onEditDocument: (id) => this.openEditDocumentForm(id),
            onDeleteDocument: (id, name) => this.handleDeleteDocument(id, name)
        });
        this.documentEventHandler = new DocumentEventHandler({
            onFileAdded: () => this.documentRenderer.renderFilesList(
                this.documentEventHandler.getSelectedFiles(),
                (file) => this.documentEventHandler.removeFile(file)
            )
        });

        // Settings
        this.settingsManager = new SettingsManager({
            onSettingsLoaded: (prefs) => this.settingsRenderer.populateSettingsForm(
                prefs,
                (strategy, provider, model) => this.updateStrategyParams(strategy, provider, model)
            )
        });
        this.settingsRenderer = new SettingsRenderer();
        this.settingsEventHandler = new SettingsEventHandler({
            onStrategyChange: (strategy) => this.updateStrategyParams(strategy),
            onProviderChange: (providerId) => this.handleProviderChange(providerId),
            onSave: () => this.handleSaveSettings(),
            onReset: () => this.handleResetSettings()
        });

        // Tests
        this.testManager = new TestManager({
            onTestsLoaded: (tests) => this.testRenderer.renderTestsList(tests)
        });
        this.testRenderer = new TestRenderer({
            onAddTest: () => this.openAddTestForm(),
            onEditTest: (id) => this.openEditTestForm(id),
            onDeleteTest: (id, name) => this.handleDeleteTest(id, name),
            onExecuteTest: (test) => this.testExecutor.handleExecuteTest(test)
        });
        this.testEventHandler = new TestEventHandler({
            onTabSwitch: (tabName) => this.testRenderer.switchTestTab(tabName),
            onFileAdded: () => this.testRenderer.renderTestFilesList(
                this.testEventHandler.getSelectedFile(),
                () => this.testEventHandler.removeTestFile()
            ),
            onFileRemoved: () => this.testRenderer.renderTestFilesList(null, null)
        });
        this.testValidator = new TestValidator();
        this.testExecutor = new TestExecutor({
            onExecutionComplete: (result) => console.log('Execution complete:', result),
            onReturnToMain: () => modalsUI.openModal('ragModal')
        });

        // State
        this.currentTestMode = null; // 'add' or 'edit'
        this.editingTest = null;
    }

    /**
     * Initialize the RAG modal
     */
    async initialize() {
        console.log('Initializing RAG Modal...');

        // Setup tab navigation
        this.tabManager.setup();

        // Setup event handlers
        this.documentEventHandler.setup();
        this.settingsEventHandler.setup();

        // Load initial data
        await Promise.all([
            this.documentManager.loadDocuments().catch(err => {
                console.error('Failed to load documents:', err);
                this.documentRenderer.showError(err.message);
            }),
            this.settingsManager.loadSearchPreferences().catch(err => {
                console.error('Failed to load settings:', err);
            }),
            this.testManager.loadTests().catch(err => {
                console.error('Failed to load tests:', err);
                this.testRenderer.showError(err.message);
            })
        ]);

        console.log('RAG Modal initialized');
    }

    /**
     * Handle tab change
     * @param {string} tabName - Tab name
     */
    handleTabChange(tabName) {
        console.log(`Tab changed to: ${tabName}`);
        // Additional logic can be added here if needed
    }

    // ============================================
    // Document Methods
    // ============================================

    /**
     * Open add document form
     */
    openAddDocumentForm() {
        this.documentEventHandler.reset();
        modalsUI.closeModal('ragModal');
        modalsUI.openModal('ragDocumentFormModal');

        this.documentRenderer.setupAddForm({
            onSubmit: () => this.handleAddDocument(),
            onCancel: () => this.returnToMainModal()
        });

        this.documentRenderer.switchDocTab('text');
        this.documentRenderer.renderFilesList([], null);
        this.documentEventHandler.setupDocumentTabs();
        this.documentEventHandler.setupDocumentFileHandlers();
    }

    /**
     * Open edit document form
     * @param {string} docId - Document ID
     */
    async openEditDocumentForm(docId) {
        try {
            const doc = await this.documentManager.getDocument(docId);

            modalsUI.closeModal('ragModal');
            modalsUI.openModal('ragDocumentFormModal');

            this.documentRenderer.setupEditForm(doc, {
                onSubmit: () => this.handleUpdateDocument(docId),
                onCancel: () => this.returnToMainModal()
            });
        } catch (error) {
            console.error('Error loading document for editing:', error);
            alert(`Ошибка загрузки документа: ${error.message}`);
        }
    }

    /**
     * Handle adding a new document
     */
    async handleAddDocument() {
        const { name, content } = this.documentRenderer.getFormValues();
        const activeTab = this.documentEventHandler.getActiveTab();

        if (!name) {
            alert('Пожалуйста, укажите название документа');
            return;
        }

        try {
            if (activeTab === 'text') {
                if (!content) {
                    alert('Пожалуйста, введите содержимое документа');
                    return;
                }
                await this.documentManager.addDocument(name, content);
            } else {
                const files = this.documentEventHandler.getSelectedFiles();
                if (files.length === 0) {
                    alert('Пожалуйста, выберите файлы');
                    return;
                }
                await this.documentManager.addDocumentsFromFiles(files);
            }

            this.returnToMainModal();
        } catch (error) {
            console.error('Error adding document:', error);
            alert(`Ошибка при добавлении документа: ${error.message}`);
        }
    }

    /**
     * Handle updating a document
     * @param {string} docId - Document ID
     */
    async handleUpdateDocument(docId) {
        const { name, content } = this.documentRenderer.getFormValues();

        if (!name) {
            alert('Пожалуйста, укажите название документа');
            return;
        }

        try {
            await this.documentManager.updateDocument(docId, name, content || null);
            this.returnToMainModal();
        } catch (error) {
            console.error('Error updating document:', error);
            alert(`Ошибка при обновлении документа: ${error.message}`);
        }
    }

    /**
     * Handle deleting a document
     * @param {string} docId - Document ID
     * @param {string} docName - Document name
     */
    async handleDeleteDocument(docId, docName) {
        if (!confirm(`Вы уверены, что хотите удалить документ "${docName}"?\nЭто действие нельзя отменить.`)) {
            return;
        }

        try {
            await this.documentManager.deleteDocument(docId);
        } catch (error) {
            console.error('Error deleting document:', error);
            alert(`Ошибка при удалении документа: ${error.message}`);
        }
    }

    // ============================================
    // Settings Methods
    // ============================================

    /**
     * Update strategy-specific parameters
     * @param {string} strategy - Selected strategy
     * @param {string} savedProvider - Saved provider ID
     * @param {string} savedModel - Saved model ID
     */
    async updateStrategyParams(strategy, savedProvider = null, savedModel = null) {
        this.settingsRenderer.updateStrategyParamsVisibility(strategy);

        if (strategy === 'CROSS_ENCODER') {
            try {
                const { providers, defaultProvider, currentModel } = await this.settingsManager.loadCrossEncoderProviders(savedProvider, savedModel);
                this.settingsRenderer.populateProviderDropdown(providers, defaultProvider);

                if (defaultProvider) {
                    await this.handleProviderChange(defaultProvider, currentModel);
                }
            } catch (error) {
                console.error('Error loading Cross-Encoder providers:', error);
            }
        }
    }

    /**
     * Handle provider change in Cross-Encoder settings
     * @param {string} providerId - Provider ID
     * @param {string} savedModel - Saved model ID (optional)
     */
    async handleProviderChange(providerId, savedModel = null) {
        try {
            const { models, defaultModel } = await this.settingsManager.loadCrossEncoderModels(providerId, savedModel);
            this.settingsRenderer.populateModelDropdown(models, defaultModel);
        } catch (error) {
            console.error('Error loading models for provider:', error);
            this.settingsRenderer.showModelLoadingError();
        }
    }

    /**
     * Handle saving settings
     */
    async handleSaveSettings() {
        const preferences = this.settingsRenderer.getFormValues();

        try {
            await this.settingsManager.handleSaveSettings(preferences);
            this.settingsRenderer.showNotification('Настройки сохранены', 'success');
        } catch (error) {
            console.error('Error saving settings:', error);
            this.settingsRenderer.showNotification(`Ошибка: ${error.message}`, 'error');
        }
    }

    /**
     * Handle resetting settings
     */
    async handleResetSettings() {
        try {
            await this.settingsManager.handleResetSettings();
            this.settingsRenderer.showNotification('Настройки сброшены', 'success');
        } catch (error) {
            console.error('Error resetting settings:', error);
            this.settingsRenderer.showNotification(`Ошибка: ${error.message}`, 'error');
        }
    }

    // ============================================
    // Test Methods
    // ============================================

    /**
     * Open add test form
     */
    openAddTestForm() {
        this.currentTestMode = 'add';
        this.editingTest = null;
        this.testEventHandler.reset();
        this.testValidator.hideValidationError();

        modalsUI.closeModal('ragModal');
        modalsUI.openModal('ragTestFormModal');

        this.testRenderer.setupAddForm({
            onSubmit: () => this.handleAddTest(),
            onCancel: () => this.returnToMainModal()
        });

        this.testRenderer.switchTestTab('text');
        this.testRenderer.renderTestFilesList(null, null);
        this.testEventHandler.setupTestTabs();
        this.testEventHandler.setupTestFileHandlers();
    }

    /**
     * Open edit test form
     * @param {string} testId - Test ID
     */
    async openEditTestForm(testId) {
        try {
            this.currentTestMode = 'edit';
            this.testValidator.hideValidationError();

            const test = await this.testManager.getTest(testId);
            this.editingTest = test;

            modalsUI.closeModal('ragModal');
            modalsUI.openModal('ragTestFormModal');

            this.testRenderer.setupEditForm(test, {
                onSubmit: () => this.handleUpdateTest(),
                onCancel: () => this.returnToMainModal()
            });

            this.testRenderer.switchTestTab('text');
        } catch (error) {
            console.error('Error loading test for editing:', error);
            alert(`Ошибка загрузки теста: ${error.message}`);
        }
    }

    /**
     * Handle adding a new test
     */
    async handleAddTest() {
        const { name, content: formContent } = this.testRenderer.getFormValues();
        this.testValidator.hideValidationError();

        if (!name) {
            alert('Пожалуйста, укажите название теста');
            return;
        }

        try {
            const content = await this.testEventHandler.getContent();

            if (!content) {
                if (this.testEventHandler.getActiveTab() === 'text') {
                    alert('Пожалуйста, введите содержимое теста');
                } else {
                    alert('Пожалуйста, выберите файл');
                }
                return;
            }

            // Validate JSON content
            const validation = this.testValidator.validateTestJSON(content);
            if (!validation.valid) {
                this.testValidator.showValidationError(validation.error, validation.lineNumber);
                return;
            }

            // Parse JSON and extract queries and evaluationMetrics
            const parsed = JSON.parse(content);
            const queries = Array.isArray(parsed) ? parsed : parsed.queries;
            const evaluationMetrics = parsed.evaluationMetrics || null;

            await this.testManager.addTest(name, queries, evaluationMetrics);
            this.returnToMainModal();

        } catch (error) {
            console.error('Error adding test:', error);
            if (error.message.startsWith('DUPLICATE_NAME:')) {
                alert(`Тест с именем "${name}" уже существует.\nВыберите другое имя.`);
            } else {
                alert(`Ошибка при добавлении теста: ${error.message}`);
            }
        }
    }

    /**
     * Handle updating a test
     */
    async handleUpdateTest() {
        if (!this.editingTest) return;

        const { name, content } = this.testRenderer.getFormValues();
        this.testValidator.hideValidationError();

        if (!name) {
            alert('Пожалуйста, укажите название теста');
            return;
        }

        // Validate JSON content if provided
        let queries = null;
        let evaluationMetrics = null;

        if (content) {
            const validation = this.testValidator.validateTestJSON(content);
            if (!validation.valid) {
                this.testValidator.showValidationError(validation.error, validation.lineNumber);
                return;
            }

            // Parse JSON and extract queries and evaluationMetrics
            const parsed = JSON.parse(content);
            queries = Array.isArray(parsed) ? parsed : parsed.queries;
            evaluationMetrics = parsed.evaluationMetrics || null;
        }

        try {
            await this.testManager.updateTest(this.editingTest.id, name, queries, evaluationMetrics);
            this.returnToMainModal();
        } catch (error) {
            console.error('Error updating test:', error);
            if (error.message.startsWith('DUPLICATE_NAME:')) {
                alert(`Тест с именем "${name}" уже существует.\nВыберите другое имя.`);
            } else {
                alert(`Ошибка при обновлении теста: ${error.message}`);
            }
        }
    }

    /**
     * Handle deleting a test
     * @param {string} testId - Test ID
     * @param {string} testName - Test name
     */
    async handleDeleteTest(testId, testName) {
        if (!confirm(`Вы уверены, что хотите удалить тест "${testName}"?\nЭто действие нельзя отменить.`)) {
            return;
        }

        try {
            await this.testManager.deleteTest(testId);
        } catch (error) {
            console.error('Error deleting test:', error);
            alert(`Ошибка при удалении теста: ${error.message}`);
        }
    }

    // ============================================
    // Utility Methods
    // ============================================

    /**
     * Return to main RAG modal
     */
    returnToMainModal() {
        modalsUI.closeModal('ragDocumentFormModal');
        modalsUI.closeModal('ragTestFormModal');
        modalsUI.openModal('ragModal');
    }
}

/**
 * Initialize RAG modal (called from main.js)
 */
export async function initializeRAGModal() {
    const ragModal = new RagModal();
    await ragModal.initialize();
    return ragModal;
}
