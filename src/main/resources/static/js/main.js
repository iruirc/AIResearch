// Main entry point - integrates all modules and initializes the application

// Import state management
import { appState } from './state/appState.js';

// Import services
import { sessionService } from './services/sessionService.js';
import { chatService } from './services/chatService.js';
import { llmModelService } from './services/llmModelService.js';
import { compressionService } from './services/compressionService.js';
import { messagePollingService } from './services/messagePollingService.js';
import { ollamaService } from './services/ollamaService.js';

// Import API modules
import { sessionsApi } from './api/sessionsApi.js';
import { mcpApi } from './api/mcpApi.js';
import { assistantsApi } from './api/assistantsApi.js';

// Import UI modules
import { messagesUI, initMessagesUI } from './ui/messagesUI.js';
import { sessionsUI, initSessionsUI } from './ui/sessionsUI.js';
import { modalsUI } from './ui/modalsUI.js?v=3';
import { sidebarUI, initSidebarUI } from './ui/sidebarUI.js';
import { SchedulerModal } from './ui/schedulerModal.js';
import { initializePipelinesModal } from './ui/pipelinesModal.js';
import { initializeOllamaModal } from './ui/ollamaModal.js';
import { initializeLlmModelModal } from './ui/llmModelModal.js';
import { initializeRAGModal } from './ui/ragModal.js';

// Import utilities
import { debounce, generateSlug } from './utils/helpers.js';

// DOM element references
let messageInput = null;
let sendButton = null;

/**
 * Initialize application
 */
async function initApp() {
    console.log('🚀 Initializing ResearchAI application...');

    // Get DOM elements
    const messagesContainer = document.getElementById('messagesContainer');
    const sessionsList = document.getElementById('sessionsList');
    const sidebar = document.querySelector('.sidebar');
    const toggleSidebarButton = document.getElementById('toggleSidebarButton');
    messageInput = document.getElementById('messageInput');
    sendButton = document.getElementById('sendButton');

    // Initialize UI modules with DOM elements
    initMessagesUI(messagesContainer);
    initSessionsUI(sessionsList);
    initSidebarUI(sidebar, toggleSidebarButton);

    // Initialize scheduler modal
    const schedulerModal = new SchedulerModal();

    // Initialize pipelines modal
    await initializePipelinesModal();

    // Initialize Ollama modal
    await initializeOllamaModal();

    // Initialize LLM model modal with tabs
    await initializeLlmModelModal();

    // Initialize RAG modal
    await initializeRAGModal();

    // Setup event listeners
    setupEventListeners();

    // Subscribe to state changes
    subscribeToStateChanges();

    try {
        // Load initial data
        console.log('📥 Loading configuration...');
        await llmModelService.loadConfig();

        console.log('📥 Loading sessions...');
        await sessionService.loadSessions();

        console.log('📥 Loading providers...');
        await llmModelService.loadProviders();

        console.log('📥 Loading assistants...');
        await llmModelService.loadAssistants();

        console.log('📥 Loading MCP servers...');
        await llmModelService.loadMcpServers();

        console.log('📥 Loading Ollama connections...');
        await ollamaService.initialize();

        console.log('✅ Application initialized successfully');

        // Show welcome message if no session is active
        const state = appState.getState();
        if (!state.currentSessionId) {
            messagesUI.showWelcomeMessage();
        }
    } catch (error) {
        console.error('❌ Failed to initialize application:', error);
        alert('Ошибка при инициализации приложения. Проверьте консоль для деталей.');
    }
}

/**
 * Setup all event listeners
 */
function setupEventListeners() {
    // Message input events
    sendButton.addEventListener('click', handleSendMessage);
    messageInput.addEventListener('keydown', handleMessageInputKeydown);
    messageInput.addEventListener('input', handleMessageInputResize);

    // Sidebar events
    document.getElementById('newChatButtonSidebar').addEventListener('click', handleNewChat);
    document.getElementById('toggleSidebarButton').addEventListener('click', handleToggleSidebar);

    // Note: Category filter events are now handled by initCategoriesDropdown() in sessionsUI.js

    // Modal events
    document.getElementById('assistantsButton').addEventListener('click', handleOpenAssistantsModal);
    document.getElementById('llmModelButton').addEventListener('click', handleOpenLlmModelModal);
    document.getElementById('mcpServersButton').addEventListener('click', handleOpenMcpServersModal);

    // Close modal buttons
    document.getElementById('closeModal').addEventListener('click', () => modalsUI.closeModal('assistantModal'));
    document.getElementById('closeLlmModelModal').addEventListener('click', () => modalsUI.closeModal('llmModelModal'));
    document.getElementById('closeCompressionModal').addEventListener('click', () => modalsUI.closeModal('compressionModal'));
    document.getElementById('closeMcpServersModal').addEventListener('click', () => modalsUI.closeModal('mcpServersModal'));
    document.getElementById('closeAssistantFormModal').addEventListener('click', () => modalsUI.closeModal('assistantFormModal'));
    document.getElementById('closeDeleteAssistantModal').addEventListener('click', () => modalsUI.closeModal('deleteAssistantModal'));
    document.getElementById('closeRagFormModal').addEventListener('click', () => modalsUI.closeModal('ragFormModal'));

    // LLM model modal events
    document.getElementById('saveLlmModelButton').addEventListener('click', handleSaveLlmModel);
    document.getElementById('cancelLlmModelButton').addEventListener('click', () => modalsUI.closeModal('llmModelModal'));
    document.getElementById('resetLlmModelButton').addEventListener('click', handleResetLlmModel);
    document.getElementById('modalProviderSelect').addEventListener('change', handleProviderChange);

    // LLM model sliders
    const temperatureSlider = document.getElementById('modalTemperatureSlider');
    const temperatureValue = document.getElementById('modalTemperatureValue');
    temperatureSlider.addEventListener('input', (e) => {
        temperatureValue.textContent = parseFloat(e.target.value).toFixed(1);
    });

    const maxTokensSlider = document.getElementById('modalMaxTokensSlider');
    const maxTokensValue = document.getElementById('modalMaxTokensValue');
    maxTokensSlider.addEventListener('input', (e) => {
        maxTokensValue.textContent = e.target.value;
    });

    // Compression modal events
    document.getElementById('applyCompressionButton').addEventListener('click', handleApplyCompression);
    document.getElementById('cancelCompressionButton').addEventListener('click', () => modalsUI.closeModal('compressionModal'));

    // Assistant form modal events
    document.getElementById('assistantForm').addEventListener('submit', handleSaveAssistantForm);
    document.getElementById('cancelAssistantFormButton').addEventListener('click', () => modalsUI.closeModal('assistantFormModal'));
    document.getElementById('cancelDeleteAssistantButton').addEventListener('click', () => modalsUI.closeModal('deleteAssistantModal'));

    // Close modals on background click
    window.addEventListener('click', (e) => {
        if (e.target.classList.contains('modal')) {
            modalsUI.closeAllModals();
        }
    });
}

/**
 * Subscribe to application state changes
 */
function subscribeToStateChanges() {
    // Subscribe to loading state changes
    appState.subscribe('loading', (isLoading) => {
        console.log('🔄 Loading state changed:', isLoading);
        sendButton.disabled = isLoading;
        messageInput.disabled = isLoading;

        if (isLoading) {
            // Add loading indicator as a message in chat
            const loadingId = messagesUI.addLoadingMessage();
            console.log('➕ Added loading message with ID:', loadingId);
            appState.setState({ loadingMessageId: loadingId });
        } else {
            // Remove loading indicator
            const state = appState.getState();
            console.log('➖ Attempting to remove loading message. Current ID:', state.loadingMessageId);
            if (state.loadingMessageId) {
                messagesUI.removeLoadingMessage(state.loadingMessageId);
                console.log('✅ Loading message removed');
                appState.setState({ loadingMessageId: null });
            } else {
                console.warn('⚠️ No loading message ID found in state');
            }
        }
    });

    // Subscribe to sessions list changes
    appState.subscribe('sessions', (sessions) => {
        const state = appState.getState();
        sessionsUI.renderSessionsList(sessions, state.currentSessionId, {
            onSessionClick: handleSessionClick,
            onRename: handleSessionRename,
            onCopy: handleSessionCopy,
            onCompress: handleSessionCompress,
            onDelete: handleSessionDelete
        });
        sidebarUI.updateSessionCount(sessions.length);

        // Update category counts
        sessionsUI.updateCategoryCounts(sessions);

        // Apply current filter
        const currentFilter = sessionsUI.getCurrentFilter();
        sessionsUI.filterSessions(currentFilter, sessions);
    });

    // Subscribe to current session changes
    appState.subscribe('currentSessionId', (sessionId) => {
        if (sessionId) {
            messagesUI.removeWelcomeMessage();
        }

        // Re-render sessions list to update active session highlighting
        const state = appState.getState();
        if (state.sessions && state.sessions.length > 0) {
            sessionsUI.renderSessionsList(state.sessions, sessionId, {
                onSessionClick: handleSessionClick,
                onRename: handleSessionRename,
                onCopy: handleSessionCopy,
                onCompress: handleSessionCompress,
                onDelete: handleSessionDelete
            });
        }
    });
}

// ============================================================================
// Event Handlers
// ============================================================================

/**
 * Handle send message
 */
async function handleSendMessage() {
    const message = messageInput.value.trim();
    if (!message || appState.getState().loading) return;

    // Clear input
    messageInput.value = '';
    messageInput.style.height = 'auto';

    // Add user message to UI with current timestamp
    messagesUI.addMessage(message, 'user', null, Date.now());

    // Check if this is a new chat
    const wasNewChat = !appState.getState().currentSessionId;

    // If new chat, create session first, then reload sessions list
    if (wasNewChat) {
        try {
            console.log('Creating new session...');
            const result = await sessionService.createSession();
            console.log('Session created:', result.sessionId);

            // Small delay to ensure session is persisted on backend
            await new Promise(resolve => setTimeout(resolve, 100));

            // Session created - reload sessions list immediately
            await sessionService.loadSessions();
            console.log('Sessions list reloaded');
        } catch (error) {
            console.error('Failed to create session:', error);
        }
    }

    try {
        // Send message via chat service
        const response = await chatService.sendMessage(message);
        console.log('Message sent, response sessionId:', response.sessionId);

        // Add assistant message to UI with metadata and timestamp
        messagesUI.addMessage(response.response, 'assistant', response.metadata, response.timestamp || Date.now());

        // Reload sessions list to update message count
        await sessionService.loadSessions();
        console.log('Sessions list reloaded to update message count');
    } catch (error) {
        console.error('Error sending message:', error);
        messagesUI.addMessage(
            `Ошибка при отправке сообщения: ${error.message}`,
            'assistant',
            null,
            Date.now()
        );
    }
}

/**
 * Handle message input keydown
 */
function handleMessageInputKeydown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSendMessage();
    }
}

/**
 * Handle message input resize
 */
function handleMessageInputResize() {
    messageInput.style.height = 'auto';
    messageInput.style.height = messageInput.scrollHeight + 'px';
}

/**
 * Handle new chat
 */
async function handleNewChat() {
    try {
        // Stop polling when creating new chat
        messagePollingService.stopPolling();

        await sessionService.createNewSession();
        messagesUI.clearMessages();
        messageInput.value = '';
        messageInput.focus();
    } catch (error) {
        console.error('Error creating new chat:', error);
        alert('Ошибка при создании нового чата');
    }
}

/**
 * Handle toggle sidebar
 */
function handleToggleSidebar() {
    sidebarUI.toggle();
}

/**
 * Handle session click
 */
async function handleSessionClick(sessionId) {
    try {
        const sessionData = await sessionService.switchSession(sessionId);

        // Render messages from the session
        if (sessionData && sessionData.messages) {
            messagesUI.renderMessages(sessionData.messages);
        } else {
            messagesUI.clearMessages();
        }

        // Start polling for new messages in this session
        messagePollingService.startPolling(sessionId);

        messageInput.focus();
    } catch (error) {
        console.error('Error switching session:', error);
        alert('Ошибка при переключении сессии');
    }
}

/**
 * Handle session rename
 */
async function handleSessionRename(sessionId, currentTitle) {
    const newTitle = prompt('Введите новое название чата:', currentTitle);
    if (!newTitle || newTitle === currentTitle) return;

    try {
        await sessionService.renameSession(sessionId, newTitle);
    } catch (error) {
        console.error('Error renaming session:', error);
        alert('Ошибка при переименовании чата');
    }
}

/**
 * Handle session copy
 */
async function handleSessionCopy(sessionId) {
    if (!confirm('Создать копию этого чата?')) return;

    try {
        await sessionService.copySession(sessionId);
    } catch (error) {
        console.error('Error copying session:', error);
        alert('Ошибка при копировании чата');
    }
}

/**
 * Handle session compress
 */
async function handleSessionCompress(sessionId) {
    try {
        // Switch to session if not current
        const state = appState.getState();
        if (state.currentSessionId !== sessionId) {
            await sessionService.switchSession(sessionId);
        }

        // Get compression info and open modal
        const info = await compressionService.getCompressionInfo(sessionId);
        modalsUI.updateCompressionModal(info);
        modalsUI.openModal('compressionModal');
    } catch (error) {
        console.error('Error opening compression modal:', error);
        alert('Ошибка при открытии окна сжатия');
    }
}

/**
 * Handle session delete
 */
async function handleSessionDelete(sessionId) {
    if (!confirm('Вы уверены, что хотите удалить этот чат?')) return;

    try {
        await sessionService.deleteSession(sessionId);
    } catch (error) {
        console.error('Error deleting session:', error);
        alert('Ошибка при удалении чата');
    }
}

/**
 * Handle open assistants modal
 */
async function handleOpenAssistantsModal() {
    try {
        const state = appState.getState();
        modalsUI.renderAssistantsList(
            state.assistants,
            handleAssistantSelect,
            handleCreateAssistant,
            handleEditAssistant,
            handleDeleteAssistant
        );
        modalsUI.openModal('assistantModal');
    } catch (error) {
        console.error('Error opening assistants modal:', error);
        alert('Ошибка при открытии списка ассистентов');
    }
}

/**
 * Handle assistant select
 */
async function handleAssistantSelect(assistantId) {
    try {
        modalsUI.closeModal('assistantModal');

        // Clear messages immediately to show new empty chat
        messagesUI.clearMessages();

        // Start assistant session (creates session, sets as current, and reloads sessions list)
        // This will trigger loading state and show loading indicator in the cleared chat
        const sessionId = await sessionService.startAssistantSession(assistantId);

        // Load session data directly from API to get any initial messages
        const sessionData = await sessionsApi.getSession(sessionId);

        // Render messages from the session if any exist
        if (sessionData && sessionData.messages && sessionData.messages.length > 0) {
            messagesUI.renderMessages(sessionData.messages);
        }

        // Start polling for new messages in this session
        messagePollingService.startPolling(sessionId);

        messageInput.focus();
    } catch (error) {
        console.error('Error starting assistant session:', error);
        alert('Ошибка при создании сессии с ассистентом');
    }
}

/**
 * Handle create assistant button click
 */
function handleCreateAssistant() {
    modalsUI.closeModal('assistantModal');
    modalsUI.openAssistantFormModal(null);
}

/**
 * Handle edit assistant button click
 * @param {Object} assistant - Assistant to edit
 */
async function handleEditAssistant(assistant) {
    try {
        modalsUI.closeModal('assistantModal');

        // Load full assistant details including systemPrompt
        const fullAssistant = await assistantsApi.getAssistant(assistant.id);

        modalsUI.openAssistantFormModal(fullAssistant);
    } catch (error) {
        console.error('Error loading assistant details:', error);
        alert('Ошибка при загрузке данных ассистента');
    }
}

/**
 * Handle delete assistant button click
 * @param {Object} assistant - Assistant to delete
 */
function handleDeleteAssistant(assistant) {
    modalsUI.openDeleteAssistantModal(assistant, async (assistantId) => {
        try {
            await assistantsApi.deleteAssistant(assistantId);

            // Reload assistants list
            await llmModelService.loadAssistants();

            // Re-render assistants modal with updated list
            const state = appState.getState();
            modalsUI.renderAssistantsList(
                state.assistants,
                handleAssistantSelect,
                handleCreateAssistant,
                handleEditAssistant,
                handleDeleteAssistant
            );

            console.log(`Assistant ${assistantId} deleted successfully`);
            alert('Ассистент успешно удалён');
        } catch (error) {
            console.error('Error deleting assistant:', error);
            alert(`Ошибка при удалении ассистента: ${error.message}`);
        }
    });
}

/**
 * Handle save assistant form
 * @param {Event} e - Form submit event
 */
async function handleSaveAssistantForm(e) {
    e.preventDefault();

    try {
        const formData = modalsUI.getAssistantFormData();

        // Validate
        if (!formData.name || !formData.systemPrompt) {
            alert('Пожалуйста, заполните обязательные поля');
            return;
        }

        if (formData.mode === 'create') {
            // Generate ID from name
            const id = generateSlug(formData.name);

            // Create assistant
            await assistantsApi.createAssistant(
                id,
                formData.name,
                formData.description,
                formData.systemPrompt
            );

            console.log(`Assistant ${id} created successfully`);
            alert('Ассистент успешно создан');
        } else {
            // Update assistant
            await assistantsApi.updateAssistant(
                formData.assistantId,
                formData.name,
                formData.description,
                formData.systemPrompt
            );

            console.log(`Assistant ${formData.assistantId} updated successfully`);
            alert('Ассистент успешно обновлён');
        }

        // Reload assistants list
        await llmModelService.loadAssistants();

        // Re-render assistants modal with updated list
        const state = appState.getState();
        modalsUI.renderAssistantsList(
            state.assistants,
            handleAssistantSelect,
            handleCreateAssistant,
            handleEditAssistant,
            handleDeleteAssistant
        );

        // Close form modal
        modalsUI.closeModal('assistantFormModal');
    } catch (error) {
        console.error('Error saving assistant:', error);
        alert(`Ошибка при сохранении ассистента: ${error.message}`);
    }
}

/**
 * Handle open LLM model modal
 */
async function handleOpenLlmModelModal() {
    try {
        const state = appState.getState();

        // Render providers and models
        modalsUI.renderProvidersList(
            state.providers,
            state.settings.providerId,
            handleProviderChange
        );

        // Load and render models for current provider
        await handleProviderChange(state.settings.providerId);

        // Update LLM model values
        modalsUI.updateLlmModelModal(state.settings);

        modalsUI.openModal('llmModelModal');
    } catch (error) {
        console.error('Error opening LLM model modal:', error);
        alert('Ошибка при открытии выбора модели');
    }
}

/**
 * Handle provider change
 */
async function handleProviderChange(providerId) {
    try {
        const models = await llmModelService.loadModels(providerId);
        const state = appState.getState();
        modalsUI.renderModelsList(models, state.settings.model);
    } catch (error) {
        console.error('Error loading models:', error);
    }
}

/**
 * Handle save LLM model settings
 */
async function handleSaveLlmModel() {
    try {
        const newSettings = modalsUI.getLlmModelFromModal();
        const autoSave = document.getElementById('autoSavePreferences').checked;

        // Update settings in memory
        await llmModelService.updateSettings(newSettings);

        // Save to database if checkbox is checked
        if (autoSave) {
            await llmModelService.savePreferences(newSettings, true);
        }

        modalsUI.closeModal('llmModelModal');
    } catch (error) {
        console.error('Error saving LLM model settings:', error);
        alert('Ошибка при сохранении настроек модели');
    }
}

/**
 * Handle reset LLM model to defaults
 */
async function handleResetLlmModel() {
    try {
        const defaults = await llmModelService.resetToDefaults();

        // Update UI with defaults
        await handleOpenLlmModelModal();

        console.log('LLM model reset to defaults');
    } catch (error) {
        console.error('Error resetting LLM model:', error);
        alert('Ошибка при сбросе настроек модели');
    }
}

/**
 * Handle open MCP servers modal
 */
async function handleOpenMcpServersModal() {
    try {
        const state = appState.getState();
        modalsUI.renderMcpServersList(state.mcpServers, handleMcpServerToggle);
        modalsUI.openModal('mcpServersModal');
    } catch (error) {
        console.error('Error opening MCP servers modal:', error);
        alert('Ошибка при открытии списка MCP серверов');
    }
}

/**
 * Handle MCP server toggle (enable/disable)
 * @param {string} serverId - Server ID
 * @param {boolean} enabled - New enabled state
 */
async function handleMcpServerToggle(serverId, enabled) {
    try {
        console.log(`Toggling MCP server ${serverId} to ${enabled ? 'enabled' : 'disabled'}`);

        // Call API to enable/disable server
        const result = enabled
            ? await mcpApi.enableServer(serverId)
            : await mcpApi.disableServer(serverId);

        if (result.success) {
            // Reload MCP servers list
            await llmModelService.loadMcpServers();

            // Re-render the modal with updated data
            const state = appState.getState();
            modalsUI.renderMcpServersList(state.mcpServers, handleMcpServerToggle);

            console.log(`Server ${serverId} ${enabled ? 'enabled' : 'disabled'} successfully`);
        } else {
            throw new Error(result.message || 'Failed to toggle server');
        }
    } catch (error) {
        console.error('Error toggling MCP server:', error);
        alert(`Ошибка при ${enabled ? 'включении' : 'отключении'} сервера: ${error.message}`);

        // Reload to reset toggle state
        await llmModelService.loadMcpServers();
        const state = appState.getState();
        modalsUI.renderMcpServersList(state.mcpServers, handleMcpServerToggle);
    }
}

/**
 * Handle apply compression
 */
async function handleApplyCompression() {
    try {
        const strategy = modalsUI.getSelectedCompressionStrategy();
        const state = appState.getState();

        modalsUI.closeModal('compressionModal');
        modalsUI.showCompressionIndicator();

        await compressionService.compressSession(state.currentSessionId, strategy);

        modalsUI.hideCompressionIndicator();

        // Reload session to show compressed messages
        await sessionService.switchSession(state.currentSessionId);

        alert('Сжатие завершено успешно');
    } catch (error) {
        console.error('Error compressing session:', error);
        modalsUI.hideCompressionIndicator();
        alert('Ошибка при сжатии диалога');
    }
}

/**
 * Handle clear all chats (called from auth.js)
 * Deletes ALL sessions
 */
async function handleClearChat() {
    const state = appState.getState();

    if (state.loading) {
        return;
    }

    const sessions = state.sessions || [];
    if (sessions.length === 0) {
        alert('Нет чатов для удаления.');
        return;
    }

    if (!confirm(`Вы уверены, что хотите удалить ВСЕ чаты (${sessions.length} шт.)? Это действие нельзя отменить!`)) {
        return;
    }

    try {
        console.log(`Deleting all ${sessions.length} sessions...`);

        // Delete all sessions
        const deletePromises = sessions.map(session =>
            fetch(`/sessions/${session.id}`, {
                method: 'DELETE',
                headers: window.authManager.getAuthHeaders()
            })
        );

        await Promise.all(deletePromises);
        console.log('All sessions deleted from server');

        // Reset UI
        appState.resetSession();
        messagesUI.clearMessages();
        messagesUI.showWelcomeMessage();

        // Reload sessions list
        console.log('Reloading sessions list...');
        await sessionService.loadSessions();
        console.log('Sessions list reloaded');

        console.log('All chats cleared successfully');
    } catch (error) {
        console.error('Error clearing all chats:', error);
        alert('Ошибка при удалении чатов');
    }
}

// Make handleClearChat globally available for auth.js
window.handleClearChat = handleClearChat;

// ============================================================================
// Initialize on DOM ready
// ============================================================================

document.addEventListener('DOMContentLoaded', initApp);
