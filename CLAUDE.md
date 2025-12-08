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
   - `ProviderType` enum - Defines supported providers (CLAUDE, OPENAI, HUGGINGFACE, OLLAMA)
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
- Persistence layer (PersistenceManager, JsonPersistenceStorage, AssistantStorage, ScheduledTaskStorage)
- Provider factory
- Repositories (ConfigRepository, SessionRepository)
- Use cases (SendMessageUseCase, GetModelsUseCase)
- Services (ChatSessionManager, AssistantManager, SchedulerManager)
- Legacy services (ClaudeService)

**Important**: Close the AppModule on application shutdown to:
- Shutdown SchedulerManager (stop all tasks, save state)
- Save all pending sessions to disk
- Shutdown ChatSessionManager
- Shutdown MCPServerManager
- Close AssistantManager (saves all custom assistants)
- Clean up HTTP client resources
- Gracefully shutdown PersistenceManager

### Session Management

Sessions are managed by `ChatSessionManager` with automatic persistence:
- Each session maintains conversation history as `List<Message>`
- Sessions can be associated with assistants (for custom system prompts) OR scheduled tasks
- **Mutual exclusivity**: A session has EITHER `assistantId` OR `scheduledTaskId` (or neither)
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
- Session metadata (created/accessed timestamps, assistant ID, scheduled task ID)

### API Versioning

The application supports two API versions:

**Legacy API (v1)**:
- `POST /chat` - Single endpoint for Claude with automatic provider detection based on model name
- Maintains backward compatibility

**New API (v2)**:
- `POST /api/v2/chat` - Multi-provider chat with explicit provider specification
- `GET /api/v2/providers` - List available providers
- `GET /api/v2/providers/{provider}/models` - Get models for specific provider

### Assistant Management

The application supports **custom AI assistants** with personalized system prompts and behavior.

#### Overview

Assistants allow creating reusable AI personas with specific behaviors, expertise, and communication styles. Each assistant has:
- **Unique ID**: Identifier for the assistant
- **Name**: Display name
- **System Prompt**: Defines assistant's behavior and expertise
- **Description**: Brief description of assistant's purpose
- **Type**: System (built-in) or User (custom)

#### Architecture Components

1. **Assistant Model** (`com.researchai.models.Assistant`):
   - Data class with id, name, systemPrompt, description, isSystem
   - `@Serializable` for JSON persistence

2. **AssistantStorage** (`com.researchai.persistence.AssistantStorage`):
   - Interface for assistant persistence
   - `JsonAssistantStorage` implementation saves to `data/assistants/`

3. **AssistantManager** (`com.researchai.services.AssistantManager`):
   - Manages assistants in memory and storage
   - Auto-loads custom assistants on startup
   - Protects system assistants from modification/deletion

4. **AssistantRoutes** (`com.researchai.routes.AssistantRoutes`):
   - REST API for CRUD operations
   - Full validation and error handling

#### Assistant Types

**System Assistants** (`isSystem: true`):
- Built into the application
- Cannot be modified or deleted via API
- Examples: `greeting-assistant`, `ai-tutor`
- Always available

**Custom Assistants** (`isSystem: false`):
- Created via REST API
- Fully editable and deletable
- Persisted to `data/assistants/{id}.json`
- Survive application restarts

#### API Endpoints

**Assistant Management:**
- `GET /assistants` - List all assistants (system + custom)
- `GET /assistants/{id}` - Get assistant by ID
- `POST /assistants` - Create new custom assistant
- `PUT /assistants/{id}` - Update custom assistant
- `DELETE /assistants/{id}` - Delete custom assistant

**Example - Create Assistant:**
```http
POST /assistants
Content-Type: application/json

{
  "id": "code-reviewer",
  "name": "Code Reviewer",
  "systemPrompt": "You are an expert code reviewer...",
  "description": "Reviews code and suggests improvements"
}
```

**Example - Use in Chat:**
```http
POST /chat
Content-Type: application/json

{
  "message": "Review this code: ...",
  "sessionId": "my-session",
  "assistantId": "code-reviewer"
}
```

#### Persistence

- **Storage**: JSON files in `data/assistants/` directory
- **Format**: One file per assistant (`{id}.json`)
- **Loading**: Automatic on application startup
- **Saving**: Immediate on create/update operations
- **Atomic Writes**: Protection against corruption

#### Important Notes

- **Session Integration**: Sessions can have `assistantId` to use custom system prompts
- **Mutual Exclusivity**: Session has EITHER `assistantId` OR `scheduledTaskId` (or neither)
- **Protection**: System assistants cannot be modified or deleted
- **Documentation**: See `Documents/ASSISTANT_API.md` for comprehensive API docs

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

**Tech Support** (optional):
- `TRELLO_SUPPORT_BOARD_ID` - Trello board ID for ticket management
- `GH_DEFAULT_OWNER` - Default GitHub repository owner for Tech Support queries
- `GH_DEFAULT_REPO` - Default GitHub repository name for Tech Support queries

**GitHub MCP** (required for GitHub features):
- `GITHUB_TOKEN` - GitHub personal access token for GitHub MCP server

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
    val assistantId: String? = null,
    val scheduledTaskId: String? = null,  // Links to task
    // ...
)
```

**Mutual Exclusivity:**
- A session can have EITHER `assistantId` OR `scheduledTaskId` (or neither)
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

## Tech Support

The application provides an **AI-powered Tech Support** service with RAG, Trello, and GitHub integration.

### Overview

Tech Support automatically classifies user queries and enriches AI responses with context from multiple sources:

**Data Sources:**
- **RAG** - Knowledge base documentation for answering how-to questions
- **Trello** - Ticket management for bug reports and feature requests
- **GitHub** - Repository information (branches, commits, issues, PRs)

### Query Types

The service automatically classifies queries into:
- `BUG_REPORT` - Error reports and issues
- `HOW_TO` - How-to questions
- `STATUS_CHECK` - Ticket status inquiries
- `FEATURE_REQUEST` - New feature requests
- `PROJECT_MANAGEMENT` - Task priorities and project status
- `GITHUB_INFO` - Repository information (branches, commits, issues, PRs)
- `GENERAL` - Other questions

### GitHub Integration

When a query is classified as `GITHUB_INFO`, the service fetches context from GitHub MCP:

**Supported queries:**
- "Какие ветки в репозитории?" → fetches branches
- "Покажи последние коммиты" → fetches recent commits
- "Какие открытые issues?" → fetches open issues
- "Какие открытые pull requests?" → fetches PRs

**Configuration:**
```env
GH_DEFAULT_OWNER=owner-name
GH_DEFAULT_REPO=repo-name
GITHUB_TOKEN=ghp_xxx
```

**API Request (override defaults):**
```json
{
  "query": "Какие ветки?",
  "githubOwner": "custom-owner",
  "githubRepo": "custom-repo",
  "includeGithub": true
}
```

### API Endpoints

- `POST /tech-support/query` - Process tech support request
- `POST /tech-support/create-ticket` - Create Trello ticket
- `POST /tech-support/add-faq` - Add FAQ to RAG
- `GET /tech-support/health` - Health check (RAG, Trello, GitHub status)

### Health Check Response

```json
{
  "status": "ok",
  "ragEnabled": true,
  "trelloConnected": true,
  "githubConnected": true
}
```

## PR Review

The application supports **AI-powered Pull Request review** via CLI and GitHub Actions integration.

### Overview

The PR Review feature allows automated code review of GitHub pull requests using AI analysis combined with RAG context from the codebase. Reviews can be triggered manually via CLI or automatically via GitHub Actions.

**Key Features:**
- **MCP Integration**: Fetches PR data (diff, files, comments) via GitHub MCP server
- **RAG Context**: Uses codebase documentation and patterns for contextual review
- **Multiple Review Modes**: Quick, Standard, Thorough analysis levels
- **Focus Areas**: Configurable focus on specific aspects (Security, Performance, Architecture, etc.)
- **Quality Gate**: Configurable score threshold for CI/CD pipelines
- **GitHub Integration**: Post reviews as PR comments automatically

### Architecture Components

1. **Domain Layer** (`com.researchai.domain.models.pr`):
   - `PRReviewRequest` - Request model with repository, PR number, mode, focus areas
   - `PRReviewResult` - Result model with summary, file reviews, score, metadata
   - `ReviewMode` enum - QUICK, STANDARD, THOROUGH
   - `FocusArea` enum - SECURITY, PERFORMANCE, CODE_STYLE, ARCHITECTURE, TESTING, etc.
   - `Severity` enum - CRITICAL, WARNING, INFO
   - Supporting models: `FileReview`, `LineComment`, `Issue`, `ReviewMetadata`

2. **Service Layer** (`com.researchai.services`):
   - `PRReviewService` - Main orchestration service
     - `reviewPullRequest()` - Execute PR review
     - `postReviewAsComment()` - Post review to GitHub as comment
     - `fetchPRData()` - Get PR data via GitHub MCP
     - `gatherRAGContext()` - Retrieve relevant code context
     - `buildReviewPrompt()` - Construct AI prompt with context
     - `executeAIReview()` - Call AI provider for analysis

3. **Routes** (`com.researchai.routes.PRReviewRoutes`):
   - `POST /pr-review` - Execute PR review
   - `POST /pr-review/comment` - Post review as GitHub comment
   - `GET /pr-review/health` - Health check

4. **CLI** (`researchai-cli`):
   - `ReviewCommand` - CLI command for PR review
   - `ReviewHandler` - Command execution logic
   - `ReviewApiClient` - HTTP client for API calls
   - `ReviewOutputFormatter` - Format output (TEXT, JSON, GITHUB markdown)

5. **Assistant**:
   - System assistant `pr-reviewer` with specialized prompt for code review
   - Provides structured JSON output with issues, suggestions, scores

### Review Modes

**QUICK** - Fast summary-only review:
- High-level overview
- Critical issues only
- No line-by-line comments
- ~30-60 seconds

**STANDARD** (default) - Balanced review:
- Detailed summary
- Critical and important issues
- Key line comments
- ~1-2 minutes

**THOROUGH** - Comprehensive review:
- Full analysis
- All issues and suggestions
- Detailed line-by-line comments
- ~2-5 minutes

### Focus Areas

All focus areas have equal priority (configurable):

- **SECURITY** - Authentication, injection, data exposure vulnerabilities
- **PERFORMANCE** - Inefficient algorithms, N+1 queries, memory leaks
- **CODE_STYLE** - Naming conventions, formatting, readability
- **ARCHITECTURE** - Design patterns, separation of concerns, modularity
- **TESTING** - Test coverage, test quality, edge cases
- **DOCUMENTATION** - Code comments, API docs, README updates
- **ERROR_HANDLING** - Exception handling, null safety, error messages
- **KOTLIN_IDIOMS** - Idiomatic Kotlin usage (coroutines, null safety, data classes)

### CLI Usage

**Basic usage:**
```bash
# Review a PR
rai review https://github.com/owner/repo/pull/123

# With specific mode
rai review https://github.com/owner/repo/pull/123 --mode thorough

# With RAG context
rai review https://github.com/owner/repo/pull/123 --use-rag

# With focus areas
rai review https://github.com/owner/repo/pull/123 --focus security,performance

# Output formats
rai review https://github.com/owner/repo/pull/123 --output json
rai review https://github.com/owner/repo/pull/123 --output github

# Post as GitHub comment
rai review https://github.com/owner/repo/pull/123 --post-comment
```

**Environment variables:**
- `RESEARCHAI_SERVER_URL` - ResearchAI server URL (default: http://localhost:8080)
- `GITHUB_TOKEN` - GitHub personal access token (required for --post-comment)

### API Usage

**Execute PR review:**
```http
POST /pr-review
Content-Type: application/json

{
  "repositoryOwner": "owner",
  "repositoryName": "repo",
  "pullRequestNumber": 123,
  "reviewMode": "STANDARD",
  "focusAreas": ["SECURITY", "PERFORMANCE"],
  "useRAG": true,
  "ragMinScore": 0.7,
  "ragMaxChunks": 10
}
```

**Response:**
```json
{
  "requestId": "uuid",
  "pullRequestUrl": "https://github.com/owner/repo/pull/123",
  "summary": {
    "overview": "...",
    "criticalIssues": [...],
    "importantIssues": [...],
    "suggestions": [...],
    "positives": [...]
  },
  "fileReviews": [...],
  "overallScore": 75,
  "metadata": {
    "reviewDurationMs": 45000,
    "filesReviewed": 12,
    "ragContextUsed": true,
    "ragChunksRetrieved": 8,
    "model": "claude-sonnet-4-5",
    "provider": "CLAUDE"
  }
}
```

**Post review comment:**
```http
POST /pr-review/comment
Content-Type: application/json

{
  "reviewResult": { /* PRReviewResult object */ },
  "githubToken": "ghp_..."
}
```

### GitHub Actions Integration

Automatic PR review on every pull request via GitHub Actions workflow.

**Setup:**
1. Add `.github/workflows/pr-review.yml` to your repository
2. Configure GitHub secrets:
   - `RESEARCHAI_SERVER_URL` - Your ResearchAI server URL
   - `GITHUB_TOKEN` - Automatically provided by GitHub

**Workflow triggers:**
- `pull_request:opened` - New PR created
- `pull_request:synchronize` - New commits pushed
- `pull_request:reopened` - PR reopened

**Quality Gate:**
- PR fails CI if review score < 50 (configurable)
- Review posted as PR comment automatically
- Blocks merge if branch protection enabled

**Documentation:** See `Documents/PR_REVIEW_GITHUB_ACTION.md` for detailed setup guide

### RAG Integration

When `useRAG: true`, the review includes context from the codebase:

1. **Query Generation**: Extracts queries from PR title, description, changed files
2. **Context Retrieval**: Searches RAG database for relevant code patterns
3. **Context Injection**: Adds top-ranked chunks to review prompt
4. **Quality Improvement**: AI uses codebase conventions for better reviews

**Configuration:**
- `ragMinScore` - Minimum similarity score (default: 0.7)
- `ragMaxChunks` - Maximum context chunks (default: 10)

### Review Output Format

**TEXT Format** (CLI default):
```
================================================================================
📊 PR REVIEW RESULTS
================================================================================

🟢 Overall Score: 85/100

📝 Summary
--------------------------------------------------------------------------------
This PR implements...

🔴 Critical Issues (2)
--------------------------------------------------------------------------------
• [SECURITY] SQL Injection vulnerability
  Direct string concatenation in query...
  💡 Use parameterized queries instead

🟡 Important Issues (5)
...
```

**JSON Format** (--output json):
- Structured JSON for programmatic processing
- All fields preserved
- Machine-readable

**GITHUB Format** (--output github):
- GitHub-flavored Markdown
- Collapsible sections
- Emoji indicators
- Ready for PR comments

### Scoring System

**Score Range:** 0-100

**Thresholds:**
- 90-100: Excellent, ready to merge
- 70-89: Good, minor issues only
- 50-69: Needs work, important issues to address
- Below 50: Significant concerns, requires major changes

**Factors:**
- Critical issues: -10 to -20 points each
- Important issues: -3 to -7 points each
- Suggestions: -1 to -2 points each
- Positive observations: +1 to +5 points

### GitHub MCP Requirements

The PR Review feature requires GitHub MCP server to be configured:

**Required MCP tools:**
- `pull_request_read` - Get PR details
- `pull_request_diff` - Get PR diff
- `pull_request_files` - Get changed files list
- `add_issue_comment` - Post review comments

**Configuration:**
Ensure GitHub MCP server is registered in `mcp-config.json` with valid GitHub token.

### Important Notes

- **MCP Dependency**: Requires GitHub MCP server to be connected
- **AI Provider**: Works with any configured provider (Claude, OpenAI, HuggingFace)
- **Rate Limits**: Be mindful of GitHub API rate limits
- **Cost**: ~$0.05-$0.20 per PR review depending on provider and size
- **Documentation**: See `Documents/PR_REVIEW_GITHUB_ACTION.md` for CI/CD setup

## Important Notes

- **No tests**: The project currently has no test suite
- **Session persistence**: Sessions are automatically saved to `data/sessions/` directory
- **Task persistence**: Scheduled tasks are automatically saved to `data/scheduled_tasks/` directory
- **Assistant persistence**: Custom assistants are automatically saved to `data/assistants/` directory
- **Main application class**: Entry point is `com.researchai.ApplicationKt` (Application.kt)
- **Java version**: Requires Java 17+
- **Static resources**: Web UI files are in `src/main/resources/static/`
- **CORS**: Enabled for all origins with `anyHost()`
- **Request timeout**: HTTP client has 5-minute timeout for long AI responses
- **Graceful shutdown**: Always use proper shutdown to save all sessions, tasks, and assistants


## Tasks
The `../Tasks/` folder is used to store progress on completing tasks.
If the [Task] tag is specified in the prompt, then it is necessary to keep in mind that working with the specified folder