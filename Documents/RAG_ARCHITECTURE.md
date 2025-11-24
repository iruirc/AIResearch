# RAG (Retrieval-Augmented Generation) Architecture

## Table of Contents
- [Overview](#overview)
- [Architecture Diagram](#architecture-diagram)
- [Core Components](#core-components)
- [Data Flow](#data-flow)
- [API Reference](#api-reference)
- [Frontend Integration](#frontend-integration)
- [Implementation Details](#implementation-details)
- [Performance Considerations](#performance-considerations)
- [Future Enhancements](#future-enhancements)

---

## Overview

The RAG (Retrieval-Augmented Generation) system enhances AI chat responses by retrieving relevant context from a knowledge base of documents. This enables the AI to provide accurate, context-aware answers based on specific domain knowledge.

### Key Features

- **Document Management**: CRUD operations for knowledge base documents
- **Vector Embeddings**: Automatic embedding generation using Ollama nomic-embed-text
- **Semantic Search**: Cosine similarity-based vector search
- **Multiple Chunking Strategies**: Fixed-size, recursive, and semantic chunking
- **Chat Integration**: Seamless integration with existing chat functionality
- **REST API**: Complete HTTP API for document and search operations
- **Web UI**: User-friendly interface for managing documents

### Technology Stack

- **Backend**: Kotlin + Ktor
- **Embeddings**: Ollama nomic-embed-text (768 dimensions)
- **Storage**: JSON-based persistence
- **Vector Search**: In-memory cosine similarity
- **Frontend**: Vanilla JavaScript + CSS

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (Web UI)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  RAG Modal   │  │  Document    │  │  Chat UI     │         │
│  │  (ragModal.js)│  │  Form        │  │  (main.js)   │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│         │                   │                   │               │
│         └───────────────────┴───────────────────┘               │
│                             │                                    │
│                      ragApi.js (API Client)                     │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTP/JSON
┌─────────────────────────────┴───────────────────────────────────┐
│                      REST API Layer (Ktor)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  RAGRoutes   │  │  ChatRoutes  │  │  Response    │         │
│  │  /rag/*      │  │  /chat       │  │  Models      │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────┴───────────────────────────────────┐
│                    Service Layer (RAGManager)                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                      RAGManager                           │  │
│  │  • addDocument()      • getDocument()                     │  │
│  │  • updateDocument()   • deleteDocument()                  │  │
│  │  • search()           • listDocuments()                   │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────┬───────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
         ▼                    ▼                    ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│   Chunking     │  │   Embedding    │  │  Vector Search │
│   Strategies   │  │    Service     │  │     Engine     │
├────────────────┤  ├────────────────┤  ├────────────────┤
│ • FIXED_SIZE   │  │ Ollama Nomic   │  │ Cosine         │
│ • RECURSIVE    │  │ Embed Text     │  │ Similarity     │
│ • SEMANTIC     │  │ (768 dims)     │  │ (In-Memory)    │
└────────────────┘  └────────────────┘  └────────────────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              │
                              ▼
                   ┌────────────────────┐
                   │  Persistence Layer │
                   │   (JSON Storage)   │
                   │  data/rag/         │
                   └────────────────────┘
```

---

## Core Components

### 1. Domain Layer

#### RAGDocument
```kotlin
@Serializable
data class RAGDocument(
    val id: String,
    val name: String,
    val originalContent: String,
    val chunks: List<TextChunk>,
    val chunkingStrategy: ChunkingStrategy,
    val enabled: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

**Purpose**: Represents a document in the knowledge base with its metadata and processed chunks.

**Key Properties**:
- `id`: Unique identifier (UUID)
- `name`: User-friendly document name
- `originalContent`: Raw text content
- `chunks`: List of text chunks with embeddings
- `chunkingStrategy`: Strategy used for splitting text
- `enabled`: Whether document is active in search
- `createdAt/updatedAt`: Timestamps for tracking

#### TextChunk
```kotlin
@Serializable
data class TextChunk(
    val text: String,
    val embedding: List<Float>,
    val chunkIndex: Int,
    val metadata: Map<String, String> = emptyMap()
)
```

**Purpose**: Represents a chunk of text with its vector embedding.

**Key Properties**:
- `text`: The actual text content
- `embedding`: 768-dimensional vector from nomic-embed-text
- `chunkIndex`: Position in original document
- `metadata`: Additional information (chunk size, strategy, etc.)

#### ChunkingStrategy
```kotlin
enum class ChunkingStrategy {
    FIXED_SIZE,    // Fixed-size chunks with overlap
    RECURSIVE,     // Hierarchical splitting
    SEMANTIC       // Sentence-based semantic boundaries
}
```

### 2. Chunking Algorithms

#### FixedSizeChunking
```kotlin
class FixedSizeChunking(
    private val chunkSize: Int = 1000,
    private val overlap: Int = 200
) : ChunkingAlgorithm {
    override fun chunk(text: String): List<String> {
        // Split text into fixed-size chunks with overlap
    }
}
```

**Strategy**: Simple sliding window approach
**Use Case**: Uniform content, technical documentation
**Parameters**:
- `chunkSize`: Target size per chunk (default: 1000 chars)
- `overlap`: Overlap between chunks (default: 200 chars)

#### RecursiveChunking
```kotlin
class RecursiveChunking(
    private val chunkSize: Int = 1000,
    private val separators: List<String> = listOf("\n\n", "\n", ". ", " ")
) : ChunkingAlgorithm {
    override fun chunk(text: String): List<String> {
        // Recursively split using separator hierarchy
    }
}
```

**Strategy**: Hierarchical splitting respecting natural boundaries
**Use Case**: Structured text, code, markdown
**Parameters**:
- `chunkSize`: Target size per chunk
- `separators`: Priority list of split points (paragraphs → sentences → words)

#### SemanticChunking
```kotlin
class SemanticChunking(
    private val maxChunkSize: Int = 1000
) : ChunkingAlgorithm {
    override fun chunk(text: String): List<String> {
        // Split by sentence boundaries
    }
}
```

**Strategy**: Split on sentence boundaries
**Use Case**: Natural language documents, articles
**Parameters**:
- `maxChunkSize`: Maximum size per chunk

### 3. Embedding Service

#### OllamaEmbeddingService
```kotlin
class OllamaEmbeddingService(
    private val config: OllamaConfig,
    private val httpClient: HttpClient
) {
    suspend fun generateEmbedding(text: String): List<Float> {
        val response = httpClient.post("${config.baseUrl}/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(EmbeddingRequest(
                model = "nomic-embed-text",
                prompt = text
            ))
        }
        return response.body<EmbeddingResponse>().embedding
    }
}
```

**Purpose**: Generate 768-dimensional embeddings using Ollama
**Model**: nomic-embed-text
**Performance**: ~100-200ms per chunk
**Output**: List of 768 floats normalized to unit vector

### 4. Vector Search Engine

#### VectorSearchEngine
```kotlin
class VectorSearchEngine {
    fun search(
        query: List<Float>,
        documents: List<RAGDocument>,
        topK: Int = 5,
        minScore: Double = 0.7
    ): List<SearchResult> {
        // Calculate cosine similarity for all chunks
        // Filter by minScore
        // Return top K results sorted by score
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
        val dotProduct = a.zip(b).sumOf { (x, y) -> x * y }
        val magnitudeA = sqrt(a.sumOf { it * it })
        val magnitudeB = sqrt(b.sumOf { it * it })
        return dotProduct / (magnitudeA * magnitudeB)
    }
}
```

**Algorithm**: Cosine similarity between query and chunk embeddings
**Complexity**: O(n*m) where n = documents, m = chunks per document
**Parameters**:
- `topK`: Number of results to return (default: 5)
- `minScore`: Minimum similarity score 0.0-1.0 (default: 0.7)

### 5. RAGManager (Service Layer)

```kotlin
class RAGManager(
    private val documentStorage: RAGDocumentStorage,
    private val embeddingService: OllamaEmbeddingService,
    private val searchEngine: VectorSearchEngine
) {
    suspend fun addDocument(
        name: String,
        content: String,
        strategy: ChunkingStrategy,
        enabled: Boolean
    ): RAGDocument {
        // 1. Chunk the text
        val chunker = getChunker(strategy)
        val textChunks = chunker.chunk(content)

        // 2. Generate embeddings for each chunk
        val chunks = textChunks.mapIndexed { index, text ->
            val embedding = embeddingService.generateEmbedding(text)
            TextChunk(text, embedding, index, metadata)
        }

        // 3. Create and persist document
        val document = RAGDocument(
            id = UUID.randomUUID().toString(),
            name = name,
            originalContent = content,
            chunks = chunks,
            chunkingStrategy = strategy,
            enabled = enabled,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        documentStorage.save(document)
        return document
    }

    suspend fun search(
        query: String,
        topK: Int = 5,
        minScore: Double = 0.7
    ): List<SearchResult> {
        // 1. Generate query embedding
        val queryEmbedding = embeddingService.generateEmbedding(query)

        // 2. Load enabled documents
        val documents = documentStorage.loadAll()
            .filter { it.enabled }

        // 3. Perform vector search
        return searchEngine.search(queryEmbedding, documents, topK, minScore)
    }
}
```

### 6. Persistence Layer

#### RAGDocumentStorage
```kotlin
class JsonRAGDocumentStorage(
    private val storageDir: File
) : RAGDocumentStorage {
    private val json = Json { prettyPrint = true }

    override suspend fun save(document: RAGDocument) {
        val file = File(storageDir, "documents/${document.id}.json")
        file.parentFile.mkdirs()
        file.writeText(json.encodeToString(document))
    }

    override suspend fun load(id: String): RAGDocument? {
        val file = File(storageDir, "documents/$id.json")
        if (!file.exists()) return null
        return json.decodeFromString(file.readText())
    }

    override suspend fun loadAll(): List<RAGDocument> {
        val docsDir = File(storageDir, "documents")
        if (!docsDir.exists()) return emptyList()

        return docsDir.listFiles { _, name -> name.endsWith(".json") }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString(file.readText())
                } catch (e: Exception) {
                    logger.error("Failed to load document: ${file.name}", e)
                    null
                }
            } ?: emptyList()
    }
}
```

**Storage Format**: Individual JSON files per document
**Location**: `data/rag/documents/{id}.json`
**Benefits**: Human-readable, easy debugging, no database required
**Limitations**: Not suitable for millions of documents

---

## Data Flow

### Document Creation Flow

```
1. User submits document via UI
   ↓
2. Frontend sends POST /rag/documents
   {
     "name": "Python Guide",
     "content": "Python is...",
     "chunkingStrategy": "FIXED_SIZE",
     "enabled": true
   }
   ↓
3. RAGRoutes receives request
   ↓
4. RAGManager.addDocument() called
   ↓
5. Text chunked based on strategy
   ["Python is a language...", "It supports..."]
   ↓
6. For each chunk:
   - Generate embedding via Ollama API
   - Create TextChunk object
   ↓
7. Create RAGDocument with all chunks
   ↓
8. Persist to JSON file
   data/rag/documents/{uuid}.json
   ↓
9. Return document with ID to frontend
   ↓
10. Frontend updates UI with new document
```

### Search Flow

```
1. User sends chat message: "What are Python frameworks?"
   ↓
2. ChatRoutes receives message
   ↓
3. RAGManager.search() called with message text
   ↓
4. Generate embedding for query text
   [0.123, 0.456, ..., 0.789] (768 dimensions)
   ↓
5. Load all enabled documents
   ↓
6. For each chunk in each document:
   - Calculate cosine similarity
   - similarity = dot(query, chunk) / (||query|| * ||chunk||)
   ↓
7. Filter chunks with score >= minScore (0.7)
   ↓
8. Sort by score descending
   ↓
9. Return top K results (default: 5)
   [
     {text: "Django and Flask...", score: 0.85},
     {text: "Python web dev...", score: 0.78}
   ]
   ↓
10. Inject context into system prompt
    "Based on this context: Django and Flask..."
   ↓
11. Send enhanced prompt to AI provider
   ↓
12. Return AI response with RAG context
```

---

## API Reference

### Document Endpoints

#### List Documents
```http
GET /rag/documents
```

**Response:**
```json
[
  {
    "id": "uuid",
    "name": "Python Guide",
    "chunkCount": 5,
    "chunkingStrategy": "FIXED_SIZE",
    "enabled": true,
    "createdAt": "2025-11-25T10:00:00Z",
    "updatedAt": "2025-11-25T10:00:00Z"
  }
]
```

#### Get Document
```http
GET /rag/documents/{id}
```

**Response:**
```json
{
  "id": "uuid",
  "name": "Python Guide",
  "originalContent": "Python is...",
  "chunks": [
    {
      "text": "Python is a programming language...",
      "embedding": [0.123, 0.456, ...],
      "chunkIndex": 0,
      "metadata": {
        "chunkSize": "150",
        "strategy": "FIXED_SIZE"
      }
    }
  ],
  "chunkingStrategy": "FIXED_SIZE",
  "enabled": true,
  "createdAt": "2025-11-25T10:00:00Z",
  "updatedAt": "2025-11-25T10:00:00Z"
}
```

#### Create Document
```http
POST /rag/documents
Content-Type: application/json

{
  "name": "Python Guide",
  "content": "Python is a high-level programming language...",
  "chunkingStrategy": "FIXED_SIZE",
  "enabled": true
}
```

**Response:** Full document object with generated ID and embeddings

#### Update Document
```http
PUT /rag/documents/{id}
Content-Type: application/json

{
  "name": "Updated Name",
  "enabled": false,
  "chunkingStrategy": "RECURSIVE"
}
```

**Note:** Cannot update `content` - requires delete and recreate

#### Delete Document
```http
DELETE /rag/documents/{id}
```

**Response:** 204 No Content

### Search Endpoint

#### Search Documents
```http
POST /rag/search
Content-Type: application/json

{
  "query": "Python web frameworks",
  "topK": 5,
  "minScore": 0.7
}
```

**Response:**
```json
[
  {
    "documentId": "uuid",
    "documentName": "Python Guide",
    "chunkIndex": 2,
    "text": "Popular frameworks include Django and Flask...",
    "score": 0.85
  }
]
```

---

## Frontend Integration

### File Structure

```
static/
├── js/
│   ├── api/
│   │   └── ragApi.js          # API client (191 lines)
│   └── ui/
│       └── ragModal.js        # Modal UI controller (432 lines)
├── styles/
│   └── features/
│       └── rag.css            # RAG styles (321 lines)
└── index.html                 # RAG modals markup
```

### API Client (ragApi.js)

```javascript
export const ragApi = {
    async loadDocuments() {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/documents`,
            { method: 'GET' },
            API_CONFIG.REQUEST_TIMEOUT
        );
        return await response.json();
    },

    async addDocument(name, content, chunkingStrategy, enabled) {
        const response = await fetchWithTimeout(
            `${API_CONFIG.RAG}/documents`,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, content, chunkingStrategy, enabled })
            },
            API_CONFIG.REQUEST_TIMEOUT
        );
        return await response.json();
    },

    // ... other methods
};
```

### Modal Controller (ragModal.js)

```javascript
export class RAGModal {
    constructor() {
        this.documents = [];
        this.currentMode = 'list'; // 'list', 'add', 'edit'
        this.editingDocument = null;
    }

    async initialize() {
        // Set up event listeners
        const ragButton = document.getElementById('ragButton');
        ragButton.addEventListener('click', async () => {
            await this.open();
        });
    }

    async loadDocuments() {
        this.documents = await ragApi.loadDocuments();
        this.renderDocumentsList();
    }

    renderDocumentsList() {
        // Render document list with action buttons
        // Add, Edit, Delete, Toggle enabled/disabled
    }
}
```

### UI Components

**RAG Button (Sidebar):**
```html
<button id="ragButton" class="rag-button-sidebar">
    <svg><!-- Book icon --></svg>
    Знания RAG
</button>
```

**Document List Modal:**
```html
<div id="ragModal" class="modal">
    <div class="modal-content">
        <div class="modal-header">
            <h3>Управление знаниями RAG</h3>
            <button id="closeRagModal">&times;</button>
        </div>
        <div id="ragDocumentsList" class="rag-documents-list">
            <!-- Documents rendered here -->
        </div>
    </div>
</div>
```

**Document Form Modal:**
```html
<div id="ragFormModal" class="modal">
    <div class="modal-content">
        <form id="ragDocumentForm" class="rag-form">
            <input type="text" id="ragDocumentName" required>
            <textarea id="ragDocumentContent" required></textarea>
            <select id="ragChunkingStrategy">
                <option value="FIXED_SIZE">Фиксированный размер</option>
                <option value="RECURSIVE">Рекурсивное разбиение</option>
                <option value="SEMANTIC">Семантическое разбиение</option>
            </select>
            <input type="checkbox" id="ragDocumentEnabled" checked>
        </form>
    </div>
</div>
```

---

## Implementation Details

### Initialization in AppModule

```kotlin
// In AppModule.kt
val ragManager = RAGManager(
    documentStorage = JsonRAGDocumentStorage(File("data/rag")),
    embeddingService = OllamaEmbeddingService(ollamaConfig, httpClient),
    searchEngine = VectorSearchEngine()
)

// Initialize on startup
ragManager.initialize()
```

### Chat Integration

```kotlin
// In ChatRoutes.kt
post("/chat") {
    val request = call.receive<ChatRequest>()

    // Retrieve RAG context
    val ragResults = ragManager.search(
        query = request.message,
        topK = 3,
        minScore = 0.7
    )

    // Build system prompt with context
    val systemPrompt = if (ragResults.isNotEmpty()) {
        val context = ragResults.joinToString("\n\n") { result ->
            "From ${result.documentName}:\n${result.text}"
        }
        """
        You are a helpful assistant. Use the following context to answer questions:

        $context

        If the context is relevant, use it in your response.
        If not, answer based on your general knowledge.
        """
    } else {
        "You are a helpful assistant."
    }

    // Send to AI provider
    val response = aiProvider.sendMessage(request.message, systemPrompt)

    call.respond(ChatResponse(response))
}
```

### Embedding Generation

```kotlin
// Ollama API request
POST http://localhost:11434/api/embeddings
{
  "model": "nomic-embed-text",
  "prompt": "Python is a programming language"
}

// Response
{
  "embedding": [0.123, 0.456, ..., 0.789]  // 768 floats
}
```

### Vector Similarity Calculation

```kotlin
fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
    require(a.size == b.size) { "Vectors must have same dimension" }

    // Dot product: a · b
    val dotProduct = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }

    // Magnitudes: ||a|| and ||b||
    val magnitudeA = sqrt(a.sumOf { (it * it).toDouble() })
    val magnitudeB = sqrt(b.sumOf { (it * it).toDouble() })

    // Cosine similarity: (a · b) / (||a|| * ||b||)
    return dotProduct / (magnitudeA * magnitudeB)
}
```

**Score Interpretation:**
- `1.0`: Identical vectors (perfect match)
- `0.7-1.0`: Highly similar (typical threshold for RAG)
- `0.5-0.7`: Somewhat similar
- `0.0-0.5`: Different topics
- `-1.0`: Opposite vectors (rare in practice)

---

## Performance Considerations

### Embedding Generation
- **Time per chunk:** ~100-200ms (Ollama local inference)
- **Bottleneck:** Sequential processing of chunks
- **Optimization:** Parallel embedding generation (future)

### Vector Search
- **Algorithm:** Brute force cosine similarity
- **Complexity:** O(n × m) where n=documents, m=chunks per document
- **Performance:**
  - 10 documents: < 50ms
  - 100 documents: < 200ms
  - 1000 documents: < 2s

### Memory Usage
- **Per document:** ~100KB (including embeddings)
- **Per chunk:** ~3KB (768 floats × 4 bytes + text)
- **1000 documents:** ~100MB RAM

### Storage
- **Format:** JSON (human-readable)
- **Size:** ~150KB per document (with embeddings)
- **1000 documents:** ~150MB disk space

### Optimization Strategies

1. **Caching:**
   - Cache query embeddings for repeated queries
   - Cache document embeddings in memory

2. **Indexing:**
   - Use HNSW (Hierarchical Navigable Small World) for approximate search
   - Reduce search complexity from O(n) to O(log n)

3. **Batch Processing:**
   - Generate embeddings in parallel
   - Use Ollama batch API (if available)

4. **Quantization:**
   - Reduce embedding precision (float32 → int8)
   - 75% memory reduction with minimal quality loss

---

## Future Enhancements

### High Priority

1. **Vector Database Integration**
   - Replace in-memory search with Qdrant or Weaviate
   - Enable ANN (Approximate Nearest Neighbor) search
   - Support for millions of documents

2. **Hybrid Search**
   - Combine vector search with keyword search (BM25)
   - Better results for exact keyword matches

3. **Metadata Filtering**
   - Filter by document type, date, category, etc.
   - Pre-filter before vector search

4. **Reranking**
   - Use cross-encoder model to rerank results
   - Improve top-K result quality

### Medium Priority

5. **Advanced Chunking**
   - LangChain integration for better text splitting
   - Context-aware chunking (preserve paragraphs, sections)

6. **Multi-language Support**
   - Use multilingual embedding models
   - Language detection and routing

7. **Document Preprocessing**
   - Extract text from PDF, DOCX, HTML
   - OCR for images
   - Table extraction

8. **Analytics Dashboard**
   - Track search queries
   - Measure result relevance
   - Identify knowledge gaps

### Low Priority

9. **Incremental Updates**
   - Update document chunks without full reprocessing
   - Diff-based chunk updates

10. **Version Control**
    - Track document versions
    - Rollback to previous versions

11. **Access Control**
    - Document-level permissions
    - User-specific knowledge bases

12. **Export/Import**
    - Backup knowledge base
    - Share knowledge bases between instances

---

## Troubleshooting

### Common Issues

**Issue: Ollama connection fails**
```
Error: Connection refused to http://localhost:11434
```
**Solution:** Ensure Ollama is running: `ollama serve`

**Issue: Embeddings are all zeros**
```
Error: Generated embedding is invalid (all zeros)
```
**Solution:** Check Ollama model is downloaded: `ollama pull nomic-embed-text`

**Issue: Search returns no results**
```
Warning: No results found for query
```
**Solution:**
- Lower `minScore` threshold (try 0.5)
- Check documents are `enabled: true`
- Verify document content is relevant to query

**Issue: Out of memory**
```
Error: OutOfMemoryError when loading documents
```
**Solution:**
- Increase JVM heap: `-Xmx4g`
- Reduce chunk size to decrease embedding count
- Implement pagination for document loading

---

## References

### Research Papers
- **RAG Paper:** "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks" (Lewis et al., 2020)
- **Dense Retrieval:** "Dense Passage Retrieval for Open-Domain Question Answering" (Karpukhin et al., 2020)
- **Vector Search:** "Billion-scale similarity search with GPUs" (Johnson et al., 2017)

### Tools & Libraries
- **Ollama:** https://ollama.ai/
- **nomic-embed-text:** https://ollama.ai/library/nomic-embed-text
- **Ktor:** https://ktor.io/
- **kotlinx.serialization:** https://github.com/Kotlin/kotlinx.serialization

### Related Documentation
- [MULTI_PROVIDER_ARCHITECTURE.md](MULTI_PROVIDER_ARCHITECTURE.md) - AI provider integration
- [POSTGRESQL_ARCHITECTURE.md](POSTGRESQL_ARCHITECTURE.md) - Database alternatives
- [FRONTEND.md](FRONTEND.md) - Frontend architecture overview

---

**Last Updated:** 2025-11-25
**Version:** 1.0
**Status:** Production Ready
