# RAG Preview Modal Architecture

**Date:** 2025-11-27

## Overview

The RAG Preview Modal is a UI component that allows users to preview and compare RAG (Retrieval-Augmented Generation) search results with and without reranking before sending a message.

## Architecture Pattern

The module follows the **Responsibility Separation Pattern** with four distinct components:

```
┌─────────────────────────────────────────────────────────────────┐
│                      RagPreviewModal                             │
│                     (Coordinator Class)                          │
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐    │
│  │   init()    │  │   show()    │  │  setOnSendCallback() │    │
│  └─────────────┘  └─────────────┘  └──────────────────────┘    │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐    │
│  │showPreview()│  │   hide()    │  │    sendMessage()     │    │
│  └─────────────┘  └─────────────┘  └──────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
            │                │                   │
            ▼                ▼                   ▼
┌───────────────────┐ ┌──────────────┐ ┌─────────────────────────┐
│RagPreviewDataManager│ │RagPreviewRenderer│ │RagPreviewEventHandler│
└───────────────────┘ └──────────────┘ └─────────────────────────┘
```

## File Structure

```
js/ui/ragPreview/
├── index.js                    # Re-exports all components
├── RagPreviewModal.js          # Main coordinator (~100 lines)
├── RagPreviewDataManager.js    # Data loading & state (~70 lines)
├── RagPreviewRenderer.js       # UI rendering (~200 lines)
└── RagPreviewEventHandler.js   # Event handling (~100 lines)
```

## Components

### 1. RagPreviewModal (Coordinator)

**File:** `RagPreviewModal.js`

**Responsibility:** Orchestrates all components, provides public API

**Key Methods:**
- `init()` - Initialize modal and sub-components
- `show()` / `hide()` - Control modal visibility
- `showPreview()` - Load and display preview data
- `setOnSendCallback()` - Set message send callback
- `sendMessage(useReranking)` - Trigger message sending

**Dependencies:**
- RagPreviewRenderer
- RagPreviewDataManager
- RagPreviewEventHandler

### 2. RagPreviewDataManager

**File:** `RagPreviewDataManager.js`

**Responsibility:** Data loading, state management, API interaction

**Key Methods:**
- `getCurrentQuery()` / `setCurrentQuery()` - Query state
- `getPreviewData()` - Access cached preview data
- `loadPreviewData(query)` - Fetch preview from API
- `getQueryFromInput()` - Get query from DOM input
- `clearData()` - Reset state

**Dependencies:**
- ragApi

### 3. RagPreviewRenderer

**File:** `RagPreviewRenderer.js`

**Responsibility:** All UI rendering and DOM manipulation

**Key Methods:**
- `showLoading(query)` - Render loading state
- `showError(message)` - Render error state
- `renderPreview(data)` - Render complete preview
- `renderResults(results, filteredResults)` - Render search results
- `renderWithoutReranking(data)` - Render left column
- `renderWithReranking(data)` - Render right column
- `renderStatistics(data)` - Render stats section

**Utility Methods:**
- `getStrategyDisplayName(strategy)` - Convert strategy to display name
- `truncateText(text, maxLength)` - Truncate long text
- `escapeHtml(text)` - Escape HTML characters

### 4. RagPreviewEventHandler

**File:** `RagPreviewEventHandler.js`

**Responsibility:** Event listener setup and management

**Key Methods:**
- `init(modal)` - Initialize all event listeners
- `initCloseButton()` - Close button handler
- `initBackdropClick()` - Modal backdrop click handler
- `initPreviewButton()` - Preview button handler
- `initSendButtons()` - Send buttons handlers

**Callbacks:**
- `onClose` - Modal close requested
- `onPreview` - Preview requested
- `onSendWithout` - Send without reranking
- `onSendWith` - Send with reranking

## Data Flow

```
User Input          API Call           Rendering
    │                  │                  │
    ▼                  ▼                  ▼
┌─────────┐      ┌───────────┐      ┌──────────┐
│ Event   │ ───► │   Data    │ ───► │ Renderer │
│ Handler │      │  Manager  │      │          │
└─────────┘      └───────────┘      └──────────┘
    │                  │                  │
    └──────────────────┴──────────────────┘
                       │
                       ▼
               ┌──────────────┐
               │ RagPreview   │
               │    Modal     │
               │ (Coordinator)│
               └──────────────┘
```

## Sequence Diagram: Show Preview

```
User        EventHandler    Modal       DataManager    Renderer    API
 │               │            │              │            │         │
 │─click preview─►            │              │            │         │
 │               │──onPreview─►              │            │         │
 │               │            │─getQueryFromInput─►       │         │
 │               │            │◄─────query───│            │         │
 │               │            │───show()────►│            │         │
 │               │            │──showLoading(query)──────►│         │
 │               │            │─loadPreviewData(query)───►│         │
 │               │            │              │─previewContext─────►│
 │               │            │              │◄────data────│        │
 │               │            │◄────data─────│            │         │
 │               │            │───renderPreview(data)────►│         │
 │◄──────────────│────────────│──────────────│────────────│─────────│
```

## Usage

```javascript
import { ragPreviewModal } from './ui/ragPreview/index.js';

// Initialize (after DOM loaded)
ragPreviewModal.init();

// Set callback for sending messages
ragPreviewModal.setOnSendCallback((query, useReranking) => {
    console.log('Sending:', query, 'with reranking:', useReranking);
    sendMessage(query, useReranking);
});
```

## Benefits of This Architecture

1. **Single Responsibility Principle (SRP)**
   - Each component has one clear purpose
   - Easy to understand and maintain

2. **Testability**
   - Components can be tested independently
   - Mock dependencies easily

3. **Scalability**
   - Add new features without modifying existing code
   - Easy to extend renderer with new UI elements

4. **Maintainability**
   - Clear separation of concerns
   - Changes in one area don't affect others

5. **Reusability**
   - Renderer utilities can be reused
   - EventHandler pattern can be applied elsewhere

## Migration from Monolithic File

**Before:** Single file `ragPreviewModal.js` (345 lines)

**After:** Four files with clear responsibilities:
- `RagPreviewModal.js` (~100 lines)
- `RagPreviewDataManager.js` (~70 lines)
- `RagPreviewRenderer.js` (~200 lines)
- `RagPreviewEventHandler.js` (~100 lines)
- `index.js` (~10 lines)

Total: ~480 lines (includes more documentation and structure)
