# Tech Support AI Assistant - Architecture

## Overview

Tech Support AI Assistant is a mini-service that provides intelligent technical support by combining:
- **RAG (Retrieval-Augmented Generation)** - for documentation and FAQ search
- **Trello MCP** - for CRM/ticket management integration
- **GitHub MCP** - for repository information (branches, commits, issues, PRs)
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
│  │    │     ├── fetchTrelloContext()                                   │    │
│  │    │     └── fetchGithubContext()                                   │    │
│  │    └── executeAIRequest()      → Generate response                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
          │                      │                       │
          ▼                      ▼                       ▼
┌─────────────────┐  ┌─────────────────────────────────────┐  ┌──────────────┐
│   RAGManager    │  │        MCPServerManager             │  │AIProviderFact│
│  (Data Layer)   │  │   (Trello MCP + GitHub MCP)         │  │(Claude/etc)  │
└─────────────────┘  └─────────────────────────────────────┘  └──────────────┘
          │                      │                       │
          ▼                      ▼                       ▼
┌─────────────────┐  ┌─────────────────────────────────────┐  ┌──────────────┐
│  Vector Store   │  │    Trello API    │    GitHub API    │  │AI Provider   │
│  (Embeddings)   │  │   (via MCP)      │   (via MCP)      │  │    API       │
└─────────────────┘  └─────────────────────────────────────┘  └──────────────┘
```

## Component Details

### 1. Domain Models (`TechSupportModels.kt`)

```kotlin
// Query classification types
enum class QueryType {
    BUG_REPORT,         // User reports a bug
    HOW_TO,             // User asks how to do something
    STATUS_CHECK,       // User asks about ticket/issue status
    FEATURE_REQUEST,    // User requests new functionality
    PROJECT_MANAGEMENT, // User asks about project status, task priorities
    GITHUB_INFO,        // User asks about GitHub repo (branches, commits, issues, PRs)
    GENERAL             // General questions
}

// Main request/response models
data class TechSupportRequest(
    val query: String,
    val sessionId: String? = null,
    val customerId: String? = null,
    val trelloBoardId: String? = null,
    val githubOwner: String? = null,      // GitHub repository owner
    val githubRepo: String? = null,        // GitHub repository name
    val includeRag: Boolean = true,
    val includeTrello: Boolean = true,
    val includeGithub: Boolean = true,     // Enable GitHub context
    val maxRagResults: Int = 5,
    val maxTrelloResults: Int = 3,
    val maxGithubResults: Int = 10,
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

// Project Management action types
data class TaskSummary(
    val cardId: String,
    val cardName: String,
    val listName: String,
    val priority: String?,  // "high", "medium", "low"
    val url: String?
)

data class ListTasksAction(
    val filter: String,
    val tasks: List<TaskSummary>,
    val totalCount: Int
)

data class TaskRecommendation(
    val order: Int,
    val cardId: String,
    val cardName: String,
    val reason: String,
    val estimatedEffort: String?
)

data class PrioritizeAction(
    val recommendedOrder: List<TaskRecommendation>
)

data class ProjectStatusAction(
    val totalTasks: Int,
    val byPriority: Map<String, Int>,
    val byStatus: Map<String, Int>
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
| `fetchGithubContext()` | Fetches repo info via GitHub MCP |
| `fetchTasksByPriority()` | Fetches tasks filtered by priority labels |
| `getProjectStatus()` | Gets project statistics by priority/status |
| `executeAIRequest()` | Generates response using AI provider |
| `createTicket()` | Creates new Trello ticket via MCP |
| `extractPrioritization()` | Extracts task recommendations from AI response |
| `isGithubConnected()` | Checks if GitHub MCP server is connected |
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
│    ┌─────────────────────┐  ┌─────────────────────────┐     │
│    │ fetchRagContext()   │  │ fetchTrelloContext()    │     │
│    │ - Search docs       │  │ - Search cards via MCP  │     │
│    │ - Get top K results │  │ - Get related tickets   │     │
│    └─────────────────────┘  └─────────────────────────┘     │
│    ┌─────────────────────┐                                   │
│    │ fetchGithubContext()│  (for GITHUB_INFO queries)       │
│    │ - Branches, commits │                                   │
│    │ - Issues, PRs       │                                   │
│    └─────────────────────┘                                   │
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

### 4.1 GitHub MCP Integration

Uses MCP protocol to interact with GitHub:

```kotlin
private suspend fun fetchGithubContext(
    query: String,
    owner: String,
    repo: String,
    maxResults: Int
): GitHubContextResult? {
    val githubClient = mcpServerManager.getClient(config.githubMcpServerId)

    // Determine what to fetch based on query keywords
    val branches = if (query.contains("ветк") || query.contains("branch")) {
        fetchGithubBranches(githubClient, owner, repo, maxResults)
    } else emptyList()

    val commits = if (query.contains("коммит") || query.contains("commit")) {
        fetchGithubCommits(githubClient, owner, repo, maxResults)
    } else emptyList()

    // ... similar for issues and PRs

    return GitHubContextResult(
        formattedContext = formatGithubContext(...),
        branches = branches,
        commits = commits,
        issues = issues,
        pullRequests = pullRequests,
        repositoryInfo = repoInfo
    )
}
```

**GitHub Context Models:**

```kotlin
data class GitHubBranchInfo(
    val name: String,
    val sha: String,
    val isProtected: Boolean,
    val isDefault: Boolean,
    val url: String?
)

data class GitHubCommitInfo(
    val sha: String,
    val message: String,
    val author: String,
    val date: String,
    val url: String?
)

data class GitHubIssueInfo(
    val number: Int,
    val title: String,
    val state: String,
    val author: String,
    val labels: List<String>,
    val url: String?
)

data class GitHubPRInfo(
    val number: Int,
    val title: String,
    val state: String,
    val sourceBranch: String,
    val targetBranch: String,
    val url: String?
)
```

**Available GitHub MCP Tools:**
- `list_branches` - List repository branches
- `list_commits` - List recent commits
- `list_issues` - List repository issues
- `list_pull_requests` - List pull requests

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
        - PROJECT_MANAGEMENT: User asks about project status, task priorities
        - GITHUB_INFO: User asks about repo (branches, commits, issues, PRs)
        - GENERAL: Other questions

        Query: "$query"

        Respond with ONLY the category name.
    """.trimIndent()

    val response = aiProvider.sendMessage(prompt)
    return QueryType.valueOf(response.trim().uppercase())
}
```

### 5.1 Project Management Flow

For `PROJECT_MANAGEMENT` queries, a specialized flow is used:

```
┌──────────────────────────────────────────────────────────────┐
│ 1. CLASSIFY QUERY → PROJECT_MANAGEMENT                       │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ 2. EXTRACT PRIORITY FILTER                                   │
│    - "high" if query contains "high", "важн", "высок"       │
│    - "medium" if query contains "medium", "средн"            │
│    - "low" if query contains "low", "низк"                   │
│    - null for all tasks                                      │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ 3. GATHER CONTEXT (Parallel)                                 │
│    ┌─────────────────────────┐  ┌─────────────────────────┐ │
│    │ fetchTasksByPriority()  │  │ fetchRagContext()       │ │
│    │ - Get lists from board  │  │ - Project documentation │ │
│    │ - Get cards from lists  │  │ - Architecture context  │ │
│    │ - Filter by label       │  └─────────────────────────┘ │
│    └─────────────────────────┘                               │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ 4. BUILD PROJECT MANAGEMENT PROMPT                           │
│    - Task list with priorities and statuses                  │
│    - RAG context for project understanding                   │
│    - Instructions for prioritization analysis                │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ 5. EXTRACT SUGGESTED ACTIONS                                 │
│    - LIST_TASKS: All filtered tasks                         │
│    - PRIORITIZE: AI recommendations                          │
│    - VIEW_TICKET: Links to top tasks                        │
└──────────────────────────────────────────────────────────────┘
```

**fetchTasksByPriority() Implementation:**

```kotlin
private suspend fun fetchTasksByPriority(
    boardId: String,
    priority: String?,
    maxResults: Int
): List<TrelloTicketInfo> {
    // 1. Get all lists from board
    val lists = trelloMcp.callTool("get_lists", boardId)

    // 2. Get cards from each list
    val allTasks = mutableListOf<TrelloTicketInfo>()
    for (list in lists) {
        val cards = trelloMcp.callTool("get_cards_by_list_id", list.id)

        // 3. Filter by priority label if specified
        val filtered = if (priority != null) {
            cards.filter { it.labels.contains(priority, ignoreCase = true) }
        } else {
            cards
        }

        allTasks.addAll(filtered)
    }

    return allTasks.take(maxResults)
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
  "trelloConnected": true,
  "githubConnected": true
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
        trelloMcpServerId = "trello",
        // GitHub settings - must be configured via .env
        defaultGithubOwner = System.getenv("GITHUB_DEFAULT_OWNER"),
        defaultGithubRepo = System.getenv("GITHUB_DEFAULT_REPO"),
        githubMcpServerId = "github"
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

## Task Workflow Integration

### Overview

The Task Workflow feature enables automated synchronization between Trello task management and GitHub branch/PR workflow. Users can start and complete tasks using natural language commands.

### Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     User Query                                │
│  "Выполняю задачу Task_123" / "Завершил Task_123"            │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                  TechSupportService                           │
│    isWorkflowQuery() → detects workflow commands              │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                  TaskWorkflowService                          │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  processWorkflow()                                      │  │
│  │    ├── extractTaskId()      → Fuzzy Task_N matching    │  │
│  │    ├── detectAction()       → START/COMPLETE/CANCEL    │  │
│  │    └── execute action:                                  │  │
│  │         ├── startTask()     → Create branch + move card │  │
│  │         ├── completeTask()  → Create PR + move card     │  │
│  │         └── cancelTask()    → Return card to ToDo       │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
          │                              │
          ▼                              ▼
┌─────────────────────┐      ┌─────────────────────────────────┐
│     Trello MCP      │      │          GitHub MCP             │
│  - move_card        │      │  - create_branch                │
│  - get_lists        │      │  - create_pull_request          │
│  - get_cards_by_id  │      │  - list_branches                │
│  - add_comment      │      │                                 │
└─────────────────────┘      └─────────────────────────────────┘
```

### Domain Models (`TaskWorkflowModels.kt`)

```kotlin
// Action types
enum class TaskAction {
    START,     // Start working on task
    COMPLETE,  // Complete task and create PR
    CANCEL     // Cancel task and return to ToDo
}

// Request model
data class TaskWorkflowRequest(
    val query: String,                    // User's natural language query
    val action: TaskAction? = null,       // Optional explicit action
    val taskId: String? = null,           // Optional explicit task ID
    val githubOwner: String? = null,      // Override GitHub owner
    val githubRepo: String? = null,       // Override GitHub repo
    val trelloBoardId: String? = null     // Override Trello board
)

// Result model
data class TaskWorkflowResult(
    val success: Boolean,
    val taskId: String,
    val action: TaskAction,
    val trelloResult: TrelloActionResult? = null,
    val githubResult: GithubActionResult? = null,
    val message: String,
    val errors: List<String> = emptyList(),
    val wasRolledBack: Boolean = false
)

// Trello operation result
data class TrelloActionResult(
    val success: Boolean,
    val cardId: String?,
    val cardName: String?,
    val fromList: String?,
    val toList: String?,
    val cardUrl: String?,
    val commentAdded: Boolean,
    val error: String? = null
)

// GitHub operation result
data class GithubActionResult(
    val success: Boolean,
    val branchName: String?,
    val branchCreated: Boolean,
    val branchAlreadyExists: Boolean,
    val prNumber: Int? = null,
    val prUrl: String? = null,
    val prCreated: Boolean = false,
    val error: String? = null
)
```

### TaskWorkflowService

**Key Methods:**

| Method | Description |
|--------|-------------|
| `processWorkflow()` | Main entry point, detects action and task ID |
| `startTask()` | Creates Git branch + moves card to InProgress |
| `completeTask()` | Creates PR to main + moves card to Review |
| `cancelTask()` | Returns card to ToDo |
| `extractTaskId()` | Fuzzy matching for Task_N in user query |
| `detectAction()` | Detects START/COMPLETE/CANCEL from keywords |
| `isWorkflowQuery()` | Checks if query is a workflow command |

**Workflow: startTask()**

```
1. Find Trello card by Task ID
2. Verify card is in valid list (Inbox/Backlog/ToDo)
3. Create Git branch feature/Task_N from main
4. Move card to InProgress
5. Add comment to card with branch name
```

**Workflow: completeTask()**

```
1. Find Trello card by Task ID
2. Verify card is in valid list (not already in Review/Done)
3. Create PR from feature/Task_N to main
4. Move card to Review
5. Add comment to card with PR link
```

### Fuzzy Matching

**Supported Task ID Formats:**
- `Task_123`, `task-123`, `Task 123`
- `#123`
- `задача 123`, `задачу №123`
- `тикет 123`

**START Action Keywords (Russian/English):**
- выполняю, начинаю, приступаю, беру в работу
- взял, взяла, начал, начала
- start, starting, taking, begin, working on

**COMPLETE Action Keywords:**
- завершил, завершила, закончил, закончила
- готов, готова, сделал, сделала
- finished, completed, done, ready

**CANCEL Action Keywords:**
- отменяю, отказываюсь, бросаю, отмена
- cancel, abort, drop

### Trello List Order

The service uses list order for validation:

```
1. Inbox      ─┐
2. Backlog    ─┼─ Can START from here
3. ToDo       ─┘
4. InProgress ─── Working state
5. Review     ─── After COMPLETE
6. Done       ─── Final state
```

### API Endpoint

**POST /api/v2/tech-support/workflow**

```json
// Request
{
  "query": "Выполняю задачу Task_123",
  "action": "START",           // optional
  "taskId": "Task_123",        // optional
  "githubOwner": "owner",      // optional
  "githubRepo": "repo",        // optional
  "trelloBoardId": "board-id"  // optional
}

// Response (success)
{
  "success": true,
  "taskId": "Task_123",
  "action": "START",
  "trelloResult": {
    "success": true,
    "cardId": "card-id",
    "cardName": "Task_123: Fix authentication bug",
    "fromList": "ToDo",
    "toList": "InProgress",
    "cardUrl": "https://trello.com/c/...",
    "commentAdded": true
  },
  "githubResult": {
    "success": true,
    "branchName": "feature/Task_123",
    "branchCreated": true,
    "branchAlreadyExists": false
  },
  "message": "Задача Task_123 взята в работу. Ветка feature/Task_123 создана."
}
```

### Error Handling

The service handles partial failures gracefully:

```kotlin
// If GitHub fails after Trello success
if (trelloResult.success && !githubResult.success) {
    // Report partial success, don't rollback Trello
    return TaskWorkflowResult(
        success = false,
        message = "Trello OK, but GitHub failed: ${githubResult.error}",
        trelloResult = trelloResult,
        githubResult = githubResult,
        errors = listOf(githubResult.error ?: "GitHub error")
    )
}
```

### Environment Variables

```env
# Required for Task Workflow
TRELLO_SUPPORT_BOARD_ID=board-id
GITHUB_DEFAULT_OWNER=owner
GITHUB_DEFAULT_REPO=repo
GITHUB_TOKEN=ghp_xxx
TRELLO_API_KEY=xxx
TRELLO_TOKEN=xxx
```

### Integration with TechSupportService

The workflow is triggered before AI classification:

```kotlin
// In TechSupportService.processRequest()
if (taskWorkflowService.isWorkflowQuery(request.query)) {
    return processWorkflowRequest(request, startTime)
}
// Otherwise, proceed with normal classification
```
