# ResearchAI Architecture Summary

## Quick Overview

ResearchAI is a Ktor-based REST API server that acts as a bridge between a frontend application and the Claude API. It provides conversation management, agent-based interactions, and flexible response formatting.

**Key Statistics:**
- Language: Kotlin
- Framework: Ktor Server
- HTTP Client: Ktor Client (CIO)
- Architecture: Layered (Routes → Services → Models)
- Data Storage: In-memory (no database)
- Configuration: Environment variables + .env file support

---

## Core Components at a Glance

### 1. HTTP Layer (Routes)
**File:** `com/example/routes/ChatRoutes.kt`

Main endpoints:
- `POST /chat` - Process user message and get Claude response
- `GET/DELETE /sessions/*` - Manage chat sessions
- `GET /agents/*` - List and start agent sessions
- `GET /models` - List available Claude models
- `GET /config` - Get current configuration

### 2. Business Logic (Services)
**Files:** `com/example/services/`

| Service | Purpose |
|---------|---------|
| `ClaudeService` | Makes HTTP requests to Claude API |
| `ChatSessionManager` | Stores and manages chat sessions (in-memory) |
| `AgentManager` | Manages agent definitions and system prompts |
| `ClaudeMessageFormatter` | Pre/post-processes messages for different formats |

### 3. Data Models
**Files:** `com/example/models/`

| Model | Purpose |
|-------|---------|
| `ClaudeRequest/Response` | Maps to Claude API schema |
| `ClaudeMessage` | Individual message in conversation |
| `ChatSession` | Stores conversation history for a session |
| `Agent` | Defines agent name, ID, and system prompt |
| `ResponseFormat` | Enum: PLAIN_TEXT, JSON, XML |
| `ChatRequest/Response` | Maps to client API schema |

### 4. Configuration
**File:** `com/example/config/ClaudeConfig.kt`

Default values:
- Model: Claude Haiku 4.5 (claude-haiku-4-5-20251001)
- Max tokens: 8192
- Temperature: 1.0
- API version: 2023-06-01

Configurable via:
- Environment variables (highest priority)
- .env file
- Hardcoded defaults (lowest priority)

---

## Request Processing Flow (Simplified)

```
1. Client sends: POST /chat with message, optional sessionId, format
2. Server retrieves or creates session
3. If agent-based session, retrieves agent's system prompt
4. ClaudeMessageFormatter enhances message (adds format instructions)
5. ClaudeService makes HTTP POST to Claude API
6. Claude returns response
7. ClaudeMessageFormatter post-processes response (validates, formats)
8. Messages added to session history
9. Response returned to client with sessionId
```

---

## Key Features

### Multi-Format Response Support
- **PLAIN_TEXT**: Returns raw response from Claude
- **JSON**: Validates JSON structure, pretty-prints, enforces strict format
- **XML**: Validates XML, pretty-prints with proper formatting

Pre-processing: Adds strict instructions to user message for format compliance
Post-processing: Cleans markdown blocks, validates, and formats output

### Session Management
- In-memory storage (ConcurrentHashMap for thread-safety)
- Sessions persist during server runtime
- Full message history maintained per session
- Last accessed timestamp tracked for monitoring
- Optional agent association for personalized behavior

### Agent System
Two built-in agents:
1. **Greeting Assistant**: Simple welcoming agent
2. **AI Tutor**: Complex multi-stage educational assistant
   - Gathers information about learner goals and level
   - Proposes learning plan
   - Provides structured educational content

Custom agents can be registered at runtime.

### HTTP Client Configuration
- Engine: CIO (Coroutine I/O)
- Connection timeout: 10 seconds
- Request timeout: 5 minutes
- Content negotiation: JSON
- Logging: INFO level

---

## Data Structures

### ChatRequest (Client → Server)
```kotlin
{
  message: String,                    // Required
  format: ResponseFormat,             // Optional, defaults to PLAIN_TEXT
  sessionId: String?,                 // Optional, creates new if not provided
  model: String?,                     // Optional, overrides default
  temperature: Double?,               // Optional, overrides default
  maxTokens: Int?                     // Optional, overrides default
}
```

### ChatResponse (Server → Client)
```kotlin
{
  response: String,                   // Formatted answer
  sessionId: String                   // Session ID for continuation
}
```

### ClaudeRequest (Server → Claude API)
```kotlin
{
  model: String,                      // Model ID
  max_tokens: Int,                    // Max output tokens
  messages: [{role, content}, ...],   // Conversation history
  temperature: Double,                // Sampling parameter
  system: String?                     // System prompt for agent
}
```

### ClaudeResponse (Claude API → Server)
```kotlin
{
  id: String,                         // Response ID
  type: String,                       // "message"
  role: String,                       // "assistant"
  content: [{type: "text", text: "..."}],  // Response text
  model: String,                      // Model used
  usage: {input_tokens, output_tokens}     // Token usage
}
```

---

## Session Lifecycle

```
1. Create Session
   - User sends first message without sessionId
   - OR explicitly calls POST /agents/start
   - New UUID generated
   - Session stored in ChatSessionManager

2. Use Session
   - Include sessionId in subsequent requests
   - Message history retrieved
   - New message added to history
   - Full history sent to Claude (for context)

3. Clear/Delete
   - POST /sessions/{id}/clear → clears messages
   - DELETE /sessions/{id} → removes entire session

4. End
   - Sessions lost when server restarts
   - No automatic cleanup
```

---

## Configuration Sources & Precedence

### Loading Order (Highest to Lowest Priority)
1. **System Environment Variables**
   - `CLAUDE_API_KEY` (required)
   - `CLAUDE_MODEL`
   - `CLAUDE_MAX_TOKENS`
   - `CLAUDE_TEMPERATURE`

2. **.env File** (via DotenvLoader)
   - File path: `./.env` (in project root)
   - Format: `KEY=VALUE` or `KEY="VALUE"`
   - Supports comments: `# comment`

3. **Hardcoded Defaults**
   ```
   model = "claude-haiku-4-5-20251001"
   maxTokens = 8192
   temperature = 1.0
   apiUrl = "https://api.anthropic.com/v1/messages"
   apiVersion = "2023-06-01"
   ```

### Note on Environment Loading
DotenvLoader uses Java reflection to modify System environment at runtime. Falls back to System.setProperty if reflection fails.

---

## Response Format Handling

### JSON Format
**Enhancement (before sending to Claude):**
```
Adds strict instructions:
- RESPOND ONLY WITH RAW JSON
- NO markdown code blocks
- Start with { and end with }
- Use only specified keys

Template provided:
{
  "title": "краткое описание",
  "source_request": "исходный запрос",
  "answer": "ответ"
}
```

**Processing (after receiving from Claude):**
1. Remove markdown code blocks (```json)
2. Parse with Kotlinx JSON
3. Pretty-print with indentation
4. Return formatted JSON

### XML Format
**Enhancement (before sending to Claude):**
```
Adds strict instructions:
- RESPOND ONLY WITH VALID XML
- Include XML declaration
- NO markdown code blocks
- Use only specified tags

Template provided:
<?xml version="1.0" encoding="UTF-8"?>
<response>
  <title>краткое описание</title>
  <source_request>исходный запрос</source_request>
  <answer>ответ</answer>
</response>
```

**Processing (after receiving from Claude):**
1. Remove markdown code blocks (```xml)
2. Parse and validate XML
3. Remove whitespace-only nodes
4. Pretty-print with indentation
5. Return formatted XML

### PLAIN_TEXT Format
No enhancement or special processing.

---

## Available Claude Models

The system supports 8 Claude models:

| Model | Version | ID |
|-------|---------|-----|
| Haiku 4.5 | Latest | claude-haiku-4-5-20251001 |
| Sonnet 4.5 | Latest | claude-sonnet-4-5-20250929 |
| Opus 4.1 | Latest | claude-opus-4-1-20250805 |
| Opus 4 | Previous | claude-opus-4-20250514 |
| Sonnet 4 | Previous | claude-sonnet-4-20250514 |
| Sonnet 3.7 | Older | claude-3-7-sonnet-20250219 |
| Haiku 3.5 | Older | claude-3-5-haiku-20241022 |
| Haiku 3 | Older | claude-3-haiku-20240307 |

**Default:** Claude Haiku 4.5

Client can override via `model` parameter in ChatRequest.

---

## Error Handling Strategy

### HTTP Errors from Claude API
- Check response status code
- Parse error response as JSON (ClaudeError)
- Extract error message from response
- Return formatted error to client
- Log full error details server-side

### Request Validation
- Message cannot be empty
- Session ID format not strictly validated
- Model ID validated against whitelist

### General Exception Handling
- All service methods wrapped in try-catch
- Exceptions logged with full stack trace
- User-friendly error message returned

---

## Performance & Scalability

### Current Characteristics
- **Single server instance** (no clustering)
- **In-memory storage** (ConcurrentHashMap)
- **No database** (sessions lost on restart)
- **Thread-safe** (concurrent access to sessions)
- **Synchronous HTTP** (not streaming)

### Capacity Limitations
- Memory usage grows with number of sessions
- No automatic cleanup of old sessions
- No rate limiting
- No request queuing

### Scaling Considerations
For production deployment:
- Use database for session persistence
- Implement session TTL (time-to-live)
- Add rate limiting
- Use cache (Redis) for frequently accessed data
- Deploy multiple instances behind load balancer
- Implement message batching for large histories

---

## Security Considerations

### Current Implementation
- API key stored in environment variables (not hardcoded)
- CORS enabled for all hosts (permissive)
- Basic input validation (non-empty messages)
- No authentication/authorization
- No HTTPS enforcement (depends on proxy)

### Recommendations for Production
1. Restrict CORS to specific domains
2. Implement user authentication (JWT, OAuth)
3. Add rate limiting per user/IP
4. Encrypt session data at rest
5. Audit all API requests
6. Use HTTPS with valid certificate
7. Validate and sanitize all inputs
8. Implement request signing/verification

---

## Integration Points

### External Services
- **Claude API** (Anthropic)
  - Endpoint: https://api.anthropic.com/v1/messages
  - Authentication: API key in header
  - Protocol: HTTP/2 over HTTPS

### Frontend Integration
- **Static assets** served from `/static` directory
- **CORS headers** for cross-origin requests
- **JSON** for all request/response bodies

### Potential Integrations
- Database (PostgreSQL, MongoDB)
- Cache (Redis)
- Monitoring (Prometheus, Datadog)
- Logging (ELK stack, CloudWatch)
- Message queues (Kafka, RabbitMQ)

---

## Known Limitations

1. **No Persistence**
   - Sessions lost on server restart
   - No message history archive

2. **No Authentication**
   - Any client can access any session
   - No user isolation

3. **Limited Scalability**
   - In-memory storage only
   - Single instance only
   - No load balancing

4. **No Streaming**
   - Full response buffered before returning
   - No token-by-token streaming

5. **No Rate Limiting**
   - No protection against abuse
   - No quota management

6. **Limited Error Details**
   - Simple error messages to client
   - Full details only in server logs

---

## Recommended Next Steps

### For Development
1. Add logging integration (ELK, Datadog)
2. Implement request tracing
3. Add metrics collection
4. Create integration tests
5. Add API documentation (OpenAPI/Swagger)

### For Production
1. Add database for persistence
2. Implement authentication/authorization
3. Add rate limiting and quota management
4. Set up monitoring and alerting
5. Implement session encryption
6. Add audit logging
7. Use API gateway for security
8. Implement graceful shutdown

### For Features
1. Streaming responses
2. File uploads/attachments
3. Conversation search
4. Message editing
5. Batch requests
6. Custom response formats
7. Conversation summarization
8. Context windows management

---

## File Organization

```
src/main/kotlin/
├── Application.kt                    # Entry point, module setup
├── Routing.kt                        # Route configuration
├── config/
│   ├── ClaudeConfig.kt              # Configuration data class
│   └── DotenvLoader.kt              # .env file loader
├── models/
│   ├── Agent.kt                     # Agent model
│   ├── ChatRequest.kt               # Client request model
│   ├── ChatResponse.kt              # Client response model
│   ├── ChatSession.kt               # Session model
│   ├── ClaudeModel.kt               # Model definitions
│   ├── ClaudeModels.kt              # Claude API models
│   ├── ResponseFormat.kt            # Format enum
│   └── SessionResponses.kt          # Session API models
├── routes/
│   └── ChatRoutes.kt                # HTTP endpoint handlers
└── services/
    ├── AgentManager.kt              # Agent management
    ├── ChatSessionManager.kt        # Session management
    ├── ClaudeMessageFormatter.kt    # Message formatting
    └── ClaudeService.kt             # Claude API integration
```

---

## References & Documentation

- **Analysis Documents:**
  - `CODEBASE_ANALYSIS.md` - Detailed technical analysis
  - `ARCHITECTURE_DIAGRAMS.md` - Visual architecture diagrams

- **External Resources:**
  - Ktor Documentation: https://ktor.io
  - Kotlin Serialization: https://github.com/Kotlin/kotlinx.serialization
  - Claude API: https://docs.anthropic.com
  - Anthropic API Documentation: https://docs.anthropic.com/en/api/getting-started

---

## Document Metadata

- Generated: 2025-11-11
- Version: 1.0
- Branch: Lesson5
- Last Commit: Chat settings (4a2b449)
- Scope: Full codebase architecture analysis

For detailed information, see:
- `CODEBASE_ANALYSIS.md` for component details
- `ARCHITECTURE_DIAGRAMS.md` for visual representations
