/**
 * @fileoverview Pipelines Modal UI module
 * Handles pipeline selection, creation, and editing
 */

import { pipelinesApi } from '../api/pipelinesApi.js';
import { assistantsApi } from '../api/assistantsApi.js';
import { modalsUI } from './modalsUI.js';
import { sessionService } from '../services/sessionService.js';
import { appState } from '../state/appState.js';
import { messagesUI } from './messagesUI.js';
import { messagePollingService } from '../services/messagePollingService.js';

let availableAssistants = [];
let currentPipeline = null;

/**
 * Initialize pipelines modal
 */
export async function initializePipelinesModal() {
    console.log('🔗 Initializing pipelines modal...');

    try {
        // Load available assistants
        availableAssistants = await assistantsApi.loadAssistants();
        console.log(`✅ Loaded ${availableAssistants.length} assistants for pipelines`);

        // Setup event listeners
        setupEventListeners();
    } catch (error) {
        console.error('❌ Failed to initialize pipelines modal:', error);
    }
}

/**
 * Setup event listeners
 */
function setupEventListeners() {
    // Pipelines button
    const pipelinesButton = document.getElementById('pipelinesButton');
    if (pipelinesButton) {
        pipelinesButton.addEventListener('click', openPipelinesModal);
    }

    // Close buttons
    const closePipelineModal = document.getElementById('closePipelineModal');
    if (closePipelineModal) {
        closePipelineModal.addEventListener('click', () => modalsUI.closeModal('pipelineModal'));
    }

    const closePipelineFormModal = document.getElementById('closePipelineFormModal');
    if (closePipelineFormModal) {
        closePipelineFormModal.addEventListener('click', () => modalsUI.closeModal('pipelineFormModal'));
    }

    const closeDeletePipelineModal = document.getElementById('closeDeletePipelineModal');
    if (closeDeletePipelineModal) {
        closeDeletePipelineModal.addEventListener('click', () => modalsUI.closeModal('deletePipelineModal'));
    }

    // Add stage button
    const addStageButton = document.getElementById('addStageButton');
    if (addStageButton) {
        addStageButton.addEventListener('click', addStage);
    }

    // Cancel buttons
    const cancelPipelineButton = document.getElementById('cancelPipelineButton');
    if (cancelPipelineButton) {
        cancelPipelineButton.addEventListener('click', () => modalsUI.closeModal('pipelineFormModal'));
    }

    const cancelDeletePipelineButton = document.getElementById('cancelDeletePipelineButton');
    if (cancelDeletePipelineButton) {
        cancelDeletePipelineButton.addEventListener('click', () => modalsUI.closeModal('deletePipelineModal'));
    }

    // Form submit
    const pipelineForm = document.getElementById('pipelineForm');
    if (pipelineForm) {
        pipelineForm.addEventListener('submit', handlePipelineFormSubmit);
    }
}

/**
 * Open pipelines selection modal
 */
async function openPipelinesModal() {
    try {
        console.log('📋 Opening pipelines modal...');
        const pipelines = await pipelinesApi.loadPipelines();
        console.log(`✅ Loaded ${pipelines.length} pipelines`);
        renderPipelinesList(pipelines);
        modalsUI.openModal('pipelineModal');
    } catch (error) {
        console.error('❌ Failed to load pipelines:', error);
        alert('Ошибка загрузки пайплайнов: ' + error.message);
    }
}

/**
 * Render pipelines list
 */
function renderPipelinesList(pipelines) {
    const listElement = document.getElementById('pipelinesList');
    if (!listElement) return;

    listElement.innerHTML = '';

    // Create button
    const createButton = document.createElement('button');
    createButton.className = 'create-pipeline-button';
    createButton.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>Создать пайплайн</span>
    `;
    createButton.addEventListener('click', () => openPipelineFormModal());
    listElement.appendChild(createButton);

    // Pipelines
    pipelines.forEach(pipeline => {
        const pipelineItem = createPipelineItem(pipeline);
        listElement.appendChild(pipelineItem);
    });
}

/**
 * Create pipeline item element
 */
function createPipelineItem(pipeline) {
    const item = document.createElement('div');
    item.className = 'pipeline-item';

    const contentDiv = document.createElement('div');
    contentDiv.className = 'pipeline-item-content';
    contentDiv.innerHTML = `
        <div class="pipeline-name">${pipeline.name}</div>
        <div class="pipeline-description">${pipeline.description}</div>
        <div class="pipeline-meta">${pipeline.assistantCount} этапов</div>
    `;
    contentDiv.addEventListener('click', () => selectPipeline(pipeline.id));

    const actionsDiv = document.createElement('div');
    actionsDiv.className = 'pipeline-item-actions';

    // Edit button
    const editButton = document.createElement('button');
    editButton.className = 'pipeline-action-button edit-button';
    editButton.title = 'Редактировать';
    editButton.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    `;
    editButton.addEventListener('click', (e) => {
        e.stopPropagation();
        openPipelineFormModal(pipeline.id);
    });

    // Delete button
    const deleteButton = document.createElement('button');
    deleteButton.className = 'pipeline-action-button delete-button';
    deleteButton.title = 'Удалить';
    deleteButton.textContent = '🗑️';
    deleteButton.addEventListener('click', (e) => {
        e.stopPropagation();
        openDeletePipelineModal(pipeline);
    });

    actionsDiv.appendChild(editButton);
    actionsDiv.appendChild(deleteButton);

    item.appendChild(contentDiv);
    item.appendChild(actionsDiv);

    return item;
}

/**
 * Select pipeline for execution
 */
async function selectPipeline(pipelineId) {
    try {
        console.log(`🚀 Executing pipeline: ${pipelineId}`);

        // Prompt for initial message
        const initialMessage = prompt('Введите начальное сообщение для пайплайна:');
        if (!initialMessage || !initialMessage.trim()) {
            return;
        }

        // Close modal
        modalsUI.closeModal('pipelineModal');

        // Clear messages and create new session
        messagesUI.clearMessages();
        messagePollingService.stopPolling();

        // Add user message to UI
        messagesUI.addMessage(initialMessage, 'user', null, Date.now());

        // Set loading state
        appState.setState({ loading: true });

        // Execute pipeline
        const result = await pipelinesApi.executePipeline(pipelineId, initialMessage);

        console.log('✅ Pipeline execution result:', result);

        // Set the session ID from result
        if (result.sessionId) {
            appState.setState({ currentSessionId: result.sessionId });
        }

        // Add final result to messages
        if (result.finalOutput) {
            messagesUI.addMessage(result.finalOutput, 'assistant', {
                model: result.model,
                tokens: result.totalTokensUsed,
                executionTimeMs: result.totalDurationMs,
                pipelineSteps: result.results?.length || 0
            }, Date.now());
        }

        // Reload sessions list
        await sessionService.loadSessions();

        // Start polling for the new session
        if (result.sessionId) {
            messagePollingService.startPolling(result.sessionId);
        }

        appState.setState({ loading: false });
    } catch (error) {
        console.error('❌ Failed to execute pipeline:', error);
        appState.setState({ loading: false });
        messagesUI.addMessage(
            `Ошибка при выполнении пайплайна: ${error.message}`,
            'assistant',
            null,
            Date.now()
        );
    }
}

/**
 * Open pipeline form modal (create or edit)
 */
async function openPipelineFormModal(pipelineId = null) {
    try {
        modalsUI.closeModal('pipelineModal');

        if (pipelineId) {
            // Edit mode
            console.log(`📝 Editing pipeline: ${pipelineId}`);
            currentPipeline = await pipelinesApi.getPipeline(pipelineId);
            document.getElementById('pipelineFormTitle').textContent = 'Редактировать пайплайн';
            document.getElementById('pipelineName').value = currentPipeline.name;
            document.getElementById('pipelineDescription').value = currentPipeline.description || '';
            renderStages(currentPipeline.assistantIds);
        } else {
            // Create mode
            console.log('✨ Creating new pipeline');
            currentPipeline = null;
            document.getElementById('pipelineFormTitle').textContent = 'Создать пайплайн';
            document.getElementById('pipelineName').value = '';
            document.getElementById('pipelineDescription').value = '';
            renderStages([]);
        }

        modalsUI.openModal('pipelineFormModal');
    } catch (error) {
        console.error('❌ Failed to load pipeline:', error);
        alert('Ошибка загрузки пайплайна: ' + error.message);
    }
}

/**
 * Render pipeline stages (with drag-and-drop support)
 */
function renderStages(assistantIds) {
    const stagesContainer = document.getElementById('pipelineStages');
    if (!stagesContainer) return;

    stagesContainer.innerHTML = '';

    assistantIds.forEach((assistantId, index) => {
        const stageElement = createStageElement(assistantId, index);
        stagesContainer.appendChild(stageElement);
    });

    // Make stages sortable (drag-and-drop)
    makeSortable(stagesContainer);
}

/**
 * Create stage element
 */
function createStageElement(assistantId, index) {
    const assistant = availableAssistants.find(a => a.id === assistantId);

    const stageDiv = document.createElement('div');
    stageDiv.className = 'pipeline-stage';
    stageDiv.draggable = true;
    stageDiv.dataset.index = index;

    stageDiv.innerHTML = `
        <div class="stage-header">
            <span class="stage-number">Этап ${index + 1}</span>
            <button type="button" class="remove-stage-button" title="Удалить этап">✕</button>
        </div>
        <div class="stage-body">
            <select class="stage-assistant-select" data-index="${index}">
                ${availableAssistants.map(a => `
                    <option value="${a.id}" ${a.id === assistantId ? 'selected' : ''}>
                        ${a.name}
                    </option>
                `).join('')}
            </select>
            <div class="drag-handle" title="Перетащите для изменения порядка">⇅</div>
        </div>
    `;

    // Remove button handler
    stageDiv.querySelector('.remove-stage-button').addEventListener('click', () => {
        stageDiv.remove();
        renumberStages();
    });

    return stageDiv;
}

/**
 * Add new stage
 */
function addStage() {
    const stagesContainer = document.getElementById('pipelineStages');
    if (!stagesContainer) return;

    const index = stagesContainer.children.length;

    if (availableAssistants.length === 0) {
        alert('Нет доступных ассистентов для добавления');
        return;
    }

    const stageElement = createStageElement(availableAssistants[0].id, index);
    stagesContainer.appendChild(stageElement);
    makeSortable(stagesContainer);
}

/**
 * Renumber stages after add/remove/reorder
 */
function renumberStages() {
    const stages = document.querySelectorAll('.pipeline-stage');
    stages.forEach((stage, index) => {
        stage.dataset.index = index;
        stage.querySelector('.stage-number').textContent = `Этап ${index + 1}`;
        stage.querySelector('.stage-assistant-select').dataset.index = index;
    });
}

/**
 * Make stages sortable (drag-and-drop)
 */
function makeSortable(container) {
    let draggedElement = null;

    container.addEventListener('dragstart', (e) => {
        if (e.target.classList.contains('pipeline-stage')) {
            draggedElement = e.target;
            e.target.classList.add('dragging');
        }
    });

    container.addEventListener('dragend', (e) => {
        if (e.target.classList.contains('pipeline-stage')) {
            e.target.classList.remove('dragging');
            renumberStages();
        }
    });

    container.addEventListener('dragover', (e) => {
        e.preventDefault();
        const afterElement = getDragAfterElement(container, e.clientY);
        if (afterElement == null) {
            container.appendChild(draggedElement);
        } else {
            container.insertBefore(draggedElement, afterElement);
        }
    });
}

/**
 * Get element after which to insert dragged element
 */
function getDragAfterElement(container, y) {
    const draggableElements = [...container.querySelectorAll('.pipeline-stage:not(.dragging)')];

    return draggableElements.reduce((closest, child) => {
        const box = child.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;

        if (offset < 0 && offset > closest.offset) {
            return { offset: offset, element: child };
        } else {
            return closest;
        }
    }, { offset: Number.NEGATIVE_INFINITY }).element;
}

/**
 * Handle pipeline form submit
 */
async function handlePipelineFormSubmit(e) {
    e.preventDefault();

    const name = document.getElementById('pipelineName').value.trim();
    const description = document.getElementById('pipelineDescription').value.trim();

    const stages = document.querySelectorAll('.stage-assistant-select');
    const assistantIds = Array.from(stages).map(select => select.value);

    if (!name) {
        alert('Введите название пайплайна');
        return;
    }

    if (assistantIds.length === 0) {
        alert('Добавьте хотя бы один этап');
        return;
    }

    try {
        if (currentPipeline) {
            // Update
            console.log(`💾 Updating pipeline: ${currentPipeline.id}`);
            await pipelinesApi.updatePipeline(
                currentPipeline.id,
                name,
                description,
                assistantIds
            );
            console.log('✅ Pipeline updated successfully');
        } else {
            // Create
            console.log('💾 Creating new pipeline');
            await pipelinesApi.createPipeline(
                name,
                description,
                assistantIds
            );
            console.log('✅ Pipeline created successfully');
        }

        modalsUI.closeModal('pipelineFormModal');
        openPipelinesModal(); // Refresh list
    } catch (error) {
        console.error('❌ Failed to save pipeline:', error);
        alert('Ошибка сохранения пайплайна: ' + error.message);
    }
}

/**
 * Open delete confirmation modal
 */
function openDeletePipelineModal(pipeline) {
    document.getElementById('deletePipelineName').textContent = pipeline.name;

    const confirmButton = document.getElementById('confirmDeletePipelineButton');

    // Remove old listeners by cloning
    const newConfirmButton = confirmButton.cloneNode(true);
    confirmButton.parentNode.replaceChild(newConfirmButton, confirmButton);

    newConfirmButton.addEventListener('click', async () => {
        try {
            console.log(`🗑️ Deleting pipeline: ${pipeline.id}`);
            await pipelinesApi.deletePipeline(pipeline.id);
            console.log('✅ Pipeline deleted successfully');
            modalsUI.closeModal('deletePipelineModal');
            openPipelinesModal(); // Refresh list
        } catch (error) {
            console.error('❌ Failed to delete pipeline:', error);
            alert('Ошибка удаления пайплайна: ' + error.message);
        }
    });

    modalsUI.openModal('deletePipelineModal');
}
