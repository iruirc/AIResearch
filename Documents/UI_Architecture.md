# Архитектура UI ResearchAI

## Оглавление
- [Общий обзор](#общий-обзор)
- [Структура файлов](#структура-файлов)
- [Архитектурные паттерны](#архитектурные-паттерны)
- [Детальная схема модулей](#детальная-схема-модулей)
- [Потоки данных](#потоки-данных)
- [Модальные окна](#модальные-окна)
- [Взаимодействие с Backend](#взаимодействие-с-backend)

---

## Общий обзор

ResearchAI использует **модульную архитектуру** с разделением ответственности по слоям:

```
┌─────────────────────────────────────────────────────────────┐
│                       PRESENTATION LAYER                     │
│                    (HTML Templates & CSS)                    │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────────┐
│                        UI LAYER (JS)                         │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │ Messages   │  │ Sessions   │  │  Modals    │            │
│  │    UI      │  │     UI     │  │     UI     │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────────┐
│                     SERVICE LAYER (JS)                       │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │   Chat     │  │  Session   │  │ Settings   │            │
│  │  Service   │  │  Service   │  │  Service   │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────────┐
│                       API LAYER (JS)                         │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │   Chat     │  │ Sessions   │  │ Settings   │            │
│  │    API     │  │    API     │  │    API     │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────────────┐
│                   STATE MANAGEMENT (JS)                      │
│                      (Observer Pattern)                      │
│                        AppState                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Структура файлов

```
src/main/resources/static/
├── index.html                    # Главная страница приложения
├── login.html                    # Страница авторизации
├── auth.js                       # Модуль аутентификации
│
├── styles/                       # CSS стили
│   ├── main.css                  # Базовые стили и переменные
│   ├── layout.css                # Layout и структура страницы
│   ├── components/               # Стили компонентов
│   │   ├── buttons.css
│   │   ├── forms.css
│   │   ├── messages.css
│   │   ├── modals.css
│   │   └── sessions.css
│   └── features/                 # Стили фич
│       ├── compression.css
│       └── mcp-servers.css
│
└── js/                           # JavaScript модули
    ├── main.js                   # Точка входа приложения
    ├── config.js                 # Конфигурация (endpoints, defaults)
    │
    ├── state/                    # Управление состоянием
    │   └── appState.js           # Centralized state (Observer pattern)
    │
    ├── services/                 # Бизнес-логика
    │   ├── chatService.js        # Логика чата
    │   ├── sessionService.js     # Управление сессиями
    │   ├── settingsService.js    # Управление настройками
    │   └── compressionService.js # Сжатие диалогов
    │
    ├── api/                      # API клиенты
    │   ├── chatApi.js            # Chat API
    │   ├── sessionsApi.js        # Sessions API
    │   ├── settingsApi.js        # Settings/Config API
    │   ├── assistantsApi.js          # Assistants API
    │   ├── compressionApi.js     # Compression API
    │   └── mcpApi.js             # MCP Servers API
    │
    ├── ui/                       # UI компоненты
    │   ├── messagesUI.js         # Отображение сообщений
    │   ├── sessionsUI.js         # Список сессий
    │   ├── modalsUI.js           # Модальные окна
    │   └── sidebarUI.js          # Боковая панель
    │
    └── utils/                    # Утилиты
        └── helpers.js            # Вспомогательные функции
```

---

## Архитектурные паттерны

### 1. **Observer Pattern** (AppState)
Централизованное управление состоянием с подпиской на изменения:

```javascript
// Подписка на изменения
appState.subscribe('loading', (isLoading) => {
  // Обновление UI при изменении состояния
});

// Изменение состояния
appState.setLoading(true); // Все подписчики будут уведомлены
```

### 2. **Module Pattern**
Каждый модуль экспортирует объект с методами:

```javascript
export const chatService = {
  async sendMessage(message) { ... },
  getErrorMessage(error) { ... }
};
```

### 3. **Service Layer Pattern**
Разделение логики на слои:
- **API Layer**: HTTP запросы к backend
- **Service Layer**: Бизнес-логика и оркестрация
- **UI Layer**: Отображение и пользовательский ввод

### 4. **Dependency Injection**
Модули получают зависимости через импорты:

```javascript
import { appState } from './state/appState.js';
import { chatApi } from './api/chatApi.js';
```

---

## Детальная схема модулей

### 1. Main Entry Point (main.js)

```
┌─────────────────────────────────────────────────────────────┐
│                          main.js                             │
│                    (Application Bootstrap)                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  initApp()                                                   │
│  ├─ initMessagesUI(container)                               │
│  ├─ initSessionsUI(container)                               │
│  ├─ initSidebarUI(sidebar, button)                          │
│  ├─ setupEventListeners()                                   │
│  ├─ subscribeToStateChanges()                               │
│  └─ Load Initial Data:                                      │
│      ├─ settingsService.loadConfig()                        │
│      ├─ sessionService.loadSessions()                       │
│      ├─ settingsService.loadProviders()                     │
│      ├─ settingsService.loadAssistants()                        │
│      └─ settingsService.loadMcpServers()                    │
│                                                              │
│  Event Handlers:                                             │
│  ├─ handleSendMessage()                                     │
│  ├─ handleNewChat()                                         │
│  ├─ handleSessionClick()                                    │
│  ├─ handleSessionRename()                                   │
│  ├─ handleSessionDelete()                                   │
│  ├─ handleSessionCompress()                                 │
│  ├─ handleOpenAssistantsModal()                                 │
│  ├─ handleOpenSettingsModal()                               │
│  └─ handleOpenMcpServersModal()                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2. State Management (appState.js)

```
┌─────────────────────────────────────────────────────────────┐
│                       AppState Class                         │
│                    (Singleton Instance)                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  State Properties:                                           │
│  ├─ isLoading: boolean                                      │
│  ├─ loadingMessageId: string|null                           │
│  ├─ currentSessionId: string|null                           │
│  ├─ sessions: Array<Session>                                │
│  ├─ assistants: Array<Agent>                                    │
│  ├─ providers: Array<Provider>                              │
│  ├─ models: Array<Model>                                    │
│  ├─ mcpServers: Array<MCPServer>                            │
│  ├─ currentSettings: Settings                               │
│  ├─ sessionTotalTokens: number                              │
│  ├─ currentContextWindow: number                            │
│  └─ isSidebarCollapsed: boolean                             │
│                                                              │
│  Methods:                                                    │
│  ├─ subscribe(key, callback)      # Подписка на изменения  │
│  ├─ notify(key)                    # Уведомление подписчиков│
│  ├─ getState()                     # Получить snapshot      │
│  ├─ setState(updates)              # Обновить без уведомлений│
│  ├─ setLoading(value)              # + notify              │
│  ├─ setCurrentSessionId(value)    # + notify              │
│  ├─ setSessions(value)             # + notify              │
│  ├─ setAssistants(value)               # + notify              │
│  ├─ setProviders(value)            # + notify              │
│  ├─ setModels(value)               # + notify              │
│  ├─ setMcpServers(value)           # + notify              │
│  ├─ setCurrentSettings(value)     # + notify              │
│  ├─ setSessionTotalTokens(value)  # + notify              │
│  └─ resetSession()                 # Сброс текущей сессии  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3. Service Layer

#### 3.1 Chat Service

```
┌─────────────────────────────────────────────────────────────┐
│                      chatService.js                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async sendMessage(message)                                  │
│  ├─ Validate input                                          │
│  ├─ appState.setLoading(true)                               │
│  ├─ chatApi.sendMessage(...)                                │
│  ├─ Update appState:                                        │
│  │   ├─ setCurrentSessionId (если новый чат)               │
│  │   └─ incrementSessionTotalTokens                         │
│  ├─ Process metadata (tokens, time, model)                  │
│  ├─ appState.setLoading(false)                              │
│  └─ return { response, sessionId, metadata, wasNewChat }    │
│                                                              │
│  getErrorMessage(error)                                      │
│  └─ Translate error to user-friendly Russian message        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 3.2 Session Service

```
┌─────────────────────────────────────────────────────────────┐
│                    sessionService.js                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async loadSessions()                                        │
│  ├─ sessionsApi.getSessions()                               │
│  └─ appState.setSessions(sessions)                          │
│                                                              │
│  async createSession(assistantId?)                               │
│  ├─ sessionsApi.createSession(assistantId)                      │
│  ├─ appState.setCurrentSessionId(sessionId)                 │
│  └─ loadSessions() # Обновить список                        │
│                                                              │
│  async createNewSession()                                    │
│  ├─ appState.resetSession()                                 │
│  └─ (не создает сессию на сервере до первого сообщения)     │
│                                                              │
│  async switchSession(sessionId)                              │
│  ├─ sessionsApi.getSession(sessionId)                       │
│  ├─ appState.setCurrentSessionId(sessionId)                 │
│  └─ return sessionData                                       │
│                                                              │
│  async renameSession(sessionId, newTitle)                    │
│  ├─ sessionsApi.updateSession(sessionId, { title })         │
│  └─ loadSessions()                                           │
│                                                              │
│  async deleteSession(sessionId)                              │
│  ├─ sessionsApi.deleteSession(sessionId)                    │
│  ├─ If current session → appState.resetSession()            │
│  └─ loadSessions()                                           │
│                                                              │
│  async copySession(sessionId)                                │
│  ├─ sessionsApi.copySession(sessionId)                      │
│  └─ loadSessions()                                           │
│                                                              │
│  async startAgentSession(assistantId)                            │
│  ├─ createSession(assistantId)                                  │
│  └─ return sessionId                                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 3.3 Settings Service

```
┌─────────────────────────────────────────────────────────────┐
│                   settingsService.js                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async loadConfig()                                          │
│  ├─ settingsApi.getConfig()                                 │
│  └─ appState.setCurrentSettings(config)                     │
│                                                              │
│  async loadProviders()                                       │
│  ├─ settingsApi.getProviders()                              │
│  └─ appState.setProviders(providers)                        │
│                                                              │
│  async loadModels(providerId)                                │
│  ├─ settingsApi.getModels(providerId)                       │
│  ├─ appState.setModels(models)                              │
│  └─ return models                                            │
│                                                              │
│  async loadAssistants()                                          │
│  ├─ assistantsApi.getAssistants()                                   │
│  └─ appState.setAssistants(assistants)                              │
│                                                              │
│  async loadMcpServers()                                      │
│  ├─ mcpApi.getMcpServers()                                  │
│  └─ appState.setMcpServers(servers)                         │
│                                                              │
│  async updateSettings(newSettings)                           │
│  ├─ settingsApi.updateConfig(newSettings)                   │
│  └─ appState.setCurrentSettings(newSettings)                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 3.4 Compression Service

```
┌─────────────────────────────────────────────────────────────┐
│                 compressionService.js                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async getCompressionInfo(sessionId)                         │
│  ├─ compressionApi.getCompressionInfo(sessionId)            │
│  └─ return { currentMessageCount, totalTokens, ... }        │
│                                                              │
│  async compressSession(sessionId, strategy)                  │
│  └─ compressionApi.compressSession(sessionId, strategy)     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4. API Layer

Все API модули имеют схожую структуру:

```
┌─────────────────────────────────────────────────────────────┐
│                       chatApi.js                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async sendMessage(message, sessionId, settings)             │
│  └─ POST /chat                                               │
│      Body: {                                                 │
│        message,                                              │
│        sessionId,                                            │
│        model,                                                │
│        temperature,                                          │
│        maxTokens,                                            │
│        format                                                │
│      }                                                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     sessionsApi.js                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async getSessions()         → GET /sessions                 │
│  async getSession(id)        → GET /sessions/:id             │
│  async createSession(assistantId) → POST /sessions               │
│  async updateSession(id, data) → PUT /sessions/:id           │
│  async deleteSession(id)     → DELETE /sessions/:id          │
│  async copySession(id)       → POST /sessions/:id/copy       │
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    settingsApi.js                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async getConfig()           → GET /config                   │
│  async updateConfig(config)  → POST /config                  │
│  async getProviders()        → GET /providers                │
│  async getModels(providerId) → GET /providers/:id/models     │
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                      assistantsApi.js                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async getAssistants()           → GET /assistants                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   compressionApi.js                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async getCompressionInfo(sessionId)                         │
│      → GET /compression/check/:sessionId                     │
│                                                              │
│  async compressSession(sessionId, strategy)                  │
│      → POST /compression/compress                            │
│         Body: { sessionId, strategy }                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                       mcpApi.js                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  async getMcpServers()       → GET /mcp/servers              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5. UI Layer

#### 5.1 Messages UI

```
┌─────────────────────────────────────────────────────────────┐
│                      messagesUI.js                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  addMessage(text, type, metadata)                            │
│  ├─ Create message div (.message.user/.assistant)          │
│  ├─ Add message content                                     │
│  ├─ Add metadata (for assistant messages):                  │
│  │   ├─ Model, time, tokens                                │
│  │   ├─ API tokens (input/output/total)                    │
│  │   ├─ Local tokens (estimated)                           │
│  │   └─ Context window progress                            │
│  └─ Scroll to bottom                                        │
│                                                              │
│  addLoadingMessage()                                         │
│  ├─ Create loading indicator with timer                     │
│  ├─ Start timer interval                                    │
│  └─ return loadingId                                         │
│                                                              │
│  removeLoadingMessage(loadingId)                             │
│  ├─ Stop timer interval                                     │
│  └─ Remove loading element from DOM                         │
│                                                              │
│  showWelcomeMessage()                                        │
│  └─ Display welcome screen with features                    │
│                                                              │
│  clearMessages()                                             │
│  └─ Clear all messages from container                       │
│                                                              │
│  renderMessages(messages)                                    │
│  ├─ Clear container                                         │
│  └─ Add each message with metadata                          │
│                                                              │
│  removeWelcomeMessage()                                      │
│  └─ Remove welcome screen                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 5.2 Sessions UI

```
┌─────────────────────────────────────────────────────────────┐
│                     sessionsUI.js                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  renderSessionsList(sessions, currentId, callbacks)          │
│  ├─ Clear sessions list                                     │
│  ├─ For each session:                                       │
│  │   ├─ Create session item                                │
│  │   ├─ Highlight if current                               │
│  │   ├─ Add click handler (onSessionClick)                 │
│  │   └─ Add context menu:                                  │
│  │       ├─ Rename (onRename)                              │
│  │       ├─ Copy (onCopy)                                  │
│  │       ├─ Compress (onCompress)                          │
│  │       └─ Delete (onDelete)                              │
│  └─ Show empty state if no sessions                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 5.3 Modals UI

```
┌─────────────────────────────────────────────────────────────┐
│                      modalsUI.js                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  openModal(modalId) / closeModal(modalId)                   │
│  └─ Add/remove 'active' class                               │
│                                                              │
│  renderAssistantsList(assistants, onAgentClick)                      │
│  ├─ Clear list                                              │
│  └─ Create agent items with click handlers                  │
│                                                              │
│  renderProvidersList(providers, current, onChange)           │
│  ├─ Clear select options                                    │
│  └─ Populate provider dropdown                              │
│                                                              │
│  renderModelsList(models, current)                           │
│  ├─ Clear select options                                    │
│  └─ Populate models dropdown                                │
│                                                              │
│  updateSettingsModal(settings)                               │
│  ├─ Update temperature slider                               │
│  ├─ Update maxTokens slider                                 │
│  └─ Update format select                                    │
│                                                              │
│  getSettingsFromModal()                                      │
│  └─ Collect values from form inputs                         │
│                                                              │
│  updateCompressionModal(info)                                │
│  ├─ Update message count                                    │
│  └─ Update token count                                      │
│                                                              │
│  getSelectedCompressionStrategy()                            │
│  └─ Get selected radio button value                         │
│                                                              │
│  renderMcpServersList(servers)                               │
│  ├─ Clear list                                              │
│  └─ Create server items with:                               │
│      ├─ Server name and status                              │
│      └─ Collapsible tools list                              │
│                                                              │
│  showCompressionIndicator() / hideCompressionIndicator()     │
│  └─ Show/hide overlay with spinner                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 5.4 Sidebar UI

```
┌─────────────────────────────────────────────────────────────┐
│                      sidebarUI.js                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  toggle()                                                    │
│  └─ Toggle 'collapsed' class on sidebar                     │
│                                                              │
│  updateSessionCount(count)                                   │
│  └─ Update header text with session count                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Потоки данных

### 1. Отправка сообщения (Send Message Flow)

```
┌────────────┐    ┌────────────┐    ┌────────────┐    ┌────────────┐
│    User    │───→│   main.js  │───→│ chatService│───→│  chatApi   │
│  (Input)   │    │ (Handler)  │    │   (Logic)  │    │  (HTTP)    │
└────────────┘    └────────────┘    └────────────┘    └──────┬─────┘
                                                              │
                                                              ▼
                        ┌───────────────────────────────────────────┐
                        │          Backend API Server               │
                        │         POST /chat                        │
                        └──────────────────┬────────────────────────┘
                                           │
                                           ▼
┌────────────┐    ┌────────────┐    ┌────────────┐    ┌────────────┐
│ messagesUI │◀───│  appState  │◀───│ chatService│◀───│  chatApi   │
│ (Display)  │    │ (Notify)   │    │ (Response) │    │ (Response) │
└────────────┘    └────────────┘    └────────────┘    └────────────┘
```

**Детальный поток:**

1. **User Input** → `handleSendMessage()`
   - Validate message
   - Clear input field
   - Add user message to UI

2. **Check if new chat** → Conditional logic
   - If new chat (no sessionId):
     - `sessionService.createSession()` → Create empty session
     - `sessionService.loadSessions()` → Reload session list

3. **Send message** → `chatService.sendMessage(message)`
   - Set `appState.setLoading(true)` → Triggers loading UI
   - Call `chatApi.sendMessage(...)` → HTTP POST
   - Backend processes and returns response
   - Update `appState`:
     - `setCurrentSessionId()` (if new)
     - `incrementSessionTotalTokens()`
   - Set `appState.setLoading(false)` → Removes loading UI

4. **Display response** → `messagesUI.addMessage()`
   - Add assistant message with metadata

### 2. Переключение сессии (Switch Session Flow)

```
┌────────────┐    ┌────────────┐    ┌────────────┐    ┌────────────┐
│  sessionsUI│───→│   main.js  │───→│sessionService───→│sessionsApi │
│  (Click)   │    │ (Handler)  │    │   (Logic)  │    │  (HTTP)    │
└────────────┘    └────────────┘    └────────────┘    └──────┬─────┘
                                                              │
                                                              ▼
                        ┌───────────────────────────────────────────┐
                        │          Backend API Server               │
                        │       GET /sessions/:id                   │
                        └──────────────────┬────────────────────────┘
                                           │
                                           ▼
┌────────────┐    ┌────────────┐    ┌────────────┐
│ messagesUI │◀───│  appState  │◀───│sessionService
│ (Render)   │    │ (Notify)   │    │ (Switch)   │
└────────────┘    └────────────┘    └────────────┘
```

**Детальный поток:**

1. **User clicks session** → `handleSessionClick(sessionId)`

2. **Switch session** → `sessionService.switchSession(sessionId)`
   - Call `sessionsApi.getSession(sessionId)` → GET request
   - Update `appState.setCurrentSessionId(sessionId)` → Notifies subscribers

3. **Render messages** → `messagesUI.renderMessages(messages)`
   - Clear current messages
   - Render all messages from session

### 3. Загрузка настроек (Settings Load Flow)

```
┌────────────┐    ┌────────────┐    ┌────────────┐    ┌────────────┐
│   User     │───→│   main.js  │───→│ settingsService─→│settingsApi │
│ (Button)   │    │ (Handler)  │    │   (Logic)  │    │  (HTTP)    │
└────────────┘    └────────────┘    └────────────┘    └──────┬─────┘
                                                              │
                                                              ▼
                        ┌───────────────────────────────────────────┐
                        │          Backend API Server               │
                        │  GET /config, /providers, /models         │
                        └──────────────────┬────────────────────────┘
                                           │
                                           ▼
┌────────────┐    ┌────────────┐    ┌────────────┐
│  modalsUI  │◀───│  appState  │◀───│settingsService
│ (Display)  │    │ (Notify)   │    │  (Update)  │
└────────────┘    └────────────┘    └────────────┘
```

**Детальный поток:**

1. **User opens settings** → `handleOpenSettingsModal()`

2. **Load data** → Multiple parallel requests:
   - `settingsService.loadConfig()` → Current settings
   - `settingsService.loadProviders()` → Available providers
   - `settingsService.loadModels(providerId)` → Models for provider

3. **Update UI** → `modalsUI`
   - `renderProvidersList()`
   - `renderModelsList()`
   - `updateSettingsModal()`

4. **User saves** → `handleSaveSettings()`
   - `modalsUI.getSettingsFromModal()` → Collect values
   - `settingsService.updateSettings()` → POST /config
   - `appState.setCurrentSettings()` → Update state

### 4. State Change Notification Flow

```
┌────────────────────────────────────────────────────────────┐
│                       appState                              │
│                   (Observable State)                        │
└───────────────────┬───────┬───────┬────────────────────────┘
                    │       │       │
          ┌─────────┘       │       └─────────┐
          │                 │                 │
          ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ messagesUI   │  │ sessionsUI   │  │   modalsUI   │
│ (Subscriber) │  │ (Subscriber) │  │ (Subscriber) │
└──────────────┘  └──────────────┘  └──────────────┘
```

**Пример подписки:**

```javascript
// main.js - subscribeToStateChanges()
appState.subscribe('loading', (isLoading) => {
  if (isLoading) {
    messagesUI.addLoadingMessage();
  } else {
    messagesUI.removeLoadingMessage();
  }
});

appState.subscribe('sessions', (sessions) => {
  sessionsUI.renderSessionsList(sessions, ...);
});

appState.subscribe('currentSessionId', (sessionId) => {
  if (sessionId) {
    messagesUI.removeWelcomeMessage();
  }
});
```

---

## Модальные окна

Приложение использует 4 основных модальных окна:

### 1. Assistants Modal (`#agentModal`)

**Назначение:** Выбор AI ассистента для создания специализированного чата

**Структура:**
```
┌─────────────────────────────────────┐
│  Выберите ассистента              [X]   │
├─────────────────────────────────────┤
│  ┌───────────────────────────────┐  │
│  │ Agent Name                    │  │
│  │ Agent description...          │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Another Agent                 │  │
│  │ Description...                │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

**Поток:**
1. User clicks "Ассистенты" button
2. `handleOpenAssistantsModal()` → `settingsService.loadAssistants()`
3. `modalsUI.renderAssistantsList()` → Display assistants
4. User selects agent
5. `handleAgentSelect(assistantId)` → `sessionService.startAgentSession()`
6. Create new session with agent
7. Load session and display messages

### 2. Settings Modal (`#settingsModal`)

**Назначение:** Настройка параметров AI (провайдер, модель, температура, токены, формат)

**Структура:**
```
┌─────────────────────────────────────┐
│  Настройки                    [X]   │
├─────────────────────────────────────┤
│  Провайдер:    [Claude ▼]          │
│  Модель:       [Haiku 4.5 ▼]       │
│  Температура:  [═══●═══] 1.0       │
│                Точный ←→ Креативный │
│  Макс. токены: [═══●═══] 4096      │
│                1024 ←→ 65536        │
│  Формат:       [Plain Text ▼]      │
│                                     │
│         [Отменить]  [Сохранить]    │
└─────────────────────────────────────┘
```

**Поток:**
1. User clicks "Настройки" button
2. `handleOpenSettingsModal()`
3. Load providers, models, current settings
4. User changes values
5. User clicks "Сохранить"
6. `handleSaveSettings()` → `settingsService.updateSettings()`
7. Update `appState` → Notify subscribers

### 3. Compression Modal (`#compressionModal`)

**Назначение:** Сжатие истории диалога для экономии контекста

**Структура:**
```
┌─────────────────────────────────────┐
│  Сжатие диалога               [X]   │
├─────────────────────────────────────┤
│  Сообщений: 15     Токенов: 12500   │
│                                     │
│  Алгоритм сжатия:                   │
│  ○ Полная замена                    │
│    Все сообщения → одна суммаризация│
│  ○ Скользящее окно                  │
│    Старые сжимаются, новые остаются │
│  ● По токенам                       │
│    Адаптивное сжатие по лимиту      │
│                                     │
│         [Отменить]  [Применить]     │
└─────────────────────────────────────┘
```

**Поток:**
1. User clicks "Сжать" on session
2. `handleSessionCompress()` → Switch to session
3. Load compression info
4. User selects strategy
5. `handleApplyCompression()` → `compressionService.compressSession()`
6. Show compression indicator (spinner)
7. Reload session with compressed messages

### 4. MCP Servers Modal (`#mcpServersModal`)

**Назначение:** Отображение подключенных MCP серверов и их инструментов

**Структура:**
```
┌─────────────────────────────────────┐
│  MCP Сервера                  [X]   │
├─────────────────────────────────────┤
│  ┌───────────────────────────────┐  │
│  │ Server Name    [Подключен]    │  │
│  │ Инструменты (5): [▼]          │  │
│  │   • tool_name_1               │  │
│  │     Description...            │  │
│  │   • tool_name_2               │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Another Server [Отключен]     │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

**Поток:**
1. User clicks "MCP-сервера" button
2. `handleOpenMcpServersModal()`
3. `modalsUI.renderMcpServersList()` → Display servers
4. User can expand/collapse tools list
5. View server status and available tools

---

## Взаимодействие с Backend

### REST API Endpoints

```
Chat:
  POST   /chat                    # Send message

Sessions:
  GET    /sessions                # Get all sessions
  POST   /sessions                # Create session
  GET    /sessions/:id            # Get session details
  PUT    /sessions/:id            # Update session (rename)
  DELETE /sessions/:id            # Delete session
  POST   /sessions/:id/copy       # Copy session

Configuration:
  GET    /config                  # Get current config
  POST   /config                  # Update config

Providers & Models:
  GET    /providers               # Get available providers
  GET    /providers/:id/models    # Get models for provider

Assistants:
  GET    /assistants                  # Get available assistants

Compression:
  GET    /compression/check/:id   # Get compression info
  POST   /compression/compress    # Compress session

MCP:
  GET    /mcp/servers             # Get MCP servers and tools
```

### Request/Response Format

**Send Message:**
```json
Request:
POST /chat
{
  "message": "Hello, AI!",
  "sessionId": "session-123",
  "model": "claude-haiku-4-5-20251001",
  "temperature": 1.0,
  "maxTokens": 4096,
  "format": "PLAIN_TEXT"
}

Response:
{
  "response": "Hello! How can I help you?",
  "sessionId": "session-123",
  "tokensUsed": 50,
  "tokenDetails": {
    "inputTokens": 10,
    "outputTokens": 40,
    "totalTokens": 50,
    "estimatedInputTokens": 12,
    "estimatedOutputTokens": 38,
    "estimatedTotalTokens": 50
  }
}
```

**Get Session:**
```json
Request:
GET /sessions/session-123

Response:
{
  "id": "session-123",
  "title": "Chat about AI",
  "createdAt": "2025-01-19T10:00:00Z",
  "updatedAt": "2025-01-19T10:05:00Z",
  "assistantId": null,
  "messages": [
    {
      "role": "user",
      "content": "Hello",
      "metadata": null
    },
    {
      "role": "assistant",
      "content": "Hi there!",
      "metadata": {
        "model": "claude-haiku-4-5-20251001",
        "time": "1.5",
        "tokens": 50,
        "inputTokens": 10,
        "outputTokens": 40,
        "totalTokens": 50
      }
    }
  ]
}
```

---

## Конфигурация (config.js)

```javascript
export const API_CONFIG = {
  CHAT: '/chat',
  SESSIONS: '/sessions',
  AGENTS: '/assistants',
  MODELS: '/models',
  PROVIDERS: '/providers',
  CONFIG: '/config',
  COMPRESSION: '/compression',
  MCP_SERVERS: '/mcp/servers',
  REQUEST_TIMEOUT: 300000 // 5 minutes
};

export const DEFAULT_SETTINGS = {
  model: 'claude-haiku-4-5-20251001',
  temperature: 1.0,
  maxTokens: 4096,
  format: 'PLAIN_TEXT',
  contextWindow: 200000,
  providerId: 'claude'
};

export const UI_CONFIG = {
  TIMER_UPDATE_INTERVAL: 100,      // ms
  STATUS_DISPLAY_DURATION: 3000,   // ms
  MAX_MESSAGE_INPUT_HEIGHT: 120    // px
};

export const COMPRESSION_CONFIG = {
  STRATEGIES: {
    FULL_REPLACEMENT: 'FULL_REPLACEMENT',
    SLIDING_WINDOW: 'SLIDING_WINDOW',
    TOKEN_BASED: 'TOKEN_BASED'
  }
};
```

---

## HTML Structure (index.html)

```html
<body>
  <div class="app-container">
    <!-- Sidebar -->
    <div class="sidebar">
      <div class="sidebar-settings">
        <button id="newChatButtonSidebar">Новый чат</button>
        <button id="assistantsButton">Ассистенты</button>
        <button id="mcpServersButton">MCP-сервера</button>
        <button id="settingsButton">Настройки</button>
      </div>
      <div class="sidebar-header">
        <h2>Чаты</h2>
      </div>
      <div class="sessions-list" id="sessionsList">
        <!-- Dynamic: populated by sessionsUI -->
      </div>
      <div class="sidebar-footer" id="sidebarFooter">
        <!-- Dynamic: populated by auth.js -->
      </div>
    </div>

    <!-- Main Chat Area -->
    <div class="chat-container">
      <div class="chat-header">
        <div class="header-left">
          <button id="toggleSidebarButton">☰</button>
        </div>
      </div>

      <div class="messages-container" id="messagesContainer">
        <!-- Dynamic: populated by messagesUI -->
      </div>

      <div class="input-container">
        <div class="input-wrapper">
          <textarea id="messageInput" placeholder="Введите ваш вопрос..."></textarea>
          <button id="sendButton">Send</button>
        </div>
        <div class="status" id="status"></div>
      </div>
    </div>
  </div>

  <!-- Modals -->
  <div id="agentModal" class="modal">...</div>
  <div id="settingsModal" class="modal">...</div>
  <div id="compressionModal" class="modal">...</div>
  <div id="mcpServersModal" class="modal">...</div>

  <!-- Compression Indicator -->
  <div id="compressionIndicator" class="compression-indicator">...</div>

  <script src="auth.js"></script>
  <script type="module" src="js/main.js"></script>
</body>
```

---

## CSS Architecture

### 1. Структура стилей

```
styles/
├── main.css              # CSS Variables, base styles, typography
├── layout.css            # Grid layout, sidebar, chat container
├── components/
│   ├── buttons.css       # Button styles
│   ├── forms.css         # Input, textarea, select styles
│   ├── messages.css      # Message bubbles, metadata
│   ├── modals.css        # Modal windows
│   └── sessions.css      # Session list items
└── features/
    ├── compression.css   # Compression UI
    └── mcp-servers.css   # MCP servers list
```

### 2. CSS Variables (main.css)

```css
:root {
  /* Colors */
  --primary-color: #007bff;
  --bg-dark: #1a1a1a;
  --bg-light: #2a2a2a;
  --text-primary: #ffffff;
  --text-secondary: #b0b0b0;

  /* Spacing */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;

  /* Layout */
  --sidebar-width: 300px;
  --header-height: 60px;
}
```

### 3. Layout Structure (layout.css)

```
┌─────────────────────────────────────────────────────────┐
│                     app-container                        │
│  ┌────────────────┬──────────────────────────────────┐  │
│  │                │                                  │  │
│  │   sidebar      │      chat-container              │  │
│  │  (300px wide)  │      (flex: 1)                   │  │
│  │                │                                  │  │
│  │  ┌──────────┐  │  ┌────────────────────────────┐ │  │
│  │  │ settings │  │  │      chat-header           │ │  │
│  │  │ buttons  │  │  └────────────────────────────┘ │  │
│  │  └──────────┘  │                                  │  │
│  │                │  ┌────────────────────────────┐ │  │
│  │  ┌──────────┐  │  │                            │ │  │
│  │  │ sessions │  │  │   messages-container       │ │  │
│  │  │   list   │  │  │   (flex: 1, overflow-y)    │ │  │
│  │  │          │  │  │                            │ │  │
│  │  └──────────┘  │  └────────────────────────────┘ │  │
│  │                │                                  │  │
│  │  ┌──────────┐  │  ┌────────────────────────────┐ │  │
│  │  │  footer  │  │  │    input-container         │ │  │
│  │  └──────────┘  │  │    (fixed at bottom)       │ │  │
│  │                │  └────────────────────────────┘ │  │
│  └────────────────┴──────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## Authentication (auth.js)

```
┌─────────────────────────────────────────────────────────┐
│                      auth.js                             │
│                 (Global Script, not ES6)                 │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  window.authManager                                      │
│  ├─ checkAuth()           # Check if user authenticated │
│  ├─ login()               # Redirect to login page      │
│  ├─ logout()              # Clear auth and redirect     │
│  ├─ getAuthHeaders()      # Return Authorization header │
│  └─ getUserInfo()         # Get current user info       │
│                                                          │
│  On page load:                                           │
│  ├─ checkAuth()                                          │
│  ├─ If not authenticated → redirect to login.html       │
│  └─ If authenticated → render user info in footer       │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## Ключевые особенности архитектуры

### 1. Разделение ответственности (Separation of Concerns)
- **API Layer**: Только HTTP запросы
- **Service Layer**: Бизнес-логика и координация
- **UI Layer**: Отображение и взаимодействие с пользователем
- **State Management**: Централизованное состояние

### 2. Реактивность (Reactivity)
- Observer Pattern через `appState`
- Автоматическое обновление UI при изменении состояния
- Подписчики уведомляются о всех изменениях

### 3. Модульность (Modularity)
- ES6 модули с явными импортами/экспортами
- Каждый модуль имеет одну ответственность
- Легко тестировать и расширять

### 4. Асинхронность (Async/Await)
- Все API вызовы асинхронные
- Обработка ошибок через try/catch
- Loading states для UX

### 5. Типизация данных (Data Structures)

```javascript
// Session
{
  id: string,
  title: string,
  createdAt: timestamp,
  updatedAt: timestamp,
  assistantId: string|null,
  messages: Message[]
}

// Message
{
  role: 'user'|'assistant',
  content: string,
  metadata: MessageMetadata|null
}

// MessageMetadata
{
  model: string,
  time: string,
  tokens: number,
  inputTokens: number,
  outputTokens: number,
  totalTokens: number,
  estimatedInputTokens: number,
  estimatedOutputTokens: number,
  estimatedTotalTokens: number,
  contextWindow: number,
  sessionTotalTokens: number
}

// Settings
{
  model: string,
  temperature: number,
  maxTokens: number,
  format: 'PLAIN_TEXT'|'JSON'|'XML',
  contextWindow: number,
  providerId: string
}

// Provider
{
  id: string,
  name: string
}

// Model
{
  id: string,
  name: string,
  displayName: string
}

// Agent
{
  id: string,
  name: string,
  description: string
}

// MCPServer
{
  name: string,
  connected: boolean,
  tools: Tool[]
}

// Tool
{
  name: string,
  description: string
}
```

---

## Диаграмма жизненного цикла приложения

```
┌─────────────────────────────────────────────────────────┐
│                    Page Load                             │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│             auth.js: checkAuth()                         │
│   ┌─────────────┬──────────────┐                        │
│   │ Not Auth?   │  Authenticated│                        │
│   ▼             ▼              │                        │
│ Redirect     Continue          │                        │
│ to login                       │                        │
└────────────────────────────────┼─────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────┐
│          DOMContentLoaded → main.js:initApp()            │
├─────────────────────────────────────────────────────────┤
│  1. Initialize UI modules (DOM references)               │
│  2. Setup event listeners                                │
│  3. Subscribe to state changes                           │
│  4. Load initial data:                                   │
│     - settingsService.loadConfig()                       │
│     - sessionService.loadSessions()                      │
│     - settingsService.loadProviders()                    │
│     - settingsService.loadAssistants()                       │
│     - settingsService.loadMcpServers()                   │
│  5. Show welcome message (if no session)                 │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│              Application Ready                           │
│           (Waiting for user interaction)                 │
└─────────────────────────────────────────────────────────┘
                    │
                    ├─→ User sends message
                    │   └─→ handleSendMessage() flow
                    │
                    ├─→ User switches session
                    │   └─→ handleSessionClick() flow
                    │
                    ├─→ User opens settings
                    │   └─→ handleOpenSettingsModal() flow
                    │
                    ├─→ User opens assistants
                    │   └─→ handleOpenAssistantsModal() flow
                    │
                    ├─→ User compresses session
                    │   └─→ handleSessionCompress() flow
                    │
                    └─→ User creates new chat
                        └─→ handleNewChat() flow
```

---

## Производительность и оптимизация

### 1. Ленивая загрузка (Lazy Loading)
- Модальные окна загружаются контент только при открытии
- MCP tools список сворачивается по умолчанию

### 2. Debouncing
- Timer updates используют `setInterval` с разумным интервалом (100ms)

### 3. Минимизация DOM операций
- `renderMessages()` очищает контейнер один раз
- Batch updates для списка сессий

### 4. Кэширование состояния
- `appState` хранит все данные в памяти
- Минимум повторных запросов к API

### 5. Event Delegation
- Модальные окна используют один обработчик на контейнер
- Session list items используют callbacks вместо inline handlers

---

## Расширяемость

### Добавление нового модального окна

1. **HTML** (index.html):
```html
<div id="myModal" class="modal">
  <div class="modal-content">
    <div class="modal-header">
      <h3>My Modal</h3>
      <button id="closeMyModal" class="close-button">&times;</button>
    </div>
    <div id="myModalContent">
      <!-- Content here -->
    </div>
  </div>
</div>
```

2. **CSS** (styles/components/modals.css):
```css
#myModal .my-modal-specific-class {
  /* Styles */
}
```

3. **JavaScript** (js/ui/modalsUI.js):
```javascript
renderMyModalContent(data) {
  const container = document.getElementById('myModalContent');
  // Render logic
}
```

4. **main.js**:
```javascript
document.getElementById('myButton').addEventListener('click', handleOpenMyModal);
document.getElementById('closeMyModal').addEventListener('click', () => modalsUI.closeModal('myModal'));

async function handleOpenMyModal() {
  const data = await myService.loadData();
  modalsUI.renderMyModalContent(data);
  modalsUI.openModal('myModal');
}
```

### Добавление нового API endpoint

1. **API client** (js/api/myApi.js):
```javascript
import { API_CONFIG } from '../config.js';

export const myApi = {
  async getData() {
    const response = await fetch('/my-endpoint', {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        ...window.authManager.getAuthHeaders()
      }
    });
    return response.json();
  }
};
```

2. **Service** (js/services/myService.js):
```javascript
import { myApi } from '../api/myApi.js';
import { appState } from '../state/appState.js';

export const myService = {
  async loadData() {
    const data = await myApi.getData();
    appState.setMyData(data);
    return data;
  }
};
```

3. **Update appState** (js/state/appState.js):
```javascript
setMyData(value) {
  this.myData = value;
  this.notify('myData');
}
```

---

## Debugging Tips

### 1. State Debugging
```javascript
// Проверить текущее состояние
console.log(appState.getState());

// Отследить изменения
appState.subscribe('loading', (value) => {
  console.log('Loading changed:', value);
});
```

### 2. API Debugging
```javascript
// В любом API файле добавить логирование
console.log('Request:', url, options);
console.log('Response:', await response.json());
```

### 3. UI Debugging
```javascript
// В messagesUI.js
console.log('Adding message:', text, type, metadata);

// В sessionsUI.js
console.log('Rendering sessions:', sessions);
```

---

## Заключение

Архитектура UI ResearchAI построена на принципах:
- **Модульности**: Каждый модуль имеет четкую ответственность
- **Реактивности**: State management с Observer Pattern
- **Разделения слоев**: API → Service → UI → Presentation
- **Расширяемости**: Легко добавлять новые функции
- **Поддерживаемости**: Понятная структура и именование

Система использует ES6 модули, async/await, и современные паттерны проектирования для создания масштабируемого и производительного пользовательского интерфейса.
