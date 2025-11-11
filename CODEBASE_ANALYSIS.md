# ResearchAI Codebase Architecture Analysis

## Project Overview

ResearchAI is a Ktor-based backend application that provides a REST API for interacting with the Claude API from Anthropic. It manages chat sessions, agents, and handles request/response formatting for multiple output formats (Plain Text, JSON, XML).

**Technology Stack:**
- Framework: Ktor Server (Kotlin)
- Target JVM: Java 17+
- HTTP Client: Ktor Client (CIO engine)
- Serialization: Kotlinx Serialization (JSON)
- Logging: Logback
- CORS: Enabled for cross-origin requests

---

## 1. Architecture Overview

### Layered Architecture

```
┌─────────────────────────────────────────────────┐
│         Web Layer (HTTP Routes)                  │
│  ┌──────────────────────────────────────────┐   │
│  │ ChatRoutes (com.example.routes)          │   │
│  │ - /chat (POST)                           │   │
│  │ - /sessions (GET, POST, DELETE)          │   │
│  │ - /agents (GET)                          │   │
│  │ - /models (GET)                          │   │
│  │ - /config (GET)                          │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│      Service Layer (Business Logic)              │
│  ┌────────────────────────────────────────────┐ │
│  │ ClaudeService                              │ │
│  │ - Handles Claude API communication         │ │
│  │ - Manages HTTP requests/responses          │ │
│  │ - Integrates message formatting           │ │
│  └────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────┐ │
│  │ ChatSessionManager                         │ │
│  │ - In-memory session storage                │ │
│  │ - Message history management              │ │
│  │ - Session lifecycle                       │ │
│  └────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────┐ │
│  │ AgentManager                               │ │
│  │ - Agent registration and retrieval         │ │
│  │ - Agent configuration                     │ │
│  └────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────┐ │
│  │ ClaudeMessageFormatter                     │ │
│  │ - Format enhancement (pre-processing)      │ │
│  │ - Response formatting (post-processing)    │ │
│  └────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│      Data Layer (Models & Configuration)         │
│  ┌────────────────────────────────────────────┐ │
│  │ Request/Response Models (ClaudeModels.kt)  │ │
│  │ - ClaudeRequest / ClaudeResponse           │ │
│  │ - ClaudeMessage / MessageRole              │ │
│  │ - ClaudeContent / ClaudeUsage              │ │
│  └────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────┐ │
│  │ Session & Agent Models                     │ │
│  │ - ChatSession / ChatRequest / ChatResponse │ │
│  │ - Agent / AgentManager                     │ │
│  └────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────┐ │
│  │ Configuration (ClaudeConfig)               │ │
│  │ - API credentials and settings             │ │
│  └────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
                        ↓
           Claude API (External Service)
```

---

## 2. Request/Response Flow

### Message Lifecycle: User Request → Claude Response

```
1. User sends ChatRequest
   ├── message: String
   ├── format: ResponseFormat (PLAIN_TEXT, JSON, XML)
   ├── sessionId: String? (optional)
   ├── model: String? (optional, overrides default)
   ├── temperature: Double? (optional, overrides default)
   └── maxTokens: Int? (optional, overrides default)

2. ChatRoutes handles POST /chat
   ├── Validates request (message not empty)
   ├── Gets or creates ChatSession
   ├── Retrieves system prompt if agent-based session
   └── Calls ClaudeService.sendMessage()

3. ClaudeService.sendMessage()
   ├── Enhances message via ClaudeMessageFormatter
   ├── Builds message history with new user message
   ├── Selects model/temperature/maxTokens (request params or defaults)
   ├── Creates ClaudeRequest object
   ├── Makes HTTP POST to Claude API
   └── Processes response via ClaudeMessageFormatter

4. ClaudeResponse Processing
   ├── Validates HTTP status
   ├── Extracts text from response content
   ├── Formats response based on ResponseFormat
   └── Returns formatted text

5. Session Update
   ├── Adds user message to ChatSession.messages
   ├── Adds assistant response to ChatSession.messages
   └── Updates session.lastAccessedAt timestamp

6. Return ChatResponse to client
   ├── response: String (formatted answer)
   └── sessionId: String (for conversation continuity)
```

---

## 3. Key Components

### 3.1 Configuration Management

**File:** `/src/main/kotlin/com/example/config/ClaudeConfig.kt`

```kotlin
data class ClaudeConfig(
    val apiKey: String,
    val apiUrl: String = "https://api.anthropic.com/v1/messages",
    val model: String = "claude-haiku-4-5-20251001",
    val maxTokens: Int = 8192,
    val temperature: Double = 1.0,
    val apiVersion: String = "2023-06-01"
)
```

**Configuration Sources (in order of precedence):**
1. Environment variables: `CLAUDE_API_KEY`, `CLAUDE_MODEL`, `CLAUDE_MAX_TOKENS`, `CLAUDE_TEMPERATURE`
2. Java system properties (from .env file)
3. Default values in ClaudeConfig data class

**Available Models:**
- Claude Haiku 4.5 (claude-haiku-4-5-20251001) - Default
- Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)
- Claude Opus 4.1 (claude-opus-4-1-20250805)
- Claude Opus 4 (claude-opus-4-20250514)
- Claude Sonnet 4 (claude-sonnet-4-20250514)
- Claude Sonnet 3.7 (claude-3-7-sonnet-20250219)
- Claude Haiku 3.5 (claude-3-5-haiku-20241022)
- Claude Haiku 3 (claude-3-haiku-20240307)

### 3.2 HTTP Client & Network Layer

**File:** `/src/main/kotlin/com/example/services/ClaudeService.kt`

**Ktor HTTP Client Configuration:**
```
Engine: CIO (Coroutine I/O)
Plugins:
  ├─ HttpTimeout
  │  ├─ requestTimeoutMillis: 300,000 (5 minutes)
  │  ├─ connectTimeoutMillis: 10,000
  │  └─ socketTimeoutMillis: 300,000
  ├─ ContentNegotiation (JSON serialization)
  └─ Logging (INFO level)
```

**API Communication:**
- Endpoint: `https://api.anthropic.com/v1/messages`
- Method: `POST`
- Headers:
  - `x-api-key`: API key from configuration
  - `anthropic-version`: "2023-06-01"
  - `Content-Type`: "application/json"

**Request Serialization:** Kotlinx JSON with `ignoreUnknownKeys = true`

**Error Handling:**
- Checks HTTP status code
- Parses error responses as `ClaudeError` objects
- Returns user-friendly error messages
- Falls back to raw body on parse failure

### 3.3 Request/Response Models

**File:** `/src/main/kotlin/com/example/models/ClaudeModels.kt`

#### Claude API Request Structure
```kotlin
@Serializable
data class ClaudeRequest(
    val model: String,                    // Model ID
    @SerialName("max_tokens")
    val maxTokens: Int,                  // Max output tokens
    val messages: List<ClaudeMessage>,   // Conversation history
    val temperature: Double = 1.0,       // Sampling temperature
    val system: String? = null           // System prompt (agent behavior)
)

@Serializable
data class ClaudeMessage(
    val role: MessageRole,               // USER or ASSISTANT
    val content: String                  // Message text
)

@Serializable
enum class MessageRole {
    @SerialName("user")
    USER,
    
    @SerialName("assistant")
    ASSISTANT
}
```

#### Claude API Response Structure
```kotlin
@Serializable
data class ClaudeResponse(
    val id: String,                      // Response ID
    val type: String,                    // Type (usually "message")
    val role: MessageRole,               // Role (usually ASSISTANT)
    val content: List<ClaudeContent>,   // Response content
    val model: String,                   // Model used
    @SerialName("stop_reason")
    val stopReason: String? = null,     // Why response stopped
    val usage: ClaudeUsage               // Token usage
)

@Serializable
data class ClaudeContent(
    val type: String,                    // Type (usually "text")
    val text: String                     // Response text
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,               // Input tokens used
    @SerialName("output_tokens")
    val outputTokens: Int               // Output tokens used
)
```

#### Error Response Structure
```kotlin
@Serializable
data class ClaudeError(
    val type: String,
    val error: ClaudeErrorDetails
)

@Serializable
data class ClaudeErrorDetails(
    val type: String,
    val message: String
)
```

### 3.4 Session Management

**File:** `/src/main/kotlin/com/example/services/ChatSessionManager.kt`

**Session Storage:** In-memory `ConcurrentHashMap<String, ChatSession>`

**Key Methods:**
- `createSession(agentId: String?)`: String - Creates new session
- `getSession(sessionId: String)`: ChatSession? - Retrieves existing session
- `getOrCreateSession(sessionId: String?)`: Pair<String, ChatSession> - Gets or creates
- `addMessageToSession(sessionId, role, content)`: Boolean - Adds message to history
- `clearSession(sessionId)`: Boolean - Clears message history
- `deleteSession(sessionId)`: Boolean - Removes entire session
- `getSessionsInfo()`: Map<String, SessionInfo> - Gets metadata for all sessions

**Session Lifecycle:**
```
1. Session created when user sends first message (or explicitly)
2. Session ID returned to client for future requests
3. Message history accumulated in session
4. lastAccessedAt timestamp updated on each access
5. Session persists in memory until server restart
```

**Session Data Model:**
```kotlin
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    private val _messages: MutableList<ClaudeMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastAccessedAt: Long = System.currentTimeMillis(),
    val agentId: String? = null
) {
    val messages: List<ClaudeMessage> // Read-only copy
    fun addMessage(role: MessageRole, content: String)
    fun clear()
}
```

### 3.5 Message Formatting

**File:** `/src/main/kotlin/com/example/services/ClaudeMessageFormatter.kt`

**Supported Formats:**
1. **PLAIN_TEXT** - Raw text response (default)
2. **JSON** - Structured JSON response
3. **XML** - Structured XML response

**Processing Flow:**
```
Pre-processing (enhanceMessage):
  ├─ PLAIN_TEXT: No enhancement
  ├─ JSON: Adds structured format instructions + template
  └─ XML: Adds XML format instructions + template

User Message:
  └─ Enhanced message sent to Claude API

Post-processing (processResponseByFormat):
  ├─ PLAIN_TEXT: Returns as-is
  ├─ JSON: Cleans markdown blocks, validates, pretty-prints
  └─ XML: Cleans markdown blocks, validates, pretty-prints
```

**Format Templates:**

JSON Template:
```json
{
  "title": "краткое описание запроса",
  "source_request": "исходный запрос",
  "answer": "ответ за запрос"
}
```

XML Template:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<response>
  <title>краткое описание запроса</title>
  <source_request>исходный запрос</source_request>
  <answer>ответ за запрос</answer>
</response>
```

**Enhancement Strategy:**
- JSON: Strict instruction to return ONLY raw JSON without markdown blocks
- XML: Strict instruction to return ONLY valid XML with proper declaration
- Includes examples of correct format and common mistakes to avoid

### 3.6 Agent Management

**File:** `/src/main/kotlin/com/example/services/AgentManager.kt`

**Available Agents:**
1. **Greeting Assistant** (greeting-assistant)
   - Purpose: Welcome and conversation initiation
   - System Prompt: "Ты - ассистент приветствия..."

2. **AI Tutor** (ai-tutor)
   - Purpose: Personalized educational assistance
   - System Prompt: Complex prompt with multi-stage interaction:
     - Dialogue initiation
     - Information gathering (progressive, not all at once)
     - Draft proposal with learning plan
     - Finalization with resources and next steps
     - Encouraging, motivating tone

**Agent Features:**
- `registerAgent(agent: Agent)`: Register new agent
- `getAgent(agentId: String)`: Agent? - Get agent by ID
- `getAllAgents()`: List<Agent> - List all available agents
- `hasAgent(agentId: String)`: Boolean - Check if agent exists

**Session-Agent Connection:**
- Session can be created with optional `agentId`
- When chat message processed in agent session, system prompt applied
- System prompt determines agent behavior/personality

### 3.7 REST API Routes

**File:** `/src/main/kotlin/com/example/routes/ChatRoutes.kt`

#### Chat Endpoints

**POST /chat** - Send message and get response
```
Request:
  {
    "message": "string",
    "format": "PLAIN_TEXT|JSON|XML",
    "sessionId": "string (optional)",
    "model": "string (optional)",
    "temperature": number (optional),
    "maxTokens": number (optional)
  }

Response:
  {
    "response": "string",
    "sessionId": "string"
  }

Error Response:
  {
    "error": "string"
  }
```

#### Session Endpoints

**GET /sessions** - List all sessions
```
Response:
  {
    "sessions": [
      {
        "id": "string",
        "messageCount": number,
        "createdAt": number (timestamp),
        "lastAccessedAt": number (timestamp),
        "agentId": "string (optional)"
      }
    ]
  }
```

**GET /sessions/{sessionId}** - Get session details with message history
```
Response:
  {
    "id": "string",
    "messages": [
      {
        "role": "user|assistant",
        "content": "string"
      }
    ],
    "createdAt": number,
    "lastAccessedAt": number
  }
```

**DELETE /sessions/{sessionId}** - Delete session
```
Response:
  {
    "success": boolean,
    "message": "string (optional)"
  }
```

**POST /sessions/{sessionId}/clear** - Clear message history
```
Response:
  {
    "success": boolean,
    "message": "string (optional)"
  }
```

#### Agent Endpoints

**GET /agents** - List available agents
```
Response:
  {
    "agents": [
      {
        "id": "string",
        "name": "string",
        "description": "string"
      }
    ]
  }
```

**POST /agents/start** - Start new session with agent
```
Request:
  {
    "agentId": "string"
  }

Response:
  {
    "sessionId": "string",
    "agentName": "string",
    "initialMessage": "string"
  }
```

#### Model Endpoints

**GET /models** - List available models
```
Response:
  {
    "models": [
      {
        "id": "string",
        "displayName": "string",
        "createdAt": "string (ISO-8601)"
      }
    ]
  }
```

#### Configuration Endpoints

**GET /config** - Get current configuration
```
Response:
  {
    "model": "string",
    "temperature": number,
    "maxTokens": number,
    "format": "string"
  }
```

**GET /health** - Health check
```
Response:
  {
    "status": "ok"
  }
```

---

## 4. Application Initialization

**File:** `/src/main/kotlin/Application.kt`

```
1. main() entry point
   └─ DotenvLoader.load() - Load .env variables
   └─ EngineMain.main(args) - Start Ktor server

2. module() function (Ktor Application)
   ├─ getClaudeConfig() - Load configuration from environment
   ├─ ClaudeService(claudeConfig) - Create HTTP client
   ├─ Setup plugins:
   │  ├─ CallLogging (INFO level)
   │  ├─ ContentNegotiation (JSON)
   │  └─ CORS (anyHost)
   └─ configureRouting() - Set up routes

3. configureRouting() function
   ├─ ChatSessionManager() - Create session manager
   ├─ AgentManager() - Create and register agents
   └─ chatRoutes() - Set up all API endpoints
```

---

## 5. Configuration Loading

**File:** `/src/main/kotlin/com/example/config/DotenvLoader.kt`

**Purpose:** Load environment variables from `.env` file at startup

**Process:**
1. Check if `.env` file exists
2. Parse each line (skip empty lines and comments starting with #)
3. Extract key=value pairs
4. Remove surrounding quotes if present
5. Set environment variables only if not already set
6. Use Java reflection to modify System environment (or fallback to System.setProperty)

**Precedence:**
1. System environment variables (highest priority)
2. `.env` file variables
3. Default configuration values (lowest priority)

---

## 6. Data Flow Examples

### Example 1: Simple Text Chat

```
Client:
POST /chat
{
  "message": "What is AI?",
  "format": "PLAIN_TEXT"
}

Server:
1. ChatRoutes receives request
2. Creates new session (no sessionId provided)
3. Calls ClaudeService.sendMessage()
4. ClaudeService:
   - Creates ClaudeRequest with user message
   - POST to https://api.anthropic.com/v1/messages
   - Receives ClaudeResponse
   - Extracts text: "AI is artificial intelligence..."
5. Saves user message and response to session
6. Returns ChatResponse with sessionId

Client receives:
{
  "response": "AI is artificial intelligence...",
  "sessionId": "uuid-string"
}
```

### Example 2: Multi-turn JSON Conversation

```
Turn 1:
Request:
{
  "message": "Explain async/await",
  "format": "JSON",
  "sessionId": "session-123"
}

ClaudeService:
- Enhances message with JSON instructions
- Adds existing messages from session to history
- Sends to Claude with system prompt (if agent-based)
- Processes response, validates JSON, formats

Response:
{
  "response": "{\"title\": \"...\", \"answer\": \"...\"}",
  "sessionId": "session-123"
}

Turn 2:
Request:
{
  "message": "With examples",
  "format": "JSON",
  "sessionId": "session-123"
}

ClaudeService:
- History now includes Turn 1 messages
- Provides context for Turn 2 response
- Response considers previous conversation
```

### Example 3: Agent-Based Session

```
Start Agent Session:
POST /agents/start
{
  "agentId": "ai-tutor"
}

Server:
1. Get agent with system prompt
2. Create session with agentId reference
3. Call ClaudeService with agent's system prompt
4. Claude responds with greeting
5. Save both messages to session

Response:
{
  "sessionId": "session-456",
  "agentName": "AI Репетитор",
  "initialMessage": "Привет! Я — AI-Тьютор..."
}

Subsequent messages in this session:
- System prompt always applied
- Message history provides context
- Agent behavior determined by its system prompt
```

---

## 7. Memory & Performance Considerations

### Memory Management
- **Session Storage:** In-memory ConcurrentHashMap
- **Limitations:**
  - Sessions persist only during server runtime
  - No persistence to database
  - Memory usage grows with number of active sessions
  - No automatic cleanup of old sessions

### Request Timeout Settings
- Connection timeout: 10 seconds
- Request/read timeout: 5 minutes (300 seconds)
- Suitable for longer Claude API responses

### Concurrency
- `ChatSessionManager` uses `ConcurrentHashMap` for thread-safety
- HTTP client handles concurrent requests
- Ktor server handles multiple simultaneous requests

---

## 8. Integration Points

### External Dependencies
- **Claude API** (https://api.anthropic.com)
  - Requires valid API key
  - Uses `/v1/messages` endpoint
  - Supports streaming (not currently implemented)

### Frontend Integration
- Static assets served from `/static` directory
- CORS enabled for cross-origin requests
- JSON request/response format

---

## 9. Key Files Summary

| File | Purpose | Key Classes |
|------|---------|------------|
| `ClaudeConfig.kt` | Configuration management | `ClaudeConfig`, `getClaudeConfig()` |
| `DotenvLoader.kt` | Environment loading | `DotenvLoader` |
| `ClaudeService.kt` | API communication | `ClaudeService` |
| `ChatSessionManager.kt` | Session lifecycle | `ChatSessionManager`, `SessionInfo` |
| `AgentManager.kt` | Agent management | `AgentManager`, `Agent` |
| `ClaudeMessageFormatter.kt` | Format handling | `ClaudeMessageFormatter` |
| `ClaudeModels.kt` | API data models | `ClaudeRequest`, `ClaudeResponse`, `MessageRole` |
| `ChatRequest.kt` | Client request | `ChatRequest`, `ConfigResponse` |
| `ChatResponse.kt` | Client response | `ChatResponse` |
| `ChatSession.kt` | Session model | `ChatSession`, `ClaudeMessage` |
| `ResponseFormat.kt` | Format enum | `ResponseFormat` |
| `SessionResponses.kt` | Session API models | `SessionListItem`, `SessionDetailResponse`, etc. |
| `ChatRoutes.kt` | HTTP routes | Route handlers for /chat, /sessions, /agents, etc. |
| `Application.kt` | App entry point | `main()`, `module()` |
| `Routing.kt` | Route configuration | `configureRouting()` |

---

## 10. Security Considerations

1. **API Key Protection**
   - Stored in environment variables (not in code)
   - Passed via header to Claude API
   - Never logged in full

2. **CORS**
   - Currently allows `anyHost()` (permissive)
   - Should be restricted in production

3. **Input Validation**
   - Message content length checked (non-empty)
   - Session ID format not explicitly validated
   - Model IDs validated against whitelist

4. **Session Isolation**
   - Sessions are identified by UUID
   - No authentication/authorization implemented
   - Any client can access any session if they know the ID

---

## 11. Limitations & Future Improvements

### Current Limitations
1. Sessions are in-memory only (lost on restart)
2. No database persistence
3. No authentication/authorization
4. No rate limiting
5. No request logging for audit trail
6. No streaming support for long responses
7. Simple error messages (limited debugging info)

### Potential Improvements
1. Add database persistence for sessions
2. Implement user authentication and authorization
3. Add request/response logging and audit trail
4. Implement streaming responses
5. Add rate limiting and quota management
6. Add more response formats (Markdown, LaTeX, etc.)
7. Implement session auto-cleanup (TTL)
8. Add metrics/monitoring integration
9. Support for file uploads/attachments
10. Conversation summarization and search

