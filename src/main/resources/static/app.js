// Конфигурация
const API_URL = '/chat';
const SESSIONS_URL = '/sessions';
const AGENTS_URL = '/agents';
const MODELS_URL = '/models';
const PROVIDERS_URL = '/providers';
const CONFIG_URL = '/config';
const COMPRESSION_URL = '/compression';
const REQUEST_TIMEOUT = 300000; // 300 секунд (5 минут)

// DOM элементы
const messagesContainer = document.getElementById('messagesContainer');
const messageInput = document.getElementById('messageInput');
const sendButton = document.getElementById('sendButton');
const statusElement = document.getElementById('status');
const sessionsList = document.getElementById('sessionsList');
const newChatButton = document.getElementById('newChatButtonSidebar');
const agentsButton = document.getElementById('agentsButton');
const agentModal = document.getElementById('agentModal');
const closeModal = document.getElementById('closeModal');
const agentsListElement = document.getElementById('agentsList');
const settingsButton = document.getElementById('settingsButton');
const settingsModal = document.getElementById('settingsModal');
const closeSettingsModal = document.getElementById('closeSettingsModal');
const saveSettingsButton = document.getElementById('saveSettingsButton');
const cancelSettingsButton = document.getElementById('cancelSettingsButton');
const modalProviderSelect = document.getElementById('modalProviderSelect');
const modalModelSelect = document.getElementById('modalModelSelect');
const modalTemperatureSlider = document.getElementById('modalTemperatureSlider');
const modalTemperatureValue = document.getElementById('modalTemperatureValue');
const modalMaxTokensSlider = document.getElementById('modalMaxTokensSlider');
const modalMaxTokensValue = document.getElementById('modalMaxTokensValue');
const modalFormatSelect = document.getElementById('modalFormatSelect');
const compressionModal = document.getElementById('compressionModal');
const closeCompressionModal = document.getElementById('closeCompressionModal');
const cancelCompressionButton = document.getElementById('cancelCompressionButton');
const applyCompressionButton = document.getElementById('applyCompressionButton');
const currentMessageCount = document.getElementById('currentMessageCount');
const currentTokenCount = document.getElementById('currentTokenCount');
const toggleSidebarButton = document.getElementById('toggleSidebarButton');
const sidebar = document.querySelector('.sidebar');

// Состояние
let isLoading = false;
let currentSessionId = null; // ID текущей сессии чата
let sessions = []; // Список всех сессий
let agents = []; // Список всех агентов
let providers = []; // Список всех провайдеров
let models = []; // Список всех доступных моделей
let currentProvider = null; // Текущий выбранный провайдер
let requestStartTime = null; // Время начала запроса
let timerInterval = null; // Интервал для обновления таймера
let sessionTotalTokens = 0; // Суммарное количество токенов в текущей сессии
let currentContextWindow = 200000; // Размер контекстного окна текущей модели (по умолчанию)
let isSidebarCollapsed = false; // Состояние боковой панели

// Настройки (текущие активные значения)
// Дефолтные значения будут заменены на значения из конфигурации бэкенда при загрузке
let currentSettings = {
    model: 'claude-haiku-4-5-20251001', // Fallback значение
    temperature: 1.0,
    maxTokens: 4096,
    format: 'PLAIN_TEXT'
};

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    // Обработчик отправки по клику
    sendButton.addEventListener('click', handleSendMessage);

    // Обработчик отправки по Enter (Shift+Enter для новой строки)
    messageInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSendMessage();
        }
    });

    // Автоматическое изменение высоты textarea
    messageInput.addEventListener('input', () => {
        messageInput.style.height = 'auto';
        messageInput.style.height = messageInput.scrollHeight + 'px';
    });

    // Обработчик изменения температуры в модальном окне
    if (modalTemperatureSlider) {
        modalTemperatureSlider.addEventListener('input', (e) => {
            modalTemperatureValue.textContent = parseFloat(e.target.value).toFixed(1);
        });
    }

    // Обработчик изменения максимального количества токенов в модальном окне
    if (modalMaxTokensSlider) {
        modalMaxTokensSlider.addEventListener('input', (e) => {
            modalMaxTokensValue.textContent = e.target.value;
        });
    }

    // Обработчик изменения провайдера
    if (modalProviderSelect) {
        modalProviderSelect.addEventListener('change', async (e) => {
            const providerId = e.target.value;
            if (providerId) {
                currentProvider = providerId;
                await loadModelsForProvider(providerId);
            }
        });
    }

    // Обработчик кнопки "Настройки"
    if (settingsButton) {
        settingsButton.addEventListener('click', openSettingsModal);
    }

    // Обработчик закрытия модального окна настроек
    if (closeSettingsModal) {
        closeSettingsModal.addEventListener('click', closeSettingsModalFunc);
    }

    // Обработчик кнопки "Сохранить"
    if (saveSettingsButton) {
        saveSettingsButton.addEventListener('click', saveSettings);
    }

    // Обработчик кнопки "Отменить"
    if (cancelSettingsButton) {
        cancelSettingsButton.addEventListener('click', closeSettingsModalFunc);
    }

    // Обработчик закрытия модального окна компрессии
    if (closeCompressionModal) {
        closeCompressionModal.addEventListener('click', closeCompressionModalFunc);
    }

    // Обработчик кнопки "Применить" компрессию
    if (applyCompressionButton) {
        applyCompressionButton.addEventListener('click', applyCompression);
    }

    // Обработчик кнопки "Отменить" компрессию
    if (cancelCompressionButton) {
        cancelCompressionButton.addEventListener('click', closeCompressionModalFunc);
    }

    // Закрытие модального окна компрессии при клике вне его
    if (compressionModal) {
        compressionModal.addEventListener('click', (e) => {
            if (e.target === compressionModal) {
                closeCompressionModalFunc();
            }
        });
    }

    // Закрытие модального окна настроек при клике вне его
    if (settingsModal) {
        settingsModal.addEventListener('click', (e) => {
            if (e.target === settingsModal) {
                closeSettingsModalFunc();
            }
        });
    }

    // Обработчик кнопки "Новый чат"
    if (newChatButton) {
        newChatButton.addEventListener('click', startNewChat);
    }

    // Обработчик кнопки "Агенты"
    if (agentsButton) {
        agentsButton.addEventListener('click', openAgentModal);
    }

    // Обработчик закрытия модального окна
    if (closeModal) {
        closeModal.addEventListener('click', closeAgentModal);
    }

    // Закрытие модального окна при клике вне его
    if (agentModal) {
        agentModal.addEventListener('click', (e) => {
            if (e.target === agentModal) {
                closeAgentModal();
            }
        });
    }

    // Обработчик кнопки переключения боковой панели
    if (toggleSidebarButton) {
        toggleSidebarButton.addEventListener('click', toggleSidebar);
    }

    // Загружаем конфигурацию, список сессий, провайдеров и моделей
    loadConfig();
    loadSessions();
    loadProviders();

    // Восстанавливаем состояние боковой панели из localStorage
    const savedSidebarState = localStorage.getItem('sidebarCollapsed');
    if (savedSidebarState === 'true') {
        toggleSidebar();
    }
});

// Основная функция отправки сообщения
async function handleSendMessage() {
    const message = messageInput.value.trim();

    if (!message || isLoading) {
        return;
    }

    // Очищаем поле ввода
    messageInput.value = '';
    messageInput.style.height = 'auto';

    // Удаляем приветственное сообщение при первом запросе
    const welcomeMessage = document.querySelector('.welcome-message');
    if (welcomeMessage) {
        welcomeMessage.remove();
    }

    // Отображаем сообщение пользователя
    addMessage(message, 'user');

    // Сохраняем время начала запроса
    requestStartTime = Date.now();

    // Показываем индикатор загрузки
    const loadingMessageId = addLoadingMessage();

    // Блокируем интерфейс
    setLoading(true);
    updateStatus('Отправка запроса...');

    try {
        // Используем текущие настройки
        const format = currentSettings.format;
        const model = currentSettings.model;
        const temperature = currentSettings.temperature;
        const maxTokens = currentSettings.maxTokens;

        // Создаем тело запроса с sessionId (если есть)
        const requestBody = {
            message,
            format,
            model,
            temperature,
            maxTokens
        };

        // Добавляем sessionId если он существует
        if (currentSessionId) {
            requestBody.sessionId = currentSessionId;
        }

        // Отправляем запрос к API с таймаутом
        const response = await fetchWithTimeout(API_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(requestBody),
        }, REQUEST_TIMEOUT);

        // Удаляем индикатор загрузки
        removeLoadingMessage(loadingMessageId);

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();

        // Вычисляем время выполнения запроса
        const elapsedTime = ((Date.now() - requestStartTime) / 1000).toFixed(2);

        // Проверяем наличие ответа
        if (data.response) {
            // Сохраняем sessionId из ответа
            if (data.sessionId) {
                currentSessionId = data.sessionId;
                console.log('Session ID:', currentSessionId);
                // Обновляем список сессий
                await loadSessions();
            }

            // Формируем метаданные для отображения
            const metadata = {
                time: elapsedTime,
                model: currentSettings.model,
                tokens: data.tokensUsed || 'N/A'
            };

            // Добавляем детальную информацию о токенах, если есть
            if (data.tokenDetails) {
                metadata.inputTokens = data.tokenDetails.inputTokens;
                metadata.outputTokens = data.tokenDetails.outputTokens;
                metadata.totalTokens = data.tokenDetails.totalTokens;
                metadata.estimatedInputTokens = data.tokenDetails.estimatedInputTokens;
                metadata.estimatedOutputTokens = data.tokenDetails.estimatedOutputTokens;
                metadata.estimatedTotalTokens = data.tokenDetails.estimatedTotalTokens;

                // Обновляем счётчик токенов сессии
                sessionTotalTokens += data.tokenDetails.totalTokens;
            }

            // Добавляем информацию о контекстном окне
            metadata.contextWindow = currentContextWindow;
            metadata.sessionTotalTokens = sessionTotalTokens;

            addMessage(data.response, 'assistant', metadata);
            updateStatus('');
        } else {
            throw new Error('Пустой ответ от сервера');
        }

    } catch (error) {
        console.error('Error:', error);

        // Удаляем индикатор загрузки
        removeLoadingMessage(loadingMessageId);

        // Определяем тип ошибки
        let errorMessage = 'Что-то пошло не так';

        if (error.name === 'AbortError') {
            errorMessage = 'Что-то пошло не так (превышено время ожидания)';
        } else if (error.message.includes('Failed to fetch')) {
            errorMessage = 'Что-то пошло не так (нет соединения с сервером)';
        }

        // Отображаем ошибку
        addMessage(errorMessage, 'error');
        updateStatus(errorMessage, 'error');

        // Очищаем статус через 3 секунды
        setTimeout(() => updateStatus(''), 3000);
    } finally {
        setLoading(false);
    }
}

// Добавление сообщения в чат
function addMessage(text, type, metadata = null) {
    console.log('addMessage called with:', { text: text?.substring(0, 100), type, metadata });

    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${type}`;

    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = text;

    console.log('Created messageDiv:', messageDiv);
    console.log('Text set to contentDiv:', contentDiv.textContent?.substring(0, 100));

    // Если есть метаданные (для сообщений assistant), добавляем их
    if (metadata && type === 'assistant') {
        const metadataDiv = document.createElement('div');
        metadataDiv.className = 'message-metadata';

        // Проверяем, есть ли раздельные токены (новый формат)
        if (metadata.inputTokens !== undefined && metadata.outputTokens !== undefined) {
            let tokensHtml = '';

            // Строка 1: Модель и время ответа
            tokensHtml += `
                <div class="metadata-line">
                    <span class="metadata-model">🤖 ${metadata.model}</span>
                    <span class="metadata-separator">│</span>
                    <span class="metadata-time">⏱ ${metadata.time}с</span>
                </div>
            `;

            // Строка 2: API токены (реальные)
            tokensHtml += `
                <div class="metadata-line">
                    <span class="metadata-section-title">API:</span>
                    <span class="metadata-tokens-input">📥 ${metadata.inputTokens}</span>
                    <span class="metadata-separator">│</span>
                    <span class="metadata-tokens-output">📤 ${metadata.outputTokens}</span>
                    <span class="metadata-separator">│</span>
                    <span class="metadata-tokens-total">🎫 ${metadata.totalTokens}</span>
                </div>
            `;

            // Строка 3: Локальные токены (оценочные), если есть
            if (metadata.estimatedInputTokens !== undefined && metadata.estimatedInputTokens > 0) {
                tokensHtml += `
                    <div class="metadata-line">
                        <span class="metadata-section-title">Local:</span>
                        <span class="metadata-tokens-estimated">📥 ${metadata.estimatedInputTokens}</span>
                `;

                // Добавляем выходные локальные токены если есть
                if (metadata.estimatedOutputTokens !== undefined && metadata.estimatedOutputTokens > 0) {
                    tokensHtml += `
                        <span class="metadata-separator">│</span>
                        <span class="metadata-tokens-estimated">📤 ${metadata.estimatedOutputTokens}</span>
                    `;
                }

                // Добавляем итоговые локальные токены если есть
                if (metadata.estimatedTotalTokens !== undefined && metadata.estimatedTotalTokens > 0) {
                    tokensHtml += `
                        <span class="metadata-separator">│</span>
                        <span class="metadata-tokens-estimated">🎫 ${metadata.estimatedTotalTokens}</span>
                    `;
                }

                tokensHtml += `</div>`;
            }

            // Строка 4: Прогресс контекстного окна
            if (metadata.sessionTotalTokens !== undefined && metadata.contextWindow !== undefined) {
                const percentage = ((metadata.sessionTotalTokens / metadata.contextWindow) * 100).toFixed(1);
                const formattedTotal = metadata.sessionTotalTokens.toLocaleString('ru-RU');
                const formattedWindow = metadata.contextWindow.toLocaleString('ru-RU');

                tokensHtml += `
                    <div class="metadata-line">
                        <span class="metadata-section-title">Context:</span>
                        <span class="metadata-context-progress">📊 ${formattedTotal} / ${formattedWindow} (${percentage}%)</span>
                    </div>
                `;
            }

            metadataDiv.innerHTML = tokensHtml;
        } else {
            // Старый формат (обратная совместимость)
            metadataDiv.innerHTML = `
                <span class="metadata-time">⏱ ${metadata.time}с</span>
                <span class="metadata-separator">│</span>
                <span class="metadata-model">🤖 ${metadata.model}</span>
                <span class="metadata-separator">│</span>
                <span class="metadata-tokens">🎫 ${metadata.tokens} токенов</span>
            `;
        }

        contentDiv.appendChild(metadataDiv);
    }

    messageDiv.appendChild(contentDiv);
    messagesContainer.appendChild(messageDiv);

    // Прокрутка вниз
    scrollToBottom();
}

// Добавление индикатора загрузки
function addLoadingMessage() {
    const loadingId = `loading-${Date.now()}`;
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message assistant loading';
    messageDiv.id = loadingId;

    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';

    const typingIndicator = document.createElement('div');
    typingIndicator.className = 'typing-indicator';
    typingIndicator.innerHTML = '<span></span><span></span><span></span>';

    // Добавляем таймер
    const timerDiv = document.createElement('div');
    timerDiv.className = 'message-timer';
    timerDiv.innerHTML = '<span class="timer-icon">⏱</span> <span class="timer-text">Время ожидания: <span class="timer-value">0.0</span>с</span>';

    contentDiv.appendChild(typingIndicator);
    contentDiv.appendChild(timerDiv);
    messageDiv.appendChild(contentDiv);
    messagesContainer.appendChild(messageDiv);

    scrollToBottom();

    // Запускаем обновление таймера
    startTimer(loadingId);

    return loadingId;
}

// Функция для запуска таймера
function startTimer(loadingId) {
    // Очищаем предыдущий интервал, если он был
    if (timerInterval) {
        clearInterval(timerInterval);
    }

    timerInterval = setInterval(() => {
        const elapsed = ((Date.now() - requestStartTime) / 1000).toFixed(1);
        const timerElement = document.querySelector(`#${loadingId} .timer-value`);
        if (timerElement) {
            timerElement.textContent = elapsed;
        }
    }, 100); // Обновляем каждые 100мс
}

// Удаление индикатора загрузки
function removeLoadingMessage(loadingId) {
    // Останавливаем таймер
    if (timerInterval) {
        clearInterval(timerInterval);
        timerInterval = null;
    }

    const loadingMessage = document.getElementById(loadingId);
    if (loadingMessage) {
        loadingMessage.remove();
    }
}

// Прокрутка к последнему сообщению
function scrollToBottom() {
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// Обновление статуса
function updateStatus(text, type = '') {
    statusElement.textContent = text;
    statusElement.className = `status ${type}`;
}

// Блокировка/разблокировка интерфейса
function setLoading(loading) {
    isLoading = loading;
    sendButton.disabled = loading;
    messageInput.disabled = loading;
}

// Fetch с таймаутом
function fetchWithTimeout(url, options, timeout) {
    return Promise.race([
        fetch(url, options),
        new Promise((_, reject) =>
            setTimeout(() => reject(new Error('AbortError')), timeout)
        )
    ]);
}

// Загрузка списка сессий
async function loadSessions() {
    try {
        const response = await fetch(SESSIONS_URL);
        if (!response.ok) {
            throw new Error('Failed to load sessions');
        }

        const data = await response.json();
        sessions = data.sessions || [];

        renderSessionsList();
    } catch (error) {
        console.error('Error loading sessions:', error);
    }
}

// Отрисовка списка сессий
function renderSessionsList() {
    if (sessions.length === 0) {
        sessionsList.innerHTML = '<div class="sessions-empty">Нет активных чатов</div>';
        return;
    }

    sessionsList.innerHTML = '';
    sessions.forEach(session => {
        const sessionItem = document.createElement('div');
        sessionItem.className = 'session-item';
        if (session.id === currentSessionId) {
            sessionItem.classList.add('active');
        }

        const title = `Чат ${sessions.indexOf(session) + 1}`;
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
            <div class="session-context-menu" style="display: none;">
                <button class="context-menu-item copy-item">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                    </svg>
                    Копировать
                </button>
                <button class="context-menu-item compression-item">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8" stroke-linecap="round" stroke-linejoin="round"/>
                        <polyline points="16 6 12 2 8 6" stroke-linecap="round" stroke-linejoin="round"/>
                        <line x1="12" y1="2" x2="12" y2="15" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    Сжатие
                </button>
                <button class="context-menu-item delete-item">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M3 6h18M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6m3 0V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
                        <path d="M10 11v6M14 11v6"></path>
                    </svg>
                    Удалить
                </button>
            </div>
        `;

        // Обработчик клика на сам элемент сессии (не на кнопку меню)
        const sessionContent = sessionItem.querySelector('.session-item-content');
        sessionContent.addEventListener('click', (e) => {
            loadSessionHistory(session.id);
        });

        // Обработчик кнопки меню (три точки)
        const menuBtn = sessionItem.querySelector('.session-menu-btn');
        const contextMenu = sessionItem.querySelector('.session-context-menu');

        menuBtn.addEventListener('click', (e) => {
            e.stopPropagation(); // Предотвращаем клик на сессию

            // Закрываем все другие открытые меню и убираем класс menu-open
            document.querySelectorAll('.session-context-menu').forEach(menu => {
                if (menu !== contextMenu) {
                    menu.style.display = 'none';
                    menu.closest('.session-item').classList.remove('menu-open');
                }
            });

            // Переключаем видимость текущего меню
            if (contextMenu.style.display === 'none' || contextMenu.style.display === '') {
                contextMenu.style.display = 'block';
                sessionItem.classList.add('menu-open');
            } else {
                contextMenu.style.display = 'none';
                sessionItem.classList.remove('menu-open');
            }
        });

        // Обработчик для кнопки "Копировать" в контекстном меню
        const copyItem = sessionItem.querySelector('.copy-item');
        copyItem.addEventListener('click', (e) => {
            e.stopPropagation();
            contextMenu.style.display = 'none';
            sessionItem.classList.remove('menu-open');
            handleCopySession(session.id);
        });

        // Обработчик для кнопки "Сжатие" в контекстном меню
        const compressionItem = sessionItem.querySelector('.compression-item');
        compressionItem.addEventListener('click', (e) => {
            e.stopPropagation();
            contextMenu.style.display = 'none';
            sessionItem.classList.remove('menu-open');
            handleCompressionForSession(session.id);
        });

        // Обработчик для кнопки "Удалить" в контекстном меню
        const deleteItem = sessionItem.querySelector('.delete-item');
        deleteItem.addEventListener('click', (e) => {
            e.stopPropagation();
            contextMenu.style.display = 'none';
            sessionItem.classList.remove('menu-open');
            handleDeleteSession(session.id);
        });

        sessionsList.appendChild(sessionItem);
    });

    // Закрываем контекстное меню при клике вне его
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.session-item')) {
            document.querySelectorAll('.session-context-menu').forEach(menu => {
                menu.style.display = 'none';
                menu.closest('.session-item')?.classList.remove('menu-open');
            });
        }
    });
}

// Загрузка истории сессии
async function loadSessionHistory(sessionId) {
    if (isLoading || sessionId === currentSessionId) {
        return;
    }

    try {
        updateStatus('Загрузка истории чата...');

        const response = await fetch(`${SESSIONS_URL}/${sessionId}`);
        if (!response.ok) {
            throw new Error('Failed to load session history');
        }

        const data = await response.json();

        // Устанавливаем текущую сессию
        currentSessionId = sessionId;

        // Очищаем контейнер сообщений
        messagesContainer.innerHTML = '';

        // Сбрасываем счётчик токенов
        sessionTotalTokens = 0;

        // Отображаем историю сообщений
        if (data.messages && data.messages.length > 0) {
            data.messages.forEach(msg => {
                // Если есть метаданные, передаем их
                const metadata = msg.metadata ? {
                    time: msg.metadata.responseTime.toFixed(2),
                    model: msg.metadata.model,
                    tokens: msg.metadata.tokensUsed,
                    // API токены (реальные)
                    inputTokens: msg.metadata.inputTokens,
                    outputTokens: msg.metadata.outputTokens,
                    totalTokens: msg.metadata.totalTokens,
                    // Локальные токены (оценочные)
                    estimatedInputTokens: msg.metadata.estimatedInputTokens,
                    estimatedOutputTokens: msg.metadata.estimatedOutputTokens,
                    estimatedTotalTokens: msg.metadata.estimatedTotalTokens
                } : null;

                // Суммируем токены из истории
                if (metadata && metadata.totalTokens) {
                    sessionTotalTokens += metadata.totalTokens;
                }

                // Добавляем информацию о контекстном окне для каждого сообщения
                if (metadata) {
                    metadata.contextWindow = currentContextWindow;
                    metadata.sessionTotalTokens = sessionTotalTokens;
                }

                addMessage(msg.content, msg.role, metadata);
            });
        } else {
            // Показываем приветственное сообщение если история пуста
            showWelcomeMessage();
        }

        // Обновляем список сессий для подсветки активной
        renderSessionsList();

        updateStatus('');
    } catch (error) {
        console.error('Error loading session history:', error);
        updateStatus('Ошибка загрузки истории чата', 'error');
        setTimeout(() => updateStatus(''), 3000);
    }
}

// Создание нового чата
async function startNewChat() {
    if (isLoading) {
        return;
    }

    // Сбрасываем sessionId
    currentSessionId = null;
    console.log('Начат новый чат');

    // Сбрасываем счётчик токенов
    sessionTotalTokens = 0;

    // Очищаем все сообщения
    messagesContainer.innerHTML = '';

    // Показываем приветственное сообщение
    showWelcomeMessage();

    // Очищаем поле ввода
    messageInput.value = '';
    messageInput.style.height = 'auto';

    // Обновляем список сессий
    renderSessionsList();

    // Очищаем статус
    updateStatus('Начат новый чат');
    setTimeout(() => updateStatus(''), 2000);
}

// Очистка текущего чата
async function handleClearChat() {
    if (isLoading || !currentSessionId) {
        return;
    }

    if (!confirm('Вы уверены, что хотите очистить историю этого чата?')) {
        return;
    }

    try {
        updateStatus('Очистка чата...');

        const response = await fetch(`${SESSIONS_URL}/${currentSessionId}/clear`, {
            method: 'POST',
        });

        if (!response.ok) {
            throw new Error('Failed to clear chat');
        }

        // Очищаем сообщения в интерфейсе
        messagesContainer.innerHTML = '';
        showWelcomeMessage();

        // Обновляем список сессий
        await loadSessions();

        updateStatus('Чат очищен', 'success');
        setTimeout(() => updateStatus(''), 2000);
    } catch (error) {
        console.error('Error clearing chat:', error);
        updateStatus('Ошибка очистки чата', 'error');
        setTimeout(() => updateStatus(''), 3000);
    }
}

// Удаление сессии
async function handleDeleteSession(sessionId) {
    if (isLoading || !sessionId) {
        return;
    }

    if (!confirm('Вы уверены, что хотите удалить эту сессию?')) {
        return;
    }

    try {
        updateStatus('Удаление сессии...');

        const response = await fetch(`${SESSIONS_URL}/${sessionId}`, {
            method: 'DELETE',
        });

        if (!response.ok) {
            throw new Error('Failed to delete session');
        }

        // Если удаляем текущую сессию, сбрасываем её и очищаем сообщения
        if (sessionId === currentSessionId) {
            currentSessionId = null;
            messagesContainer.innerHTML = '';
            showWelcomeMessage();
        }

        // Обновляем список сессий
        await loadSessions();

        updateStatus('Сессия удалена', 'success');
        setTimeout(() => updateStatus(''), 2000);
    } catch (error) {
        console.error('Error deleting session:', error);
        updateStatus('Ошибка удаления сессии', 'error');
        setTimeout(() => updateStatus(''), 3000);
    }
}

// Копирование сессии
async function handleCopySession(sessionId) {
    if (isLoading) {
        return;
    }

    try {
        updateStatus('Копирование чата...');

        const response = await fetch(`${SESSIONS_URL}/${sessionId}/copy`, {
            method: 'POST',
        });

        if (!response.ok) {
            throw new Error('Failed to copy session');
        }

        const data = await response.json();

        // Обновляем список сессий
        await loadSessions();

        // Текущая сессия остаётся текущей (не переключаемся на скопированную)
        updateStatus('Чат скопирован', 'success');
        setTimeout(() => updateStatus(''), 2000);

        console.log('Session copied successfully:', data.newSessionId);
    } catch (error) {
        console.error('Error copying session:', error);
        updateStatus('Ошибка копирования чата', 'error');
        setTimeout(() => updateStatus(''), 3000);
    }
}

// Обработчик сжатия для конкретной сессии из контекстного меню
async function handleCompressionForSession(sessionId) {
    if (isLoading) {
        return;
    }

    try {
        // Если это не текущая сессия, загружаем её историю
        if (currentSessionId !== sessionId) {
            await loadSessionHistory(sessionId);
        }

        // Открываем модальное окно компрессии
        await openCompressionModal();
    } catch (error) {
        console.error('Error opening compression modal for session:', error);
        updateStatus('Ошибка открытия окна сжатия', 'error');
        setTimeout(() => updateStatus(''), 3000);
    }
}

// Показать приветственное сообщение
function showWelcomeMessage() {
    const welcomeDiv = document.createElement('div');
    welcomeDiv.className = 'welcome-message';
    welcomeDiv.innerHTML = `
        <h2>👋 Добро пожаловать в чат!</h2>
        <p>Задайте свой вопрос ниже</p>
    `;
    messagesContainer.appendChild(welcomeDiv);
}

// Форматирование времени "назад"
function getTimeAgo(timestamp) {
    const now = Date.now();
    const diff = now - timestamp;
    const seconds = Math.floor(diff / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days} дн. назад`;
    if (hours > 0) return `${hours} ч. назад`;
    if (minutes > 0) return `${minutes} мин. назад`;
    return 'только что';
}

// Открытие модального окна выбора агента
async function openAgentModal() {
    if (isLoading) {
        return;
    }

    agentModal.classList.add('active');

    // Загружаем список агентов
    await loadAgents();
}

// Закрытие модального окна
function closeAgentModal() {
    agentModal.classList.remove('active');
}

// Загрузка списка агентов
async function loadAgents() {
    try {
        agentsListElement.innerHTML = '<div class="agents-loading">Загрузка агентов...</div>';

        const response = await fetch(AGENTS_URL);
        if (!response.ok) {
            throw new Error('Failed to load agents');
        }

        const data = await response.json();
        agents = data.agents || [];

        renderAgentsList();
    } catch (error) {
        console.error('Error loading agents:', error);
        agentsListElement.innerHTML = '<div class="agents-loading">Ошибка загрузки агентов</div>';
    }
}

// Отрисовка списка агентов
function renderAgentsList() {
    if (agents.length === 0) {
        agentsListElement.innerHTML = '<div class="agents-loading">Нет доступных агентов</div>';
        return;
    }

    agentsListElement.innerHTML = '';
    agents.forEach(agent => {
        const agentItem = document.createElement('div');
        agentItem.className = 'agent-item';

        agentItem.innerHTML = `
            <div class="agent-name">${agent.name}</div>
            <div class="agent-description">${agent.description}</div>
        `;

        agentItem.addEventListener('click', () => startAgentSession(agent.id));
        agentsListElement.appendChild(agentItem);
    });
}

// Создание новой сессии с агентом
async function startAgentSession(agentId) {
    if (isLoading) {
        return;
    }

    try {
        setLoading(true);
        updateStatus('Создание сессии с агентом...');

        const response = await fetch(`${AGENTS_URL}/start`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ agentId }),
        });

        if (!response.ok) {
            throw new Error('Failed to start agent session');
        }

        const data = await response.json();

        // Закрываем модальное окно
        closeAgentModal();

        // Устанавливаем текущую сессию
        currentSessionId = data.sessionId;

        // Очищаем контейнер сообщений
        messagesContainer.innerHTML = '';

        // Отображаем начальное сообщение агента
        addMessage('Привет', 'user');
        addMessage(data.initialMessage, 'assistant');

        // Обновляем список сессий
        await loadSessions();

        updateStatus('Сессия с агентом создана', 'success');
        setTimeout(() => updateStatus(''), 2000);
    } catch (error) {
        console.error('Error starting agent session:', error);
        updateStatus('Ошибка создания сессии с агентом', 'error');
        setTimeout(() => updateStatus(''), 3000);
    } finally {
        setLoading(false);
    }
}

// Загрузка конфигурации с бэкенда
async function loadConfig() {
    try {
        const response = await fetch(CONFIG_URL);
        if (!response.ok) {
            throw new Error('Failed to load config');
        }

        const data = await response.json();

        // Обновляем текущие настройки из конфигурации бэкенда
        currentSettings.model = data.model;
        currentSettings.temperature = data.temperature;
        currentSettings.maxTokens = data.maxTokens;
        currentSettings.format = data.format;

        console.log('Конфигурация загружена с бэкенда:', currentSettings);

        // Загружаем capabilities для получения contextWindow
        await updateContextWindow(currentSettings.model);
    } catch (error) {
        console.error('Error loading config:', error);
        // Если не удалось загрузить конфигурацию, используем дефолтные значения
    }
}

// Обновление contextWindow для модели
async function updateContextWindow(modelId) {
    try {
        const capabilities = await loadModelCapabilities(modelId);
        currentContextWindow = capabilities.contextWindow;
        console.log(`Context window for ${modelId}: ${currentContextWindow}`);
    } catch (error) {
        console.error('Error updating context window:', error);
        // Используем дефолтное значение
    }
}

// Загрузка списка провайдеров
async function loadProviders() {
    try {
        const response = await fetch(PROVIDERS_URL);
        if (!response.ok) {
            throw new Error('Failed to load providers');
        }

        const data = await response.json();
        providers = data.providers || [];

        renderProvidersList();
    } catch (error) {
        console.error('Error loading providers:', error);
    }
}

// Загрузка списка моделей для конкретного провайдера
async function loadModelsForProvider(providerId) {
    try {
        const response = await fetch(`${MODELS_URL}?provider=${providerId}`);
        if (!response.ok) {
            throw new Error('Failed to load models');
        }

        const data = await response.json();
        models = data.models || [];

        renderModelsList();
    } catch (error) {
        console.error('Error loading models:', error);
    }
}

// Загрузка capabilities для конкретной модели
async function loadModelCapabilities(modelId) {
    try {
        const response = await fetch(`${MODELS_URL}/${encodeURIComponent(modelId)}/capabilities`);
        if (!response.ok) {
            throw new Error('Failed to load model capabilities');
        }

        const capabilities = await response.json();
        return capabilities;
    } catch (error) {
        console.error('Error loading model capabilities:', error);
        // В случае ошибки возвращаем дефолтные значения
        return {
            maxTokens: 4096,
            contextWindow: 200000,
            supportsVision: false,
            supportsStreaming: true
        };
    }
}

// Обновление слайдера maxTokens на основе capabilities модели
function updateMaxTokensSlider(maxTokens) {
    const minTokens = 1024;
    const step = 1024;

    // Обновляем атрибуты слайдера
    modalMaxTokensSlider.min = minTokens;
    modalMaxTokensSlider.max = maxTokens;
    modalMaxTokensSlider.step = step;

    // Если текущее значение больше нового максимума, устанавливаем на максимум
    if (parseInt(modalMaxTokensSlider.value) > maxTokens) {
        modalMaxTokensSlider.value = maxTokens;
        modalMaxTokensValue.textContent = maxTokens;
    }

    // Обновляем лейблы
    const maxTokensLabels = document.querySelector('.max-tokens-labels');
    if (maxTokensLabels) {
        maxTokensLabels.innerHTML = `
            <span>${minTokens}</span>
            <span>${maxTokens}</span>
        `;
    }

    console.log(`MaxTokens slider updated: min=${minTokens}, max=${maxTokens}, current=${modalMaxTokensSlider.value}`);
}

// Отрисовка списка провайдеров в селекторе
function renderProvidersList() {
    if (providers.length === 0) {
        return;
    }

    modalProviderSelect.innerHTML = '';
    providers.forEach(provider => {
        const option = document.createElement('option');
        option.value = provider.id;
        option.textContent = provider.name;

        modalProviderSelect.appendChild(option);
    });

    // После загрузки провайдеров определяем провайдера текущей модели
    const currentModelId = currentSettings.model;
    const detectedProvider = detectProviderFromModel(currentModelId);

    if (detectedProvider) {
        currentProvider = detectedProvider;
        modalProviderSelect.value = detectedProvider;
        // Загружаем модели для этого провайдера
        loadModelsForProvider(detectedProvider);
    }
}

// Определяем провайдера по ID модели
function detectProviderFromModel(modelId) {
    if (modelId.startsWith('claude-')) {
        return 'claude';
    } else if (modelId.startsWith('gpt-')) {
        return 'openai';
    } else if (modelId.includes('/') || modelId.toLowerCase().includes('deepseek')) {
        return 'huggingface';
    }
    return 'claude'; // По умолчанию Claude
}

// Отрисовка списка моделей в селекторе
async function renderModelsList() {
    if (models.length === 0) {
        return;
    }

    modalModelSelect.innerHTML = '';
    models.forEach(model => {
        const option = document.createElement('option');
        option.value = model.id;
        option.textContent = model.displayName;

        // Выбираем модель из currentSettings (загруженную с бэкенда)
        if (model.id === currentSettings.model) {
            option.selected = true;
        }

        modalModelSelect.appendChild(option);
    });

    // Добавляем обработчик изменения модели для обновления слайдера maxTokens
    modalModelSelect.removeEventListener('change', handleModelChange);
    modalModelSelect.addEventListener('change', handleModelChange);

    // Загружаем capabilities для текущей выбранной модели
    if (currentSettings.model) {
        const capabilities = await loadModelCapabilities(currentSettings.model);
        updateMaxTokensSlider(capabilities.maxTokens);
    }
}

// Обработчик изменения выбранной модели
async function handleModelChange(e) {
    const modelId = e.target.value;
    if (modelId) {
        console.log(`Model changed to: ${modelId}`);

        // Загружаем capabilities для выбранной модели
        const capabilities = await loadModelCapabilities(modelId);

        // Обновляем слайдер maxTokens
        updateMaxTokensSlider(capabilities.maxTokens);
    }
}

// Открытие модального окна настроек
async function openSettingsModal() {
    if (isLoading) {
        return;
    }

    // Определяем провайдера текущей модели и устанавливаем его
    const detectedProvider = detectProviderFromModel(currentSettings.model);
    if (detectedProvider) {
        modalProviderSelect.value = detectedProvider;
    }

    // Загружаем текущие настройки в модальное окно
    modalModelSelect.value = currentSettings.model;
    modalTemperatureSlider.value = currentSettings.temperature;
    modalTemperatureValue.textContent = currentSettings.temperature.toFixed(1);
    modalMaxTokensSlider.value = currentSettings.maxTokens;
    modalMaxTokensValue.textContent = currentSettings.maxTokens;
    modalFormatSelect.value = currentSettings.format;

    // Загружаем capabilities для текущей модели и обновляем слайдер
    const capabilities = await loadModelCapabilities(currentSettings.model);
    updateMaxTokensSlider(capabilities.maxTokens);

    settingsModal.classList.add('active');
}

// Закрытие модального окна настроек
function closeSettingsModalFunc() {
    settingsModal.classList.remove('active');
}

// Сохранение настроек
async function saveSettings() {
    // Сохраняем новые настройки
    const newModel = modalModelSelect.value;
    currentSettings.model = newModel;
    currentSettings.temperature = parseFloat(modalTemperatureSlider.value);
    currentSettings.maxTokens = parseInt(modalMaxTokensSlider.value);
    currentSettings.format = modalFormatSelect.value;

    console.log('Настройки сохранены:', currentSettings);

    // Обновляем contextWindow для новой модели
    await updateContextWindow(newModel);

    // Закрываем модальное окно
    closeSettingsModalFunc();

    // Показываем уведомление
    updateStatus('Настройки сохранены', 'success');
    setTimeout(() => updateStatus(''), 2000);
}

// ========================================
// Функции компрессии
// ========================================

// Открытие модального окна компрессии
async function openCompressionModal() {
    if (!currentSessionId) {
        updateStatus('Нет активной сессии для сжатия', 'error');
        return;
    }

    // Получаем информацию о текущей сессии
    try {
        const response = await fetch(`${COMPRESSION_URL}/config/${currentSessionId}`);
        if (!response.ok) {
            throw new Error('Не удалось загрузить информацию о сессии');
        }

        const data = await response.json();

        // Обновляем статистику
        currentMessageCount.textContent = data.currentMessageCount || 0;
        currentTokenCount.textContent = data.totalTokens || 0;

        compressionModal.classList.add('active');
    } catch (error) {
        console.error('Ошибка загрузки информации о сессии:', error);
        updateStatus('Ошибка загрузки информации', 'error');
    }
}

// Закрытие модального окна компрессии
function closeCompressionModalFunc() {
    compressionModal.classList.remove('active');
}

// Применение компрессии
async function applyCompression() {
    if (!currentSessionId) {
        updateStatus('Нет активной сессии для сжатия', 'error');
        return;
    }

    // Получаем выбранную стратегию
    const selectedStrategy = document.querySelector('input[name="compressionStrategy"]:checked').value;

    // Закрываем модальное окно
    closeCompressionModalFunc();

    // Показываем индикатор компрессии
    showCompressionIndicator();

    try {
        // Получаем текущую конфигурацию
        const currentConfigResponse = await fetch(`${COMPRESSION_URL}/config/${currentSessionId}`);
        if (!currentConfigResponse.ok) {
            throw new Error('Не удалось загрузить текущую конфигурацию');
        }
        const currentConfigData = await currentConfigResponse.json();

        // Обновляем только стратегию, сохраняя остальные параметры
        const updatedConfig = {
            ...currentConfigData.config,
            strategy: selectedStrategy
        };

        // Отправляем обновленную конфигурацию
        const configResponse = await fetch(`${COMPRESSION_URL}/config`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: currentSessionId,
                config: updatedConfig
            })
        });

        if (!configResponse.ok) {
            const configError = await configResponse.json();
            throw new Error(configError.error || 'Не удалось обновить стратегию сжатия');
        }

        // Теперь вызываем API компрессии
        const response = await fetch(`${COMPRESSION_URL}/compress`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: currentSessionId,
                providerId: currentProvider || 'CLAUDE',
                model: currentSettings.model,
                contextWindowSize: currentContextWindow
            })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Ошибка сжатия');
        }

        const result = await response.json();

        // Скрываем индикатор
        hideCompressionIndicator();

        if (result.success && result.compressionPerformed) {
            // Очищаем текущий чат
            messagesContainer.innerHTML = '';

            // Сбрасываем счетчик токенов
            sessionTotalTokens = 0;

            console.log('Compression result:', result);
            console.log('New messages count:', result.newMessages?.length);
            console.log('Summary message:', result.summaryMessage);

            // Отображаем новые сообщения из ответа API
            if (result.newMessages && result.newMessages.length > 0) {
                console.log('Displaying', result.newMessages.length, 'messages');
                result.newMessages.forEach((msg, index) => {
                    console.log('Processing message', index, ':', msg);

                    // Извлекаем текст из content
                    let messageText = '';
                    if (msg.content && typeof msg.content === 'object') {
                        messageText = msg.content.text || '';
                    } else if (typeof msg.content === 'string') {
                        messageText = msg.content;
                    }

                    console.log('Message text:', messageText.substring(0, 100));

                    // Определяем тип сообщения
                    const messageType = msg.role === 'USER' ? 'user' : 'assistant';

                    // Подготавливаем метаданные если есть
                    let metadata = null;
                    if (msg.metadata) {
                        // Суммируем токены
                        if (msg.metadata.totalTokens) {
                            sessionTotalTokens += msg.metadata.totalTokens;
                        }

                        metadata = {
                            time: msg.metadata.responseTime.toFixed(2),
                            model: msg.metadata.model,
                            tokens: msg.metadata.tokensUsed,
                            inputTokens: msg.metadata.inputTokens,
                            outputTokens: msg.metadata.outputTokens,
                            totalTokens: msg.metadata.totalTokens,
                            estimatedInputTokens: msg.metadata.estimatedInputTokens,
                            estimatedOutputTokens: msg.metadata.estimatedOutputTokens,
                            estimatedTotalTokens: msg.metadata.estimatedTotalTokens,
                            contextWindow: currentContextWindow,
                            sessionTotalTokens: sessionTotalTokens // Используем обновленное значение
                        };
                    }

                    // Добавляем сообщение
                    console.log('Adding message, type:', messageType, 'metadata:', metadata);
                    addMessage(messageText, messageType, metadata);
                });
            } else {
                console.log('No new messages to display!');
            }

            // Показываем уведомление в статусе
            const compressionRatio = Math.round(result.compressionRatio * 100);
            updateStatus(
                `Сжатие выполнено: ${result.originalMessageCount} → ${result.newMessageCount} сообщений (${compressionRatio}%)`,
                'success'
            );
            setTimeout(() => updateStatus(''), 5000);
        } else {
            updateStatus(result.message || 'Сжатие не требуется', 'info');
            setTimeout(() => updateStatus(''), 3000);
        }
    } catch (error) {
        console.error('Ошибка сжатия:', error);
        hideCompressionIndicator();
        updateStatus(`Ошибка: ${error.message}`, 'error');
        setTimeout(() => updateStatus(''), 3000);
    }
}

// Показать индикатор компрессии
function showCompressionIndicator() {
    // НЕ очищаем сообщения здесь! Это будет сделано только если компрессия успешна
    // Создаем overlay с индикатором поверх текущих сообщений
    const indicator = document.createElement('div');
    indicator.className = 'compression-indicator compression-overlay';
    indicator.id = 'compressionIndicator';
    indicator.innerHTML = `
        <div class="compression-spinner"></div>
        <p>Выполняется сжатие диалога...</p>
        <p class="compression-subtitle">Генерируется суммаризация</p>
    `;

    messagesContainer.appendChild(indicator);
}

// Скрыть индикатор компрессии
function hideCompressionIndicator() {
    const indicator = document.getElementById('compressionIndicator');
    if (indicator) {
        indicator.remove();
    }
}

// Добавление информационного сообщения о результате компрессии
function addCompressionResultMessage(originalCount, newCount, compressionRatio, archivedCount) {
    const messageElement = document.createElement('div');
    messageElement.className = 'message system-message compression-result';

    messageElement.innerHTML = `
        <div class="compression-result-header">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M9 11l3 3L22 4" stroke="#667eea" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" stroke="#667eea" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <h3>Сжатие диалога выполнена</h3>
        </div>
        <div class="compression-result-stats">
            <div class="stat-item">
                <span class="stat-label">Было сообщений:</span>
                <span class="stat-value">${originalCount}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Стало сообщений:</span>
                <span class="stat-value">${newCount}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Сжато:</span>
                <span class="stat-value">${compressionRatio}%</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Архивировано:</span>
                <span class="stat-value">${archivedCount} сообщений</span>
            </div>
        </div>
        <div class="compression-result-footer">
            <p>История диалога сжата. Контекст сохранён в виде суммаризации выше. Вы можете продолжить беседу.</p>
        </div>
    `;

    messagesContainer.appendChild(messageElement);
    scrollToBottom();
}

// ========================================
// Функция переключения боковой панели
// ========================================
function toggleSidebar() {
    isSidebarCollapsed = !isSidebarCollapsed;

    if (isSidebarCollapsed) {
        sidebar.classList.add('collapsed');
    } else {
        sidebar.classList.remove('collapsed');
    }

    // Сохраняем состояние в localStorage
    localStorage.setItem('sidebarCollapsed', isSidebarCollapsed);
}
