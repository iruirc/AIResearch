/**
 * @fileoverview RAG Document Manager
 * Handles CRUD operations for RAG documents
 * @module ui/rag/documents/DocumentManager
 */

import { ragApi } from '../../../api/ragApi.js';
import { readFileContent } from '../utils/index.js';

/**
 * Document Manager class for managing RAG documents
 */
export class DocumentManager {
    /**
     * Create a DocumentManager
     * @param {Object} options - Configuration options
     * @param {Function} options.onDocumentsLoaded - Callback when documents are loaded
     * @param {Function} options.getActiveTab - Function to get active form tab
     * @param {Function} options.getSelectedFiles - Function to get selected files
     */
    constructor(options = {}) {
        this.documents = [];
        this.editingDocument = null;
        this.onDocumentsLoaded = options.onDocumentsLoaded || null;
        this.getActiveTab = options.getActiveTab || (() => 'text');
        this.getSelectedFiles = options.getSelectedFiles || (() => []);
    }

    /**
     * Get all documents
     * @returns {Array} Documents array
     */
    getDocuments() {
        return this.documents;
    }

    /**
     * Get editing document
     * @returns {Object|null} Editing document
     */
    getEditingDocument() {
        return this.editingDocument;
    }

    /**
     * Set editing document
     * @param {Object|null} doc - Document to edit
     */
    setEditingDocument(doc) {
        this.editingDocument = doc;
    }

    /**
     * Load all RAG documents from the API
     */
    async loadDocuments() {
        try {
            console.log('Loading RAG documents...');
            this.documents = await ragApi.loadDocuments();
            console.log(`Loaded ${this.documents.length} documents`);

            if (this.onDocumentsLoaded) {
                this.onDocumentsLoaded(this.documents);
            }

            return this.documents;
        } catch (error) {
            console.error('Error loading RAG documents:', error);
            throw error;
        }
    }

    /**
     * Load a single document by ID
     * @param {string} documentId - Document ID
     * @returns {Promise<Object>} Document object
     */
    async loadDocument(documentId) {
        try {
            const doc = await ragApi.getDocument(documentId);
            this.editingDocument = doc;
            return doc;
        } catch (error) {
            console.error('Error loading document:', error);
            throw error;
        }
    }

    /**
     * Handle adding a new document
     * @param {Object} params - Document parameters
     * @param {string} params.name - Document name
     * @param {string} params.content - Document content (for text mode)
     * @param {string} params.strategy - Chunking strategy
     * @param {boolean} params.enabled - Whether document is enabled
     */
    async handleAddDocument(params) {
        const { name, content, strategy, enabled } = params;

        if (!name) {
            throw new Error('Пожалуйста, укажите название документа');
        }

        try {
            const activeTab = this.getActiveTab();

            if (activeTab === 'text') {
                // Text mode: single document from textarea
                if (!content) {
                    throw new Error('Пожалуйста, введите содержимое документа');
                }

                console.log(`Adding text document: ${name}`);
                await ragApi.addDocument(name, content, strategy, enabled);
                console.log('Document added successfully');

            } else {
                // Files mode: each file becomes a separate document
                const selectedFiles = this.getSelectedFiles();

                if (selectedFiles.length === 0) {
                    throw new Error('Пожалуйста, выберите хотя бы один файл');
                }

                if (selectedFiles.length === 1) {
                    // Single file: use regular addDocument with originalFileName
                    const file = selectedFiles[0];
                    const fileContent = await readFileContent(file);

                    console.log(`Adding single file document: ${file.name}`);
                    await ragApi.addDocument(file.name, fileContent, strategy, enabled, file.name);
                    console.log('File document added successfully');

                } else {
                    // Multiple files: use batch endpoint, each file = separate document
                    console.log(`Adding ${selectedFiles.length} documents via batch`);

                    const documents = [];
                    for (const file of selectedFiles) {
                        const fileContent = await readFileContent(file);
                        documents.push({
                            name: file.name,
                            content: fileContent,
                            originalFileName: file.name
                        });
                    }

                    const result = await ragApi.addDocumentsBatch(documents, strategy, enabled);

                    console.log(`Created ${result.created.length} documents`);
                    if (result.errors && result.errors.length > 0) {
                        const errorMessages = result.errors.map(e => `${e.name}: ${e.error}`).join('\n');
                        console.warn('Some documents failed:', errorMessages);
                        if (result.created.length === 0) {
                            throw new Error(`Не удалось добавить документы:\n${errorMessages}`);
                        }
                    }
                }
            }

            // Reload documents
            await this.loadDocuments();

        } catch (error) {
            console.error('Error adding document:', error);
            if (error.message.startsWith('DUPLICATE_NAME:')) {
                throw new Error(`Знание с именем "${name}" уже существует.\nВыберите другое имя.`);
            }
            throw error;
        }
    }

    /**
     * Handle updating an existing document
     * @param {Object} params - Update parameters
     * @param {string} params.name - New document name
     * @param {string} params.strategy - New chunking strategy
     * @param {boolean} params.enabled - New enabled state
     */
    async handleUpdateDocument(params) {
        if (!this.editingDocument) {
            throw new Error('No document being edited');
        }

        const { name, strategy, enabled } = params;

        if (!name) {
            throw new Error('Пожалуйста, укажите название документа');
        }

        try {
            console.log(`Updating document: ${this.editingDocument.id}`);
            await ragApi.updateDocument(this.editingDocument.id, name, enabled, strategy);
            console.log('Document updated successfully');

            // Reload documents
            await this.loadDocuments();

        } catch (error) {
            console.error('Error updating document:', error);
            if (error.message.startsWith('DUPLICATE_NAME:')) {
                throw new Error(`Знание с именем "${name}" уже существует.\nВыберите другое имя.`);
            }
            throw error;
        }
    }

    /**
     * Handle deleting a document
     * @param {string} documentId - Document ID to delete
     */
    async handleDeleteDocument(documentId) {
        try {
            console.log(`Deleting document: ${documentId}`);
            await ragApi.deleteDocument(documentId);
            console.log('Document deleted successfully');

            // Reload documents
            await this.loadDocuments();

        } catch (error) {
            console.error('Error deleting document:', error);
            throw error;
        }
    }

    /**
     * Handle toggling document enabled/disabled state
     * @param {string} documentId - Document ID
     * @param {boolean} enabled - New enabled state
     */
    async handleToggleDocument(documentId, enabled) {
        try {
            console.log(`Toggling document ${documentId} to ${enabled ? 'enabled' : 'disabled'}`);
            await ragApi.updateDocument(documentId, null, enabled, null);
            console.log(`Document ${enabled ? 'enabled' : 'disabled'} successfully`);

            // Reload documents
            await this.loadDocuments();

        } catch (error) {
            console.error('Error toggling document:', error);
            throw error;
        }
    }

    // ============================================
    // Simplified methods for RagModal coordinator
    // ============================================

    /**
     * Get a document by ID
     * @param {string} docId - Document ID
     * @returns {Promise<Object>} Document object
     */
    async getDocument(docId) {
        return await this.loadDocument(docId);
    }

    /**
     * Add a new document with text content
     * @param {string} name - Document name
     * @param {string} content - Document content
     * @param {string} strategy - Chunking strategy (default: FIXED_SIZE)
     * @param {boolean} enabled - Whether document is enabled (default: true)
     */
    async addDocument(name, content, strategy = 'FIXED_SIZE', enabled = true) {
        try {
            console.log(`Adding document: ${name}`);
            await ragApi.addDocument(name, content, strategy, enabled);
            console.log('Document added successfully');
            await this.loadDocuments();
        } catch (error) {
            console.error('Error adding document:', error);
            throw error;
        }
    }

    /**
     * Add documents from files (each file becomes a separate document)
     * @param {File[]} files - Array of files
     * @param {string} name - Ignored (each file uses its own name)
     * @param {string} strategy - Chunking strategy (default: FIXED_SIZE)
     * @param {boolean} enabled - Whether documents are enabled (default: true)
     */
    async addDocumentsFromFiles(files, name = null, strategy = 'FIXED_SIZE', enabled = true) {
        if (!files || files.length === 0) {
            throw new Error('Пожалуйста, выберите хотя бы один файл');
        }

        try {
            if (files.length === 1) {
                // Single file: use regular addDocument
                const file = files[0];
                const fileContent = await readFileContent(file);

                console.log(`Adding single file document: ${file.name}`);
                await ragApi.addDocument(file.name, fileContent, strategy, enabled, file.name);
                console.log('File document added successfully');

            } else {
                // Multiple files: use batch endpoint
                console.log(`Adding ${files.length} documents via batch`);

                const documents = [];
                for (const file of files) {
                    const fileContent = await readFileContent(file);
                    documents.push({
                        name: file.name,
                        content: fileContent,
                        originalFileName: file.name
                    });
                }

                const result = await ragApi.addDocumentsBatch(documents, strategy, enabled);

                console.log(`Created ${result.created.length} documents`);
                if (result.errors && result.errors.length > 0) {
                    const errorMessages = result.errors.map(e => `${e.name}: ${e.error}`).join('\n');
                    console.warn('Some documents failed:', errorMessages);
                }
            }

            await this.loadDocuments();
        } catch (error) {
            console.error('Error adding documents from files:', error);
            throw error;
        }
    }

    /**
     * Update an existing document
     * @param {string} docId - Document ID
     * @param {string} name - New document name
     * @param {string|null} content - New content (ignored for existing documents)
     * @param {string} strategy - Chunking strategy
     * @param {boolean} enabled - Whether document is enabled
     */
    async updateDocument(docId, name, content = null, strategy = null, enabled = null) {
        try {
            console.log(`Updating document: ${docId}`);
            await ragApi.updateDocument(docId, name, enabled, strategy);
            console.log('Document updated successfully');
            await this.loadDocuments();
        } catch (error) {
            console.error('Error updating document:', error);
            throw error;
        }
    }

    /**
     * Delete a document
     * @param {string} docId - Document ID
     */
    async deleteDocument(docId) {
        return await this.handleDeleteDocument(docId);
    }
}
