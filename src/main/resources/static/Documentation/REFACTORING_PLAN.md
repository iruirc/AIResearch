# JavaScript Refactoring Plan

## Current Status
- Created directory structure: `js/api`, `js/services`, `js/ui`, `js/state`, `js/utils`
- Created base modules: `config.js`, `appState.js`, `helpers.js`, `chatApi.js`

## Completed Modules

### 1. Configuration (`js/config.js`)
- API endpoints
- Default settings
- UI configuration
- Compression strategies

### 2. State Management (`js/state/appState.js`)
- Centralized application state
- Event-based state updates
- Subscriber pattern for reactive UI

### 3. Utilities (`js/utils/helpers.js`)
- fetchWithTimeout
- getTimeAgo
- detectProviderFromModel
- scrollToBottom
- debounce

### 4. API Layer - COMPLETED ✅
- **`js/api/chatApi.js`** (38 lines) - Chat API wrapper
- **`js/api/sessionsApi.js`** (139 lines) - Session CRUD operations
- **`js/api/assistantsApi.js`** (54 lines) - Assistant operations
- **`js/api/settingsApi.js`** (92 lines) - Settings, providers, models
- **`js/api/compressionApi.js`** (115 lines) - Compression operations
- **`js/api/mcpApi.js`** (29 lines) - MCP servers operations
- **Total: 467 lines** - Complete API abstraction layer

### 5. Service Layer - COMPLETED ✅
- **`js/services/sessionService.js`** (171 lines) - Session lifecycle management
- **`js/services/chatService.js`** (110 lines) - Message sending workflow
- **`js/services/compressionService.js`** (139 lines) - Compression orchestration
- **`js/services/settingsService.js`** (161 lines) - Configuration management
- **Total: 581 lines** - Complete business logic layer

### 6. UI Layer - COMPLETED ✅
- **`js/ui/messagesUI.js`** (238 lines) - Messages display and metadata
- **`js/ui/sessionsUI.js`** (173 lines) - Sessions list and context menus
- **`js/ui/modalsUI.js`** (264 lines) - Modal windows management
- **`js/ui/sidebarUI.js`** (107 lines) - Sidebar control
- **Total: 782 lines** - Complete UI presentation layer

## Full Refactoring Plan

### API Modules (`js/api/`)

**sessionsApi.js**
```javascript
export const sessionsApi = {
    loadSessions: async () => { /* ... */ },
    getSession: async (sessionId) => { /* ... */ },
    deleteSession: async (sessionId) => { /* ... */ },
    copySession: async (sessionId) => { /* ... */ },
    renameSession: async (sessionId, title) => { /* ... */ },
    clearSession: async (sessionId) => { /* ... */ }
};
```

**assistantsApi.js**
```javascript
export const assistantsApi = {
    loadAgents: async () => { /* ... */ },
    startAssistantSession: async (assistantId) => { /* ... */ }
};
```

**settingsApi.js**
```javascript
export const settingsApi = {
    loadConfig: async () => { /* ... */ },
    loadProviders: async () => { /* ... */ },
    loadModels: async (providerId) => { /* ... */ },
    loadModelCapabilities: async (modelId) => { /* ... */ }
};
```

**compressionApi.js**
```javascript
export const compressionApi = {
    getConfig: async (sessionId) => { /* ... */ },
    updateConfig: async (sessionId, config) => { /* ... */ },
    compress: async (sessionId, options) => { /* ... */ },
    getArchivedMessages: async (sessionId) => { /* ... */ }
};
```

**mcpApi.js**
```javascript
export const mcpApi = {
    loadServers: async () => { /* ... */ }
};
```

### Service Layer (`js/services/`)

**sessionService.js**
- Orchestrates session operations
- Manages session list UI updates
- Handles session switching

**chatService.js**
- Manages message sending
- Handles loading states
- Updates message UI

**compressionService.js**
- Compression workflow
- Strategy selection
- Result presentation

**settingsService.js**
- Settings CRUD operations
- Provider/model management
- Context window calculations

### UI Modules (`js/ui/`)

**messagesUI.js**
```javascript
export const messagesUI = {
    addMessage: (text, type, metadata) => { /* ... */ },
    addLoadingMessage: () => { /* ... */ },
    removeLoadingMessage: (id) => { /* ... */ },
    showWelcomeMessage: () => { /* ... */ },
    clearMessages: () => { /* ... */ }
};
```

**sessionsUI.js**
```javascript
export const sessionsUI = {
    renderSessionsList: (sessions, currentSessionId) => { /* ... */ },
    showContextMenu: (sessionId, position) => { /* ... */ },
    hideContextMenu: () => { /* ... */ }
};
```

**modalsUI.js**
```javascript
export const modalsUI = {
    openModal: (modalId) => { /* ... */ },
    closeModal: (modalId) => { /* ... */ },
    // Specific modals
    assistantModal: { /* ... */ },
    settingsModal: { /* ... */ },
    compressionModal: { /* ... */ },
    mcpServersModal: { /* ... */ }
};
```

**sidebarUI.js**
```javascript
export const sidebarUI = {
    toggle: () => { /* ... */ },
    updateUserInfo: (user) => { /* ... */ }
};
```

### Main Entry Point (`js/main.js`)

```javascript
import { appState } from './state/appState.js';
import { chatService } from './services/chatService.js';
import { sessionService } from './services/sessionService.js';
// ... other imports

// Initialize application
async function initApp() {
    // Load initial data
    await settingsService.loadConfig();
    await sessionService.loadSessions();

    // Setup event listeners
    setupEventListeners();

    // Restore sidebar state
    restoreSidebarState();
}

function setupEventListeners() {
    // Message input
    document.getElementById('sendButton').addEventListener('click', handleSendMessage);
    document.getElementById('messageInput').addEventListener('keydown', handleMessageInput);

    // Sidebar
    document.getElementById('newChatButtonSidebar').addEventListener('click', handleNewChat);
    document.getElementById('toggleSidebarButton').addEventListener('click', toggleSidebar);

    // Modals
    document.getElementById('assistantsButton').addEventListener('click', openAssistantsModal);
    document.getElementById('settingsButton').addEventListener('click', openSettingsModal);
    // ... more listeners
}

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', initApp);
```

## Migration Strategy

### Phase 1: Foundation (COMPLETED)
- ✅ Create directory structure
- ✅ Extract configuration
- ✅ Create state management
- ✅ Create utilities

### Phase 2: API Layer ✅ COMPLETED
1. ✅ Created all API modules (sessionsApi, assistantsApi, settingsApi, compressionApi, mcpApi)
2. ⏳ Replace direct fetch calls in app.js with API calls (pending Phase 5)
3. ⏳ Test each API module independently (pending Phase 5)

### Phase 3: Service Layer ✅ COMPLETED
1. ✅ Created service modules (sessionService, chatService, compressionService, settingsService)
2. ✅ Implemented business logic orchestration
3. ✅ Services use API modules and update state via appState

### Phase 4: UI Layer ✅ COMPLETED
1. ✅ Extracted all UI manipulation code
2. ✅ Created UI modules (messagesUI, sessionsUI, modalsUI, sidebarUI)
3. ✅ UI modules read from state and provide clean interfaces

### Phase 5: Integration ✅ COMPLETED
1. ✅ Created main.js entry point (451 lines)
2. ✅ Wired up all modules with proper imports
3. ✅ Setup all event listeners and state subscriptions
4. ✅ Created backup app.js.backup
5. ✅ Updated index.html to use module system (`<script type="module">`)

### Phase 6: Cleanup ✅ COMPLETED
1. ✅ Removed unused code (archived app.js, app.js.backup, style.css.backup)
2. ✅ Added comprehensive JSDoc comments to all modules
3. ✅ Created optimization guide (OPTIMIZATION.md)
4. ✅ Tested application functionality - all endpoints working

## Benefits of This Architecture

1. **Separation of Concerns**: API, Business Logic, UI clearly separated
2. **Testability**: Each module can be tested independently
3. **Maintainability**: Easy to find and modify specific functionality
4. **Scalability**: Easy to add new features without touching existing code
5. **Reusability**: Modules can be reused across different parts of the app
6. **Type Safety**: Can add TypeScript later without major refactoring

## Next Steps

To continue refactoring:
1. Implement API modules one by one
2. Create corresponding service modules
3. Extract UI code into UI modules
4. Create main.js and wire everything together
5. Test thoroughly before removing old app.js

## File Structure After Complete Refactoring ✅

```
static/
├── js/
│   ├── README.md              # Architecture documentation
│   ├── REFACTORING_PLAN.md    # This file
│   ├── config.js              # Configuration constants
│   ├── main.js                # Main entry point (451 lines)
│   ├── api/                   # API Layer (467 lines total)
│   │   ├── chatApi.js         # Chat API wrapper (38 lines)
│   │   ├── sessionsApi.js     # Sessions CRUD (139 lines)
│   │   ├── assistantsApi.js       # Agents operations (54 lines)
│   │   ├── settingsApi.js     # Settings management (92 lines)
│   │   ├── compressionApi.js  # Compression API (115 lines)
│   │   └── mcpApi.js          # MCP servers (29 lines)
│   ├── services/              # Service Layer (581 lines total)
│   │   ├── chatService.js     # Chat workflow (110 lines)
│   │   ├── sessionService.js  # Session lifecycle (171 lines)
│   │   ├── compressionService.js # Compression logic (139 lines)
│   │   └── settingsService.js    # Config management (161 lines)
│   ├── ui/                    # UI Layer (782 lines total)
│   │   ├── messagesUI.js      # Messages display (238 lines)
│   │   ├── sessionsUI.js      # Sessions list (173 lines)
│   │   ├── modalsUI.js        # Modals management (264 lines)
│   │   └── sidebarUI.js       # Sidebar control (107 lines)
│   ├── state/                 # State Management
│   │   └── appState.js        # Centralized state (102 lines)
│   └── utils/                 # Utilities
│       └── helpers.js         # Helper functions (65 lines)
├── app.js.backup              # Original file backup (1770 lines)
├── auth.js                    # Authentication (unchanged)
└── index.html                 # Updated to use ES6 modules
```

**Total Modular Code**: ~2448 lines across 18 focused modules
**Average Module Size**: ~136 lines (highly maintainable)
**Original Monolith**: 1770 lines in single file
