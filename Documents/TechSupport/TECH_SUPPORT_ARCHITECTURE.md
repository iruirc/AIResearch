# Tech Support AI Assistant - Architecture

## Overview

Tech Support AI Assistant is a mini-service that provides intelligent technical support by combining:
- **RAG (Retrieval-Augmented Generation)** - for documentation and FAQ search
- **MCP (Model Context Protocol)** - for CRM/Trello integration
- **AI Classification** - for automatic query categorization

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Layer                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│   Web UI          │        REST API         │         CLI                   │
│  (JavaScript)     │    /api/v2/tech-support │    rai support                │
└─────────┬─────────┴────────────┬────────────┴──────────┬────────────────────┘
          │                      │                       │
          └──────────────────────┼───────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          TechSupportRoutes                                   │
│                     (Presentation Layer - Ktor)                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TechSupportService                                   │
│                        (Business Logic Layer)                                │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  processRequest()                                                    │    │
│  │    ├── classifyQuery()         → AI-based classification            │    │
│  │    ├── gatherContext()         → Parallel data fetching             │    │
│  │    │     ├── fetchRagContext()                                      │    │
│  │    │     └── fetchTrelloContext()                                   │    │
│  │    └── executeAIRequest()      → Generate response                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
          │                      │                       │
          ▼                      ▼                       ▼
┌─────────────────┐  ┌─────────────────────┐  ┌─────────────────────────────┐
│   RAGManager    │  │  MCPServerManager   │  │    AIProviderFactory        │
│  (Data Layer)   │  │   (Trello MCP)      │  │   (Claude/OpenAI/etc)       │
└─────────────────┘  └─────────────────────┘  └─────────────────────────────┘
          │                      │                       │
          ▼                      ▼                       ▼
┌─────────────────┐  ┌─────────────────────┐  ┌─────────────────────────────┐
│  Vector Store   │  │    Trello API       │  │      AI Provider API        │
│  (Embeddings)   │  │   (via MCP)         │  │  (Anthropic/OpenAI/etc)     │
└─────────────────┘  └─────────────────────┘  └─────────────────────────────┘
```

## Component Details

### 1. Domain Models (`TechSupportModels.kt`)

```kotlin
// Query classification types
enum class QueryType {
    BUG_REPORT,      // User reports a bug
    HOW_TO,          // User asks how to do something
    STATUS_CHECK,    // User asks about ticket/issue status
    FEATURE_REQUEST, // User requests new functionality
    GENERAL          // General questions
}

// Main request/response models
data class TechSupportRequest(
    val query: String,
    val sessionId: String? = null,
    val customerId: String? = null,
    val trelloBoardId: String? = null,
    val includeRag: Boolean = true,
    val includeTrello: Boolean = true,
    val maxRagResults: Int = 5,
    val maxTrelloResults: Int = 3,
    val providerId: ProviderType = ProviderType.CLAUDE,
    val model: String? = null
)

data class TechSupportResponse(
    val answer: String,
    val sessionId: String,
    val queryType: QueryType,
    val sourcesUsed: SourcesUsed,
    val suggestedActions: List<SuggestedActionWrapper>,
    val relatedTickets: List<TrelloTicketInfo>,
    val processingTimeMs: Long
)
```

### 2. TechSupportService

Main orchestration service that coordinates all components.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `processRequest()` | Main entry point, orchestrates the entire flow |
| `classifyQuery()` | Uses AI to classify query into QueryType |
| `gatherContext()` | Parallel fetch of RAG and Trello context |
| `fetchRagContext()` | Searches documentation via RAGManager |
| `fetchTrelloContext()` | Fetches tickets via Trello MCP |
| `executeAIRequest()` | Generates response using AI provider |
| `createTicket()` | Creates new Trello ticket via MCP |
| `checkHealth()` | Returns service health status |

**Flow Diagram:**

```
┌──────────────┐
│ User Query   │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ 1. CLASSIFY QUERY                                            │
│    └── Send to AI with classification prompt                 │
│    └── Extract QueryType from response                       │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ 2. GATHER CONTEXT (Parallel)                                 │
│    ┌─────────────────────┐    ┌─────────────────────────┐   │
│    │ fetchRagContext()   │    │ fetchTrelloContext()    │   │
│    │ - Search docs       │    │ - Search cards via MCP  │   │
│    │ - Get top K results │    │ - Get related tickets   │   │
│    └─────────────────────┘    └─────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ 3. BUILD ENRICHED PROMPT                                     │
│    └── Combine: user query + RAG context + Trello context    │
│    └── Add query type hint                                   │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ 4. EXECUTE AI REQUEST                                        │
│    └── Use tech-support-assistant system prompt              │
│    └── Send enriched prompt to AI provider                   │
│    └── Parse response for suggested actions                  │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ 5. BUILD RESPONSE                                            │
│    └── TechSupportResponse with all metadata                 │
└──────────────────────────────────────────────────────────────┘
```

### 3. RAG Integration

The service uses `RAGManager` for documentation search:

```kotlin
private suspend fun fetchRagContext(query: String, request: TechSupportRequest): RagContext {
    val searchResults = ragManager.search(
        query = query,
        topK = request.maxRagResults,
        minScore = config.ragMinScore
    )

    return RagContext(
        results = searchResults,
        formattedContext = searchResults.joinToString("\n\n") {
            "Source: ${it.documentName}\n${it.text}"
        }
    )
}
```

**RAG Content Structure:**
```
data/rag/tech-support/
├── docs/           # Technical documentation
├── faq/            # Frequently asked questions
└── known-issues/   # Known issues and workarounds
```

### 4. Trello MCP Integration

Uses MCP protocol to interact with Trello:

```kotlin
private suspend fun fetchTrelloContext(query: String, request: TechSupportRequest): TrelloContext {
    val boardId = request.trelloBoardId ?: config.defaultBoardId

    // Search cards using Trello MCP
    val searchResult = mcpServerManager.callTool(
        serverId = config.trelloMcpServerId,
        toolName = "search_cards",
        arguments = mapOf("query" to query, "board_id" to boardId)
    )

    return TrelloContext(
        tickets = parseTickets(searchResult),
        formattedContext = formatTicketsForPrompt(tickets)
    )
}
```

**Available MCP Tools:**
- `search_cards` - Search Trello cards
- `get_card` - Get card details
- `create_card` - Create new card
- `get_lists` - Get board lists

### 5. AI Classification

Query classification using AI:

```kotlin
private suspend fun classifyQuery(query: String): QueryType {
    val prompt = """
        Classify this support query into one category:
        - BUG_REPORT: User reports something broken
        - HOW_TO: User asks how to do something
        - STATUS_CHECK: User asks about status
        - FEATURE_REQUEST: User requests new feature
        - GENERAL: Other questions

        Query: "$query"

        Respond with ONLY the category name.
    """.trimIndent()

    val response = aiProvider.sendMessage(prompt)
    return QueryType.valueOf(response.trim().uppercase())
}
```

### 6. System Assistant

The `tech-support-assistant` provides specialized behavior:

```kotlin
Assistant(
    id = "tech-support-assistant",
    name = "Tech Support Assistant",
    systemPrompt = """
        You are a professional technical support assistant.

        Your responsibilities:
        1. Answer user questions using provided documentation context
        2. Reference relevant support tickets when available
        3. Suggest creating tickets for new issues
        4. Provide clear, helpful responses

        Guidelines:
        - Be professional and empathetic
        - Reference specific documentation when available
        - Suggest concrete next steps
        - Acknowledge limitations honestly
    """,
    isSystem = true
)
```

## API Endpoints

### POST /api/v2/tech-support

Process a tech support query.

**Request:**
```json
{
  "query": "Why isn't authentication working?",
  "sessionId": "optional-session-id",
  "customerId": "optional-customer-id",
  "trelloBoardId": "optional-board-override",
  "includeRag": true,
  "includeTrello": true,
  "maxRagResults": 5,
  "maxTrelloResults": 3,
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5"
}
```

**Response:**
```json
{
  "answer": "Based on the documentation...",
  "sessionId": "session-123",
  "queryType": "HOW_TO",
  "sourcesUsed": {
    "ragSourceCount": 3,
    "trelloTicketCount": 2,
    "ragSources": ["authentication.md", "faq.md"],
    "trelloSources": ["AUTH-123", "AUTH-456"]
  },
  "suggestedActions": [
    {
      "actionType": "VIEW_TICKET",
      "viewTicket": {
        "cardId": "abc123",
        "cardName": "Auth timeout issue",
        "reason": "Similar reported issue"
      }
    }
  ],
  "relatedTickets": [
    {
      "cardId": "abc123",
      "cardName": "Auth timeout issue",
      "listName": "In Progress",
      "url": "https://trello.com/c/abc123",
      "labels": ["bug", "auth"]
    }
  ],
  "processingTimeMs": 2345
}
```

### POST /api/v2/tech-support/tickets

Create a support ticket.

**Request:**
```json
{
  "title": "Authentication not working",
  "description": "User reports login failures...",
  "listName": "Inbox",
  "labels": ["bug", "auth"],
  "boardId": "optional-board-override"
}
```

### GET /api/v2/tech-support/health

Check service health.

**Response:**
```json
{
  "status": "healthy",
  "ragEnabled": true,
  "trelloConnected": true
}
```

## Dependency Injection

All components are wired through `AppModule`:

```kotlin
// Configuration
val techSupportConfig: TechSupportConfig by lazy {
    TechSupportConfig(
        ragMinScore = 0.5f,
        defaultBoardId = System.getenv("TRELLO_SUPPORT_BOARD_ID"),
        trelloMcpServerId = "trello"
    )
}

// Service
val techSupportService: TechSupportService by lazy {
    TechSupportService(
        mcpServerManager = mcpServerManager,
        ragManager = ragManager,
        aiProviderFactory = providerFactory,
        configRepository = configRepository,
        assistantManager = assistantManager,
        preferencesManager = preferencesManager,
        config = techSupportConfig
    )
}
```

## Error Handling

The service implements graceful degradation:

```kotlin
// If RAG fails, continue without documentation context
val ragContext = try {
    fetchRagContext(query, request)
} catch (e: Exception) {
    logger.warn("RAG fetch failed, continuing without documentation", e)
    RagContext.empty()
}

// If Trello fails, continue without ticket context
val trelloContext = try {
    fetchTrelloContext(query, request)
} catch (e: Exception) {
    logger.warn("Trello fetch failed, continuing without tickets", e)
    TrelloContext.empty()
}
```

## Performance Considerations

1. **Parallel Context Fetching**: RAG and Trello queries run concurrently
2. **Configurable Limits**: `maxRagResults` and `maxTrelloResults` control data volume
3. **Minimum Score Threshold**: `ragMinScore` filters low-relevance results
4. **Caching**: MCP connections are reused across requests

## Security

- API endpoints follow existing authentication patterns
- Trello access controlled via MCP server configuration
- No sensitive data stored in responses
- Session management inherited from main application
