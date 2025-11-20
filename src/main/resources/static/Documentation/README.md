# JavaScript Modules Architecture

This directory contains the modular JavaScript architecture for ResearchAI Chat application.

## Directory Structure

```
js/
├── main.js              # Main entry point (451 lines)
├── config.js            # Configuration constants
├── api/                 # API Layer - HTTP abstraction (467 lines total)
│   ├── chatApi.js       # Chat API wrapper
│   ├── sessionsApi.js   # Sessions CRUD operations
│   ├── assistantsApi.js     # Agents operations
│   ├── settingsApi.js   # Settings, providers, models
│   ├── compressionApi.js# Compression operations
│   └── mcpApi.js        # MCP servers operations
├── services/            # Service Layer - Business logic (581 lines total)
│   ├── chatService.js   # Message sending workflow
│   ├── sessionService.js# Session lifecycle management
│   ├── compressionService.js # Compression orchestration
│   └── settingsService.js    # Configuration management
├── ui/                  # UI Layer - DOM manipulation (782 lines total)
│   ├── messagesUI.js    # Message display and metadata
│   ├── sessionsUI.js    # Sessions list and context menus
│   ├── modalsUI.js      # Modal windows management
│   └── sidebarUI.js     # Sidebar control
├── state/               # State Management
│   └── appState.js      # Centralized application state with pub-sub
└── utils/               # Utilities
    └── helpers.js       # Helper functions (fetch, time, scroll, etc.)
```

## Architecture Layers

### 1. Configuration (`config.js`)
- API endpoints
- Default settings
- UI configuration
- Timeout values

### 2. State Management (`state/appState.js`)
- Centralized application state
- Pub-sub pattern for reactive updates
- State subscribers for UI updates

### 3. API Layer (`api/`)
- Encapsulates all HTTP communication
- Each module wraps specific backend endpoints
- Uses `fetchWithTimeout` for timeout handling
- Returns parsed JSON or throws errors

### 4. Service Layer (`services/`)
- Business logic orchestration
- Coordinates between API layer and state
- Handles complex workflows (e.g., session switching, compression)
- Updates application state via `appState`

### 5. UI Layer (`ui/`)
- Pure DOM manipulation
- Reads from application state
- Accepts callbacks for user interactions
- No business logic

### 6. Utilities (`utils/helpers.js`)
- Reusable helper functions
- `fetchWithTimeout` - HTTP with timeout
- `getTimeAgo` - Time formatting
- `scrollToBottom` - Smooth scrolling
- `debounce` - Function debouncing

### 7. Main Entry Point (`main.js`)
- Initializes all modules
- Sets up event listeners
- Subscribes to state changes
- Wires UI callbacks to service methods

## Key Design Patterns

1. **Layered Architecture** - Clear separation: API → Services → UI
2. **Pub-Sub Pattern** - State management with observers
3. **Callback Pattern** - UI modules use callbacks for events
4. **ES6 Modules** - Import/export for modularity
5. **Single Responsibility** - Each module has one clear purpose

## Data Flow

```
User Action (UI)
    ↓
Event Handler (main.js)
    ↓
Service Method (services/)
    ↓
API Call (api/)
    ↓
Backend
    ↓
Update State (appState)
    ↓
Notify Subscribers
    ↓
Update UI (ui/)
```

## Benefits

1. **Maintainability** - Easy to find and modify specific functionality
2. **Testability** - Each module can be tested independently
3. **Scalability** - Easy to add new features without touching existing code
4. **Reusability** - Modules can be reused across different parts
5. **Separation of Concerns** - API, business logic, and UI are clearly separated

## Migration from Legacy Code

The original monolithic `app.js` (1770 lines) has been refactored into this modular architecture:

- **app.js.backup** - Original file preserved as backup
- **Modular codebase** - ~2281 lines across 17 focused modules
- **Average module size** - ~134 lines (highly maintainable)

## Usage

All modules are loaded via ES6 module system in `index.html`:

```html
<script type="module" src="js/main.js"></script>
```

The `main.js` file imports and initializes all modules automatically.

## Development Guidelines

1. **Keep modules focused** - One responsibility per module
2. **Use appState for state** - Never store state in module variables
3. **UI callbacks for events** - UI modules should not call services directly
4. **Error handling** - Always use try-catch in service methods
5. **JSDoc comments** - Document all public functions

## Future Improvements (Phase 6)

1. Remove unused legacy code
2. Add comprehensive JSDoc comments
3. Optimize bundle size with tree-shaking
4. Consider TypeScript migration for type safety
5. Add unit tests for critical modules
