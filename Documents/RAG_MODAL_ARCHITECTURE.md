# RAG Modal Architecture

**Дата:** 2025-11-27

## Overview

Документация архитектуры модуля RAG Modal после рефакторинга монолитного файла `ragModal.js` (2834 строк) в модульную структуру.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              main.js                                         │
│                     import { initializeRAGModal }                            │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           rag/index.js                                       │
│                  export { initializeRAGModal, RAGModal }                     │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           RagModal.js                                        │
│                    Main Coordinator Class                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  - documents: []          - currentMode: 'list'                     │    │
│  │  - tests: []              - activeModalTab: 'documents'             │    │
│  │  - searchPreferences      - editingDocument/editingTest             │    │
│  │                                                                     │    │
│  │  Methods: initialize(), open()                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└──────────┬──────────────┬──────────────┬──────────────┬──────────────┬──────┘
           │              │              │              │              │
           ▼              ▼              ▼              ▼              ▼
    ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
    │documents/│   │settings/ │   │  tests/  │   │ shared/  │   │  utils/  │
    └────┬─────┘   └────┬─────┘   └────┬─────┘   └────┬─────┘   └────┬─────┘
         │              │              │              │              │
         ▼              ▼              ▼              ▼              ▼
```

## Module Structure

### 1. Root Level (`rag/`)

```
rag/
├── index.js          # Entry point, re-exports
└── RagModal.js       # Main coordinator class
```

**index.js** - Main entry point
- Exports `initializeRAGModal()` function
- Exports `RAGModal` class
- Re-exports sub-modules for external access

**RagModal.js** - Coordinator
- Initializes all sub-modules
- Manages global state
- Delegates operations to appropriate modules

### 2. Documents Module (`documents/`)

```
documents/
├── index.js
├── DocumentManager.js      # CRUD operations
├── DocumentRenderer.js     # UI rendering
└── DocumentEventHandler.js # Event handlers
```

**Responsibilities:**
- Load/Create/Update/Delete documents
- Render documents list
- Handle document form interactions
- Toggle document enabled state

**External Dependencies:**
- `ragApi` for API calls
- `modalsUI` for modal operations

### 3. Settings Module (`settings/`)

```
settings/
├── index.js
├── SettingsManager.js      # Settings CRUD
├── SettingsRenderer.js     # Form rendering
└── SettingsEventHandler.js # Event handlers
```

**Responsibilities:**
- Load/Save/Reset search preferences
- Manage Cross-Encoder provider/model selection
- Render settings form
- Show notifications

**External Dependencies:**
- `ragApi` for preferences API
- `llmModelApi` for LLM models

### 4. Tests Module (`tests/`)

```
tests/
├── index.js
├── TestManager.js       # CRUD operations
├── TestRenderer.js      # UI rendering
├── TestEventHandler.js  # Event handlers
├── TestExecutor.js      # Test execution
└── TestValidator.js     # JSON validation
```

**Responsibilities:**
- Load/Create/Update/Delete tests
- Render tests list
- Validate test JSON format
- Execute tests with progress tracking
- Handle test file uploads

**External Dependencies:**
- `ragTestApi` for test API calls
- `shared/FileHandler` for file operations
- `shared/ChunksRenderer` for results display

### 5. Shared Module (`shared/`)

```
shared/
├── index.js
├── FileHandler.js      # File upload/management
├── TabManager.js       # Tab switching logic
├── ChunksRenderer.js   # Chunks display
└── Exporter.js         # Results export
```

**Responsibilities:**
- Common file upload functionality
- Tab management (modal tabs, form tabs)
- Render RAG chunks for queries
- Export results to Markdown/download

### 6. Utils Module (`utils/`)

```
utils/
├── index.js
└── RagUtils.js         # Utility functions
```

**Utilities:**
- `escapeHtml(text)` - XSS protection
- `formatTime(ms)` - Time formatting
- `formatFileSize(bytes)` - Size formatting

## Data Flow

### Document Operations

```
User Action           Module                    API
    │                   │                        │
    │ Click "Add"       │                        │
    ├──────────────────►│ DocumentEventHandler   │
    │                   │         │              │
    │                   │         ▼              │
    │                   │ DocumentManager        │
    │                   │         │              │
    │                   │         │──────────────►│ ragApi.addDocument()
    │                   │         │◄─────────────│
    │                   │         ▼              │
    │                   │ DocumentRenderer       │
    │◄──────────────────│ (update list)          │
```

### Test Execution

```
User Action           Module                    API
    │                   │                        │
    │ Click "Execute"   │                        │
    ├──────────────────►│ TestEventHandler       │
    │                   │         │              │
    │                   │         ▼              │
    │                   │ TestExecutor           │
    │                   │         │              │
    │                   │         │──────────────►│ ragTestApi.execute()
    │                   │         │◄─────────────│ (streaming)
    │                   │         ▼              │
    │                   │ ChunksRenderer         │
    │◄──────────────────│ (show progress/results)│
```

## Module Dependencies

```
                    ┌─────────────────┐
                    │   External APIs  │
                    │  ragApi          │
                    │  ragTestApi      │
                    │  llmModelApi     │
                    │  modalsUI        │
                    │  appState        │
                    └────────┬────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   documents/    │ │    settings/    │ │     tests/      │
│   Manager       │ │    Manager      │ │    Manager      │
│   Renderer      │ │    Renderer     │ │    Renderer     │
│   EventHandler  │ │    EventHandler │ │    EventHandler │
└────────┬────────┘ └────────┬────────┘ │    Executor     │
         │                   │          │    Validator    │
         │                   │          └────────┬────────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │     shared/     │
                    │  FileHandler    │
                    │  TabManager     │
                    │  ChunksRenderer │
                    │  Exporter       │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │     utils/      │
                    │    RagUtils     │
                    └─────────────────┘
```

## Class Diagrams

### RagModal (Coordinator)

```
┌──────────────────────────────────────────────────────────────┐
│                         RagModal                              │
├──────────────────────────────────────────────────────────────┤
│ - documents: Document[]                                       │
│ - tests: Test[]                                               │
│ - currentMode: 'list' | 'add' | 'edit'                       │
│ - activeModalTab: 'documents' | 'settings' | 'testing'       │
│ - editingDocument: Document | null                            │
│ - editingTest: Test | null                                    │
│ - searchPreferences: SearchPreferences | null                 │
├──────────────────────────────────────────────────────────────┤
│ - documentManager: DocumentManager                            │
│ - settingsManager: SettingsManager                            │
│ - testManager: TestManager                                    │
│ - tabManager: TabManager                                      │
├──────────────────────────────────────────────────────────────┤
│ + initialize(): Promise<void>                                 │
│ + open(): Promise<void>                                       │
└──────────────────────────────────────────────────────────────┘
```

### DocumentManager

```
┌──────────────────────────────────────────────────────────────┐
│                     DocumentManager                           │
├──────────────────────────────────────────────────────────────┤
│ - modal: RagModal                                             │
│ - renderer: DocumentRenderer                                  │
│ - eventHandler: DocumentEventHandler                          │
├──────────────────────────────────────────────────────────────┤
│ + loadDocuments(): Promise<void>                              │
│ + handleAddDocument(): Promise<void>                          │
│ + handleUpdateDocument(): Promise<void>                       │
│ + handleDeleteDocument(id, name): Promise<void>               │
│ + handleToggleDocument(id, enabled): Promise<void>            │
└──────────────────────────────────────────────────────────────┘
```

### TestExecutor

```
┌──────────────────────────────────────────────────────────────┐
│                       TestExecutor                            │
├──────────────────────────────────────────────────────────────┤
│ - modal: RagModal                                             │
│ - chunksRenderer: ChunksRenderer                              │
│ - exporter: Exporter                                          │
│ - currentExecution: Execution | null                          │
│ - executionTimer: number | null                               │
│ - executionResults: Result[] | null                           │
├──────────────────────────────────────────────────────────────┤
│ + handleExecuteTest(test): Promise<void>                      │
│ + openExecutionModal(test): void                              │
│ + updateExecutionProgress(current, total, query): void        │
│ + handleExecutionComplete(result): void                       │
│ + handleExecutionCancel(result): void                         │
│ + downloadResults(): Promise<void>                            │
└──────────────────────────────────────────────────────────────┘
```

## File Size Comparison

### Before Refactoring

| File | Lines | Size |
|------|-------|------|
| ragModal.js | 2,834 | ~107 KB |
| **Total** | **2,834** | **~107 KB** |

### After Refactoring

| Module | Files | Est. Lines | Est. Size |
|--------|-------|------------|-----------|
| Root | 2 | ~200 | ~8 KB |
| documents/ | 4 | ~500 | ~20 KB |
| settings/ | 4 | ~450 | ~18 KB |
| tests/ | 6 | ~900 | ~36 KB |
| shared/ | 5 | ~650 | ~26 KB |
| utils/ | 2 | ~100 | ~4 KB |
| **Total** | **23** | **~2,800** | **~112 KB** |

**Benefits:**
- Average file size: ~120 lines (vs 2,834)
- Maximum file size: ~250 lines
- Clear module boundaries
- Easy to navigate and maintain

## Migration Strategy

### Phase 1: Foundation
1. Create folder structure
2. Implement `utils/RagUtils.js`
3. Implement `shared/` modules

### Phase 2: Feature Modules
4. Migrate `documents/` module
5. Migrate `settings/` module
6. Migrate `tests/` module

### Phase 3: Integration
7. Create `RagModal.js` coordinator
8. Create `index.js` entry point
9. Update `main.js` import

### Phase 4: Cleanup
10. Test all functionality
11. Remove old `ragModal.js`

## API Contracts

### initializeRAGModal()

```javascript
/**
 * Initialize the RAG modal
 * Called from main.js during app initialization
 * @returns {Promise<void>}
 */
export async function initializeRAGModal() {
    const ragModal = new RagModal();
    await ragModal.initialize();
}
```

### Module Exports

```javascript
// rag/index.js
export { RAGModal } from './RagModal.js';
export { initializeRAGModal } from './RagModal.js';

// Optional: sub-module exports for external use
export * from './documents/index.js';
export * from './settings/index.js';
export * from './tests/index.js';
export * from './shared/index.js';
export * from './utils/index.js';
```

## Notes

- Each module should have its own `index.js` for clean imports
- Modules communicate through the coordinator (RagModal)
- Shared utilities are in `shared/` and `utils/`
- External API dependencies remain unchanged
- The public API (`initializeRAGModal`) stays the same for backward compatibility
