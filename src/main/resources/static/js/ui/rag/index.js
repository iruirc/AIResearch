/**
 * @fileoverview RAG Module Main Entry Point
 * Re-exports the main RagModal class and initialization function
 * @module ui/rag
 */

export { RagModal, initializeRAGModal } from './RagModal.js';

// Re-export submodules for direct access if needed
export * from './documents/index.js';
export * from './settings/index.js';
export * from './tests/index.js';
export * from './shared/index.js';
export * from './utils/index.js';
