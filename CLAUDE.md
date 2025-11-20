# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ResearchAI is a multi-provider AI chat API server built with Kotlin and Ktor. It provides a unified REST API for interacting with multiple AI providers (Claude, OpenAI, HuggingFace) through a clean architecture with abstraction layers.

## Development Commands

### Build and Run
```bash
# Run in development mode
./gradlew run

# Build project
./gradlew build

# Build fat JAR with all dependencies
./gradlew buildFatJar

# Run the JAR
java -jar build/libs/ResearchAI-0.0.1-all.jar
```

### Environment Setup
```bash
# Load environment variables from .env
export $(cat .env | xargs)
```

### Docker
```bash
# Start with docker-compose
docker-compose up -d

# View logs
docker-compose logs -f

# Stop
docker-compose down

# Rebuild without cache
docker-compose build --no-cache
```

## Folder accessibility

You also have access to the parent folder.
The parent folder contains the following subfolder:
- Tasks
You can change the necessary files in the specified subfolders of the parent folder.


## Architecture

### Multi-Provider Design

The codebase uses a **Strategy Pattern** with Clean Architecture principles to support multiple AI providers interchangeably:

1. **Domain Layer** (`com.researchai.domain`):
   - `AIProvider` interface - Base contract for all AI providers
   - `AIProviderFactory` - Factory for creating provider instances
   - `ProviderType` enum - Defines supported providers (CLAUDE, OPENAI, HUGGINGFACE, GEMINI, CUSTOM)
   - `AIRequest`/`AIResponse` - Provider-agnostic request/response models
   - Use cases: `SendMessageUseCase`, `GetModelsUseCase`

2. **Data Layer** (`com.researchai.data.provider`):
   - Provider-specific implementations in subpackages:
     - `claude/` - ClaudeProvider with ClaudeApiModels and ClaudeMapper
     - `openai/` - OpenAIProvider with OpenAIApiModels and OpenAIMapper
     - `huggingface/` - HuggingFaceProvider with HuggingFaceApiModels and HuggingFaceMapper
   - Each provider has:
     - **Provider class** - Implements AIProvider interface
     - **API models** - Provider-specific request/response structures
     - **Mapper** - Converts between domain models and provider-specific models

3. **Presentation Layer** (`com.researchai.routes`):
   - `ChatRoutes.kt` - Legacy `/chat` endpoint + session management
   - `ProviderRoutes.kt` - New v2 API endpoints for multi-provider support

### Dependency Injection

The `AppModule` class (`com.researchai.di.AppModule`) is a manual DI container that initializes:
- HTTP client with timeouts (5 min request, 10 sec connect)
- Persistence layer (PersistenceManager, JsonPersistenceStorage, ScheduledTaskStorage)
- Provider factory
- Repositories (ConfigRepository, SessionRepository)
- Use cases (SendMessageUseCase, GetModelsUseCase)
- Services (ChatSessionManager, AgentManager, SchedulerManager)
- Legacy services (ClaudeService)

**Important**: Close the AppModule on application shutdown to:
- Shutdown SchedulerManager (stop all tasks, save state)
- Save all pending sessions to disk
- Shutdown ChatSessionManager
- Shutdown MCPServerManager
- Clean up HTTP client resources
- Gracefully shutdown PersistenceManager

### Session Management

Sessions are managed by `ChatSessionManager` with automatic persistence:
- Each session maintains conversation history as `List<Message>`
- Sessions can be associated with agents (for custom system prompts) OR scheduled tasks
- **Mutual exclusivity**: A session has EITHER `agentId` OR `scheduledTaskId` (or neither)
- Messages store metadata (model, tokens used, response time)
- **Automatic persistence**: Sessions are saved to disk and restored on restart

#### Persistence Architecture

The persistence system uses a **Hybrid approach** (JSON + in-memory cache):

1. **PersistenceStorage** interface (`com.researchai.persistence.PersistenceStorage`):
   - Abstract interface for storage implementations
   - Supports save, load, delete operations

2. **JsonPersistenceStorage** (`com.researchai.persistence.JsonPersistenceStorage`):
   - Saves sessions as JSON files in `data/sessions/` directory
   - Each session is stored as `{sessionId}.json`
   - Uses atomic file writes to prevent corruption
   - Automatically creates storage directory on startup

3. **PersistenceManager** (`com.researchai.persistence.PersistenceManager`):
   - Manages asynchronous background saving
   - Batches multiple saves to reduce I/O operations
   - Default settings: 1 second delay, batch size of 10 sessions
   - Ensures all sessions are saved during graceful shutdown
   - Sessions marked as "dirty" are queued for saving

4. **ChatSessionManager** integration:
   - Loads all sessions from storage on initialization
   - Automatically marks sessions as dirty on any modification
   - Sessions are persisted asynchronously without blocking API calls

**Important**: Sessions include:
- Message history
- Archived messages (from compression)
- Compression configuration and count
- Session metadata (created/accessed timestamps, agent ID, scheduled task ID)

### API Versioning

The application supports two API versions:

**Legacy API (v1)**:
- `POST /chat` - Single endpoint for Claude with automatic provider detection based on model name
- Maintains backward compatibility

**New API (v2)**:
- `POST /api/v2/chat` - Multi-provider chat with explicit provider specification
- `GET /api/v2/providers` - List available providers
- `GET /api/v2/providers/{provider}/models` - Get models for specific provider

## Configuration

### Environment Variables

All providers are configured via environment variables (loaded from `.env` file):

**Claude** (required):
- `CLAUDE_API_KEY` - API key from console.anthropic.com
- `CLAUDE_MODEL` - Optional, defaults to `claude-haiku-4-5-20251001`
- `CLAUDE_MAX_TOKENS` - Optional, defaults to 8192
- `CLAUDE_TEMPERATURE` - Optional, defaults to 1.0

**OpenAI** (optional):
- `OPENAI_API_KEY` - Required to enable OpenAI
- `OPENAI_ORGANIZATION_ID` - Optional
- `OPENAI_PROJECT_ID` - Optional
- `OPENAI_MODEL` - Optional, defaults to `gpt-4-turbo`
- `OPENAI_MAX_TOKENS` - Optional, defaults to 4096
- `OPENAI_TEMPERATURE` - Optional, defaults to 1.0

**HuggingFace** (optional):
- `HUGGINGFACE_API_KEY` - Required to enable HuggingFace
- `HUGGINGFACE_MODEL` - Optional, defaults to `deepseek-ai/DeepSeek-R1:fastest`
- `HUGGINGFACE_MAX_TOKENS` - Optional, defaults to 8192
- `HUGGINGFACE_TEMPERATURE` - Optional, defaults to 1.0

### Configuration Loading

Configuration is loaded at application startup in `Application.kt`:
1. `DotenvLoader.load()` - Loads .env file
2. Config objects created: `getClaudeConfig()`, `getOpenAIConfig()`, `getHuggingFaceConfig()`
3. Providers with missing API keys are disabled with warnings
4. AppModule initialized with available configurations

## Key Design Patterns

1. **Strategy Pattern**: AIProvider interface allows swapping providers without changing business logic
2. **Factory Pattern**: AIProviderFactory creates provider instances based on ProviderType
3. **Repository Pattern**: ConfigRepository and SessionRepository abstract data access
4. **Use Case Pattern**: Business logic encapsulated in use cases (SendMessageUseCase, GetModelsUseCase)
5. **Mapper Pattern**: Each provider has a mapper to convert between domain and API-specific models

## Adding New Providers

To add a new AI provider:

1. Add provider type to `ProviderType` enum in `domain/models/ProviderType.kt`
2. Create provider config class extending `ProviderConfig` in `domain/models/ProviderConfig.kt`
3. Create provider implementation folder under `data/provider/{providername}/`:
   - `{ProviderName}ApiModels.kt` - API-specific request/response models
   - `{ProviderName}Mapper.kt` - Converts domain models to/from API models
   - `{ProviderName}Provider.kt` - Implements AIProvider interface
4. Register provider in `AIProviderFactoryImpl` constructor
5. Add configuration loading in `Application.kt`
6. Add config getter function (e.g., `get{Provider}Config()`) in config package

## Chat Compression

The application supports **automatic chat compression** to manage long conversations and avoid context window limits.

### Compression Strategies

Three compression strategies are available:

1. **FULL_REPLACEMENT** (default):
   - Replaces all messages with a single AI-generated summary
   - Maximum compression, but loses conversation structure
   - Triggered when message count ≥ 10 (configurable)
   - Best for: Long conversations where full history isn't needed

2. **SLIDING_WINDOW**:
   - Keeps last N messages intact, summarizes older messages
   - Maintains recent context while compressing old history
   - Triggered when message count ≥ 12 (configurable)
   - Keeps last 6 messages by default
   - Best for: Conversations where recent context is important

3. **TOKEN_BASED**:
   - Adaptive compression based on token count
   - Compresses when reaching 80% of context window (configurable)
   - Keeps last 40% of tokens in original form
   - Best for: Managing context window limits precisely

### Compression API Endpoints

**Compress a session:**
```http
POST /compression/compress
Content-Type: application/json

{
  "sessionId": "session-id",
  "providerId": "CLAUDE",  // optional, defaults to CLAUDE
  "model": "claude-haiku-4-5-20251001",  // optional
  "contextWindowSize": 200000  // optional, for TOKEN_BASED strategy
}
```

**Update compression config:**
```http
POST /compression/config
Content-Type: application/json

{
  "sessionId": "session-id",
  "config": {
    "strategy": "SLIDING_WINDOW",
    "slidingWindowMessageThreshold": 12,
    "slidingWindowKeepLast": 6
  }
}
```

**Get compression config:**
```http
GET /compression/config/{sessionId}
```

**Check if compression is needed:**
```http
GET /compression/check/{sessionId}?contextWindowSize=200000
```

**Get archived messages:**
```http
GET /compression/archived/{sessionId}
```

### Compression Architecture

- **CompressionAlgorithm**: Interface for compression algorithms
- **ChatCompressionService**: Manages compression and AI-based summarization
- **CompressionStrategy**: Enum defining available strategies
- **CompressionConfig**: Per-session configuration
- **ChatSession**: Stores archived messages and compression count

Original messages are preserved in `archivedMessages` for audit/review purposes.

## Task Scheduler

The application supports **automated recurring chat tasks** via the Task Scheduler feature.

### Overview

The Task Scheduler allows creating scheduled tasks that automatically send messages to AI providers at configurable intervals. Each task creates a dedicated chat session and executes periodically.

### Architecture Components

1. **Domain Layer** (`com.researchai.scheduler`):
   - `ScheduledTask` interface - Base contract for scheduled tasks
   - `TaskScheduler<T>` abstract class - Coroutine-based scheduler with lifecycle management
   - `ScheduledChatTask` data class - Task configuration with hybrid provider/model settings
   - `ChatTaskScheduler` concrete class - Chat message execution implementation

2. **Service Layer** (`com.researchai.services`):
   - `SchedulerManager` - Central manager for all task schedulers
   - Handles lifecycle: creation, start, stop, delete
   - Auto-loads tasks from storage on startup

3. **Persistence Layer** (`com.researchai.persistence`):
   - `ScheduledTaskStorage` - JSON-based task persistence
   - Storage directory: `data/scheduled_tasks/`
   - Uses atomic file writes to prevent corruption

### Key Features

- **Recurring Execution**: Tasks run at user-defined intervals (minimum 10 seconds)
- **Immediate Execution**: Optional first execution on task creation
- **Hybrid Configuration**: Global provider/model settings with per-task overrides
- **Graceful Error Handling**: Errors displayed in chat without stopping scheduler
- **Session Integration**: Automatic session creation with `scheduledTaskId` linkage
- **Persistent Storage**: Tasks survive application restarts
- **Full Lifecycle**: Create, start, stop, delete operations via REST API

### API Endpoints

**Task Management:**
- `POST /scheduler/tasks` - Create new scheduled task
- `GET /scheduler/tasks` - List all tasks
- `GET /scheduler/tasks/{id}` - Get task details
- `POST /scheduler/tasks/{id}/stop` - Pause task execution
- `POST /scheduler/tasks/{id}/start` - Resume task execution
- `DELETE /scheduler/tasks/{id}` - Delete task and associated session

**Create Task Request:**
```json
{
  "title": "Daily Market Summary",
  "taskRequest": "Provide market summary",
  "intervalSeconds": 86400,
  "executeImmediately": true,
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5"
}
```

### Lifecycle Management

**On Application Start:**
1. `SchedulerManager` initialized by `AppModule`
2. Loads all tasks from `data/scheduled_tasks/`
3. Creates `ChatTaskScheduler` instances
4. Starts schedulers (without calling `initialize()` - sessions exist)

**On Application Shutdown:**
1. `AppModule.close()` calls `SchedulerManager.shutdown()`
2. All schedulers stopped
3. All tasks saved to disk
4. Coroutine scopes cancelled

### Integration with Sessions

Sessions are linked to tasks via `scheduledTaskId` field:

```kotlin
data class ChatSession(
    val id: String,
    val agentId: String? = null,
    val scheduledTaskId: String? = null,  // Links to task
    // ...
)
```

**Mutual Exclusivity:**
- A session can have EITHER `agentId` OR `scheduledTaskId` (or neither)
- When task is deleted, associated session is also deleted
- When session is manually deleted, task becomes orphaned (will fail on execution)

### Error Handling Strategy

Execution errors are handled gracefully:

```kotlin
override suspend fun onTaskError(error: Exception) {
    val errorMessage = """
        ⚠️ Ошибка выполнения задачи:
        ${error.message}
        Следующая попытка через ${formatInterval(task.intervalSeconds)}
    """.trimIndent()

    sessionManager.addMessageToSession(sessionId, MessageRole.ASSISTANT, errorMessage)
}
```

- Errors posted to chat as messages
- Scheduler continues running
- Next execution attempted after interval
- No automatic task termination

### Frontend Integration

**UI Components:**
- Scheduler button in sidebar
- Modal form for task creation
- "Задачи" category filter for task sessions
- Task icon in session list

**JavaScript Modules:**
- `static/js/api/schedulerApi.js` - API client
- `static/js/ui/schedulerModal.js` - Modal UI logic

### Configuration

**Minimum Interval:** 10 seconds (enforced in backend and frontend)

**Provider/Model Configuration:**
- `providerId = null, model = null` → Uses global settings
- `providerId = CLAUDE, model = null` → Uses Claude with global model
- `providerId = CLAUDE, model = "opus"` → Uses Claude Opus specifically

### Important Notes

- **Persistence**: Tasks are automatically saved to `data/scheduled_tasks/` directory
- **Auto-loading**: Tasks are restored and started on application restart
- **Graceful Shutdown**: Always use proper shutdown to save all tasks
- **Documentation**: See `Documents/TASK_SCHEDULER.md` for comprehensive details

## Important Notes

- **No tests**: The project currently has no test suite
- **Session persistence**: Sessions are automatically saved to `data/sessions/` directory
- **Task persistence**: Scheduled tasks are automatically saved to `data/scheduled_tasks/` directory
- **Main application class**: Entry point is `com.researchai.ApplicationKt` (Application.kt)
- **Java version**: Requires Java 17+
- **Static resources**: Web UI files are in `src/main/resources/static/`
- **CORS**: Enabled for all origins with `anyHost()`
- **Request timeout**: HTTP client has 5-minute timeout for long AI responses
- **Graceful shutdown**: Always use proper shutdown to save all sessions and tasks


## Tasks
The `../Tasks/` folder is used to store progress on completing tasks.
If the [Task] tag is specified in the prompt, then it is necessary to keep in mind that working with the specified folder