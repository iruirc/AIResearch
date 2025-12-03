# PR Review Architecture - AI-Powered Pull Request Analysis

## Executive Summary

This document outlines the architecture for an automated PR review system that leverages ResearchAI's existing infrastructure (RAG, MCP, AI providers) to analyze pull requests and provide intelligent code review feedback.

---

## 1. High-Level Architecture Overview

```
                                    +------------------+
                                    |   GitHub Action  |
                                    |   (Trigger)      |
                                    +--------+---------+
                                             |
                                             | HTTP Request
                                             v
+------------------+              +----------+-----------+
|                  |              |                      |
|   GitHub MCP     |<------------>|   ResearchAI API    |
|   Server         |   MCP        |   (Ktor Backend)    |
|   (51 tools)     |   Protocol   |                     |
+------------------+              +----------+-----------+
                                             |
                                             | Internal Calls
                    +------------------------+------------------------+
                    |                        |                        |
                    v                        v                        v
           +--------+--------+      +--------+--------+      +--------+--------+
           |                 |      |                 |      |                 |
           |  PR Review      |      |  RAG Service    |      |  AI Provider    |
           |  Service        |      |  (Context)      |      |  (Claude/etc)   |
           |                 |      |                 |      |                 |
           +-----------------+      +-----------------+      +-----------------+
```

### Architecture Principles

1. **Leverage Existing Components**: Use RAG for codebase context, MCP for GitHub interaction, existing AI providers
2. **Headless Operation**: Primary interface is API-based for CI/CD integration
3. **Extensibility**: Support multiple review modes and output formats
4. **Idempotency**: Same PR + same codebase state = same review result

---

## 2. Backend Components

### 2.1 New Domain Layer Components

#### PRReviewRequest Model
```kotlin
// com.researchai.domain.models.pr/PRReviewModels.kt

@Serializable
data class PRReviewRequest(
    val repositoryOwner: String,
    val repositoryName: String,
    val pullRequestNumber: Int,
    val reviewMode: ReviewMode = ReviewMode.STANDARD,
    val focusAreas: List<FocusArea> = FocusArea.entries,
    val providerId: ProviderType = ProviderType.CLAUDE,
    val model: String? = null,
    val ragDocumentIds: List<String>? = null,  // Specific RAG docs to use
    val maxFilesToReview: Int = 50,
    val includeLineComments: Boolean = true
)

@Serializable
enum class ReviewMode {
    QUICK,      // Fast review - summary only, no line comments
    STANDARD,   // Balanced - summary + key issues
    THOROUGH    // Deep analysis - full line-by-line review
}

@Serializable
enum class FocusArea {
    SECURITY,           // Security vulnerabilities
    PERFORMANCE,        // Performance issues
    CODE_STYLE,         // Style and conventions
    ARCHITECTURE,       // Architectural concerns
    TESTING,            // Test coverage
    DOCUMENTATION,      // Missing docs
    ERROR_HANDLING,     // Error handling patterns
    KOTLIN_IDIOMS       // Kotlin-specific best practices
}
```

#### PRReviewResult Model
```kotlin
@Serializable
data class PRReviewResult(
    val requestId: String,
    val pullRequestUrl: String,
    val summary: ReviewSummary,
    val fileReviews: List<FileReview>,
    val overallScore: Int,  // 0-100
    val metadata: ReviewMetadata
)

@Serializable
data class ReviewSummary(
    val overview: String,
    val criticalIssues: List<Issue>,
    val importantIssues: List<Issue>,
    val suggestions: List<Issue>,
    val positives: List<String>
)

@Serializable
data class FileReview(
    val filePath: String,
    val changeType: ChangeType,  // ADDED, MODIFIED, DELETED, RENAMED
    val lineComments: List<LineComment>,
    val fileSummary: String?
)

@Serializable
data class LineComment(
    val lineNumber: Int,
    val side: DiffSide,  // LEFT (old), RIGHT (new)
    val severity: Severity,
    val category: FocusArea,
    val message: String,
    val suggestedFix: String?
)

@Serializable
enum class Severity {
    CRITICAL,   // Must fix - security, data loss
    WARNING,    // Should fix - bugs, bad practices
    INFO        // Nice to have - style, suggestions
}

@Serializable
data class Issue(
    val severity: Severity,
    val category: FocusArea,
    val title: String,
    val description: String,
    val affectedFiles: List<String>,
    val suggestedAction: String?
)

@Serializable
data class ReviewMetadata(
    val reviewDurationMs: Long,
    val filesReviewed: Int,
    val linesAnalyzed: Int,
    val ragContextUsed: Boolean,
    val ragChunksRetrieved: Int,
    val tokensUsed: Int,
    val model: String,
    val provider: String
)
```

### 2.2 PR Review Service

```kotlin
// com.researchai.services/PRReviewService.kt

class PRReviewService(
    private val mcpServerManager: MCPServerManager,
    private val ragManager: RAGManager,
    private val aiProviderFactory: AIProviderFactory,
    private val configRepository: ConfigRepository,
    private val assistantManager: AssistantManager
) {
    companion object {
        const val GITHUB_MCP_SERVER_ID = "github"
        const val PR_REVIEWER_ASSISTANT_ID = "pr-reviewer"
    }

    /**
     * Execute a full PR review
     */
    suspend fun reviewPullRequest(request: PRReviewRequest): Result<PRReviewResult> {
        val startTime = System.currentTimeMillis()

        return runCatching {
            // 1. Fetch PR data via MCP
            val prData = fetchPRData(request)

            // 2. Gather RAG context
            val ragContext = gatherRAGContext(prData, request)

            // 3. Build review prompt
            val reviewPrompt = buildReviewPrompt(prData, ragContext, request)

            // 4. Execute AI review
            val aiResponse = executeAIReview(reviewPrompt, request)

            // 5. Parse and structure response
            val result = parseReviewResponse(aiResponse, prData, request)

            // 6. Add metadata
            result.copy(
                metadata = result.metadata.copy(
                    reviewDurationMs = System.currentTimeMillis() - startTime
                )
            )
        }
    }

    private suspend fun fetchPRData(request: PRReviewRequest): PRData {
        val githubClient = mcpServerManager.getClient(GITHUB_MCP_SERVER_ID)
            ?: throw IllegalStateException("GitHub MCP server not connected")

        // Fetch PR details
        val prDetailsResult = githubClient.callTool(
            "get_pull_request",
            buildJsonObject {
                put("owner", request.repositoryOwner)
                put("repo", request.repositoryName)
                put("pull_number", request.pullRequestNumber)
            }
        )

        // Fetch PR diff
        val diffResult = githubClient.callTool(
            "get_pull_request_diff",
            buildJsonObject {
                put("owner", request.repositoryOwner)
                put("repo", request.repositoryName)
                put("pull_number", request.pullRequestNumber)
            }
        )

        // Fetch changed files
        val filesResult = githubClient.callTool(
            "get_pull_request_files",
            buildJsonObject {
                put("owner", request.repositoryOwner)
                put("repo", request.repositoryName)
                put("pull_number", request.pullRequestNumber)
            }
        )

        return PRData(
            details = parsePRDetails(prDetailsResult),
            diff = parseDiff(diffResult),
            changedFiles = parseChangedFiles(filesResult)
        )
    }

    private suspend fun gatherRAGContext(
        prData: PRData,
        request: PRReviewRequest
    ): RAGContext {
        // Build search queries from changed files and PR description
        val queries = buildRAGQueries(prData)

        val contextChunks = mutableListOf<SearchResult>()

        for (query in queries) {
            val results = ragManager.searchRelevantContext(
                query = query,
                topK = 5,
                minScore = 0.6f
            )
            contextChunks.addAll(results)
        }

        // Deduplicate and rank
        return RAGContext(
            chunks = contextChunks
                .distinctBy { it.documentId to it.chunkIndex }
                .sortedByDescending { it.score }
                .take(20)
        )
    }

    private fun buildRAGQueries(prData: PRData): List<String> {
        val queries = mutableListOf<String>()

        // Query from PR title and description
        queries.add(prData.details.title)
        prData.details.body?.let { queries.add(it.take(500)) }

        // Query from changed file paths (to find related code)
        prData.changedFiles
            .map { it.filename }
            .distinct()
            .take(10)
            .forEach { filename ->
                queries.add("implementation of $filename")
            }

        return queries
    }
}
```

### 2.3 PR Review Routes

```kotlin
// com.researchai.routes/PRReviewRoutes.kt

fun Route.prReviewRoutes(prReviewService: PRReviewService) {
    route("/pr-review") {

        // POST /pr-review - Execute PR review
        post {
            try {
                val request = call.receive<PRReviewRequest>()

                // Validate request
                if (request.repositoryOwner.isBlank() || request.repositoryName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse("Repository owner and name are required"))
                    return@post
                }

                if (request.pullRequestNumber <= 0) {
                    call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse("Valid pull request number is required"))
                    return@post
                }

                val result = prReviewService.reviewPullRequest(request)

                result.onSuccess { review ->
                    call.respond(HttpStatusCode.OK, review)
                }.onFailure { error ->
                    call.respond(HttpStatusCode.InternalServerError,
                        ErrorResponse(error.message ?: "Review failed"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error"))
            }
        }

        // POST /pr-review/stream - Execute PR review with SSE progress
        post("/stream") {
            // SSE streaming implementation for long-running reviews
            // Similar to RAG document streaming
        }

        // POST /pr-review/comment - Post review as GitHub comment
        post("/comment") {
            try {
                val request = call.receive<PostCommentRequest>()
                val result = prReviewService.postReviewAsComment(request)
                // ...
            } catch (e: Exception) {
                // ...
            }
        }

        // GET /pr-review/status/{requestId} - Get review status
        get("/status/{requestId}") {
            // For async review tracking
        }
    }
}
```

### 2.4 PR Reviewer Assistant

Create a dedicated assistant for PR reviews with optimized system prompt:

```kotlin
// System assistant to be added to AssistantManager

val prReviewerAssistant = Assistant(
    id = "pr-reviewer",
    name = "PR Reviewer",
    systemPrompt = """
You are an expert code reviewer specializing in Kotlin backend development. Your role is to review pull requests and provide actionable, specific feedback.

## Review Guidelines

### Priority Order
1. Security vulnerabilities (authentication, injection, data exposure)
2. Logic errors and potential bugs
3. Performance issues
4. Error handling gaps
5. Code maintainability
6. Style and conventions

### Kotlin-Specific Focus
- Null safety usage (prefer `?.let`, avoid `!!`)
- Coroutine patterns (structured concurrency, proper scope usage)
- Sealed class usage for state modeling
- Extension function opportunities
- Data class immutability

### Output Format
Structure your response as JSON with the following schema:
{
  "summary": {
    "overview": "2-3 sentence summary",
    "criticalIssues": [...],
    "importantIssues": [...],
    "suggestions": [...],
    "positives": [...]
  },
  "fileReviews": [
    {
      "filePath": "...",
      "lineComments": [
        {
          "lineNumber": 42,
          "side": "RIGHT",
          "severity": "WARNING",
          "category": "SECURITY",
          "message": "...",
          "suggestedFix": "..."
        }
      ]
    }
  ],
  "overallScore": 75
}

### Scoring Guidelines
- 90-100: Excellent, ready to merge
- 70-89: Good, minor issues only
- 50-69: Needs work, important issues to address
- Below 50: Significant concerns, requires major changes
""",
    description = "AI-powered PR reviewer for Kotlin codebases",
    isSystem = true
)
```

---

## 3. Frontend/CLI Approach Recommendation

### Recommendation: CLI-First with Optional Web Dashboard

**Primary Interface: CLI Command**

Rationale:
1. CI/CD integration requires headless operation
2. Developers prefer command-line tools in their workflow
3. Existing CLI infrastructure (`researchai-cli`) is already in place
4. Can be easily integrated into any CI pipeline

**Secondary Interface: Web Dashboard (Phase 2)**

For:
- Review history browsing
- Team analytics
- Configuration management

### 3.1 CLI Implementation

```kotlin
// researchai-cli/src/main/kotlin/com/researchai/cli/commands/ReviewCommand.kt

class ReviewCommand : CliktCommand(
    name = "review",
    help = "Review a pull request using AI"
) {
    private val prUrl by argument(
        "pr-url",
        help = "GitHub PR URL (e.g., https://github.com/owner/repo/pull/123)"
    )

    private val mode by option(
        "--mode", "-m",
        help = "Review mode: quick, standard, thorough"
    ).enum<ReviewMode>().default(ReviewMode.STANDARD)

    private val focus by option(
        "--focus", "-f",
        help = "Focus areas (comma-separated): security,performance,style,architecture"
    ).split(",")

    private val output by option(
        "--output", "-o",
        help = "Output format: text, json, github"
    ).enum<OutputFormat>().default(OutputFormat.TEXT)

    private val postComment by option(
        "--post-comment",
        help = "Post review as GitHub PR comment"
    ).flag()

    private val serverUrl by option(
        "--server", "-s",
        help = "ResearchAI server URL (optional, defaults to RESEARCHAI_SERVER_URL env var)"
    )

    override fun run() = runBlocking {
        val (owner, repo, prNumber) = parsePRUrl(prUrl)

        // Priority: 1. --server flag, 2. RESEARCHAI_SERVER_URL env var, 3. config file, 4. default
        val client = ResearchAiClient(serverUrl ?: loadConfig().serverUrl)

        echo("Reviewing PR #$prNumber in $owner/$repo...")
        echo("Mode: $mode")

        try {
            val request = PRReviewRequest(
                repositoryOwner = owner,
                repositoryName = repo,
                pullRequestNumber = prNumber,
                reviewMode = mode,
                focusAreas = focus?.mapNotNull {
                    FocusArea.entries.find { area ->
                        area.name.equals(it, ignoreCase = true)
                    }
                } ?: FocusArea.entries
            )

            val result = client.reviewPR(request) { progress ->
                echo("\r${progress.phase}: ${progress.percent}%", trailingNewline = false)
            }

            // Output formatting
            when (output) {
                OutputFormat.TEXT -> printTextReview(result)
                OutputFormat.JSON -> echo(json.encodeToString(result))
                OutputFormat.GITHUB -> printGitHubMarkdown(result)
            }

            // Optionally post comment
            if (postComment) {
                client.postReviewComment(result)
                echo("\nReview posted as PR comment")
            }

        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
            throw e
        } finally {
            client.close()
        }
    }
}
```

### 3.2 CLI Usage Examples

```bash
# Basic review
rai review https://github.com/owner/repo/pull/123

# Thorough review with specific focus
rai review https://github.com/owner/repo/pull/123 \
    --mode thorough \
    --focus security,performance

# Output as GitHub markdown
rai review https://github.com/owner/repo/pull/123 --output github

# Review and post comment
rai review https://github.com/owner/repo/pull/123 --post-comment

# JSON output for CI integration
rai review https://github.com/owner/repo/pull/123 --output json > review.json
```

---

## 4. Integration Points with Existing System

### 4.1 MCP Integration

The GitHub MCP server (already configured in `config/mcp-servers.json`) provides:

| Tool | Purpose |
|------|---------|
| `get_pull_request` | Fetch PR metadata |
| `get_pull_request_diff` | Get unified diff |
| `get_pull_request_files` | List changed files |
| `get_file_contents` | Fetch full file content |
| `create_pull_request_review` | Post review comments |
| `create_issue_comment` | Post PR comments |

### 4.2 RAG Integration

Existing RAG system provides codebase context:

```kotlin
// Use existing RAGManager for context retrieval
val context = ragManager.searchRelevantContext(
    query = "authentication middleware implementation",
    topK = 10,
    minScore = 0.6f
)
```

Context is used to:
1. Understand project conventions
2. Find related code patterns
3. Identify architectural decisions
4. Check for similar implementations

### 4.3 AI Provider Integration

Leverage existing multi-provider architecture:

```kotlin
// Use SendMessageUseCase with PR-specific parameters
val result = sendMessageUseCase(
    message = reviewPrompt,
    sessionId = null,  // Stateless review
    providerId = request.providerId,
    model = request.model,
    parameters = RequestParameters(
        temperature = 0.3,  // Lower for consistent reviews
        maxTokens = 8192
    )
)
```

### 4.4 Assistant Integration

PR reviewer assistant provides specialized system prompt:

```kotlin
val assistant = assistantManager.getAssistant("pr-reviewer")
val systemPrompt = assistant?.systemPrompt
```

---

## 5. CI/CD Pipeline Design

### 5.1 GitHub Action Workflow

```yaml
# .github/workflows/pr-review.yml

name: AI PR Review

on:
  pull_request:
    types: [opened, synchronize, reopened]

jobs:
  ai-review:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup ResearchAI CLI
        run: |
          curl -sSL https://researchai.example.com/install.sh | bash
          echo "$HOME/.researchai/bin" >> $GITHUB_PATH

      - name: Run AI Review
        id: review
        env:
          RESEARCHAI_SERVER_URL: ${{ secrets.RESEARCHAI_SERVER_URL }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          # CLI reads RESEARCHAI_SERVER_URL from environment
          rai review ${{ github.event.pull_request.html_url }} \
            --mode standard \
            --output json > review.json

          # Extract score for status check
          SCORE=$(jq '.overallScore' review.json)
          echo "score=$SCORE" >> $GITHUB_OUTPUT

      - name: Post Review Comment
        if: always()
        env:
          RESEARCHAI_SERVER_URL: ${{ secrets.RESEARCHAI_SERVER_URL }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          rai review ${{ github.event.pull_request.html_url }} \
            --post-comment

      - name: Check Quality Gate
        run: |
          SCORE=${{ steps.review.outputs.score }}
          if [ "$SCORE" -lt 50 ]; then
            echo "PR review score ($SCORE) below threshold (50)"
            exit 1
          fi
```

### 5.2 Alternative: Direct API Integration

For custom CI systems:

```bash
#!/bin/bash
# ci-review.sh

PR_URL="$1"
SERVER_URL="${RESEARCHAI_SERVER_URL:-http://localhost:8080}"

# Extract PR info from URL
OWNER=$(echo $PR_URL | sed -n 's|.*/\([^/]*\)/[^/]*/pull/.*|\1|p')
REPO=$(echo $PR_URL | sed -n 's|.*/[^/]*/\([^/]*\)/pull/.*|\1|p')
PR_NUM=$(echo $PR_URL | sed -n 's|.*/pull/\([0-9]*\).*|\1|p')

# Call ResearchAI API
RESPONSE=$(curl -s -X POST "$SERVER_URL/pr-review" \
  -H "Content-Type: application/json" \
  -d "{
    \"repositoryOwner\": \"$OWNER\",
    \"repositoryName\": \"$REPO\",
    \"pullRequestNumber\": $PR_NUM,
    \"reviewMode\": \"STANDARD\"
  }")

# Process response
echo "$RESPONSE" | jq '.'

# Exit with error if critical issues found
CRITICAL_COUNT=$(echo "$RESPONSE" | jq '.summary.criticalIssues | length')
if [ "$CRITICAL_COUNT" -gt 0 ]; then
  echo "Critical issues found: $CRITICAL_COUNT"
  exit 1
fi
```

### 5.3 Pipeline Architecture

```
+-------------------+     +------------------+     +------------------+
|   GitHub PR       |     |   GitHub Action  |     |   ResearchAI     |
|   Event           |---->|   Workflow       |---->|   Server         |
|                   |     |                  |     |                  |
+-------------------+     +--------+---------+     +--------+---------+
                                   |                        |
                                   |                        v
                          +--------v---------+     +--------+---------+
                          |   Review Result  |<----|   AI Analysis    |
                          |   (JSON)         |     |   + RAG Context  |
                          +--------+---------+     +------------------+
                                   |
                          +--------v---------+
                          |   Quality Gate   |
                          |   (Pass/Fail)    |
                          +--------+---------+
                                   |
                          +--------v---------+
                          |   Post Comment   |
                          |   to PR          |
                          +------------------+
```

---

## 6. Data Flow Diagram

### 6.1 Complete Review Flow

```
1. TRIGGER
   GitHub PR Event
         |
         v
2. ORCHESTRATION
   +------------------+
   | GitHub Action    |
   | - Parse PR URL   |
   | - Call CLI/API   |
   +--------+---------+
            |
            v
3. DATA GATHERING (Parallel)
   +------------------+     +------------------+
   | MCP: GitHub      |     | RAG: Codebase    |
   | - PR details     |     | - Related code   |
   | - Diff           |     | - Patterns       |
   | - Files          |     | - Conventions    |
   +--------+---------+     +--------+---------+
            |                        |
            +----------+-------------+
                       |
                       v
4. PROMPT CONSTRUCTION
   +------------------+
   | Build Review     |
   | Prompt           |
   | - System prompt  |
   | - PR data        |
   | - RAG context    |
   | - Focus areas    |
   +--------+---------+
            |
            v
5. AI ANALYSIS
   +------------------+
   | AI Provider      |
   | (Claude/OpenAI)  |
   | - Code review    |
   | - Issue finding  |
   | - Suggestions    |
   +--------+---------+
            |
            v
6. RESPONSE PROCESSING
   +------------------+
   | Parse & Structure|
   | - JSON parsing   |
   | - Validation     |
   | - Scoring        |
   +--------+---------+
            |
            v
7. OUTPUT
   +------------------+     +------------------+
   | CLI Output       |     | GitHub Comment   |
   | - Text/JSON/MD   |     | - Review         |
   +------------------+     | - Line comments  |
                            +------------------+
```

### 6.2 Context Assembly Flow

```
PR Diff                      RAG Search
   |                             |
   v                             v
+--------+                 +----------+
| Parse  |                 | Query    |
| Files  |                 | Builder  |
+---+----+                 +----+-----+
    |                           |
    v                           v
+--------+                 +----------+
| File   |                 | Vector   |
| Names  |                 | Search   |
+---+----+                 +----+-----+
    |                           |
    +-------------+-------------+
                  |
                  v
           +------+------+
           | Context     |
           | Assembler   |
           +------+------+
                  |
                  v
           +------+------+
           | Priority    |
           | Ranker      |
           +------+------+
                  |
                  v
           Final Context
           (Code samples,
            patterns,
            conventions)
```

---

## 7. Key Technical Considerations and Trade-offs

### 7.1 Token Budget Management

**Challenge**: Large PRs can exceed context window limits.

**Strategy**:
```kotlin
class TokenBudgetManager(
    private val maxTokens: Int = 100_000  // Claude 3 context
) {
    fun allocateBudget(): TokenBudget {
        return TokenBudget(
            systemPrompt = 2_000,
            ragContext = 20_000,
            prDiff = 60_000,
            reservedForResponse = 18_000
        )
    }

    fun prioritizeFiles(
        files: List<ChangedFile>,
        maxTokens: Int
    ): List<ChangedFile> {
        // Priority: Kotlin > Config > Docs > Tests
        // Truncate large files
        // Skip binary files
    }
}
```

### 7.2 Review Consistency

**Trade-off**: Temperature setting affects consistency vs. creativity.

**Recommendation**:
- Use `temperature = 0.2-0.3` for consistent reviews
- Use `temperature = 0.7` for creative suggestions

### 7.3 Rate Limiting

**Challenge**: Multiple PRs can trigger simultaneous reviews.

**Solutions**:
1. Queue-based processing with job scheduler
2. GitHub Action concurrency limits
3. Rate limiting at API level

```kotlin
// Rate limiting configuration
@Serializable
data class RateLimitConfig(
    val maxConcurrentReviews: Int = 5,
    val maxReviewsPerHour: Int = 50,
    val maxTokensPerHour: Int = 1_000_000
)
```

### 7.4 Security Considerations

| Concern | Mitigation |
|---------|------------|
| API Key exposure | Use GitHub secrets, never log keys |
| Code injection | Sanitize PR content before prompt |
| Sensitive code | Optional sensitive file exclusion |
| Audit trail | Log all reviews (without code content) |

### 7.5 Cost Optimization

**Strategies**:
1. Use cheaper models for quick reviews
2. Cache RAG context across similar files
3. Implement review result caching (same diff = same review)
4. Tiered review modes (quick for draft PRs)

```kotlin
fun selectOptimalModel(request: PRReviewRequest): String {
    return when (request.reviewMode) {
        ReviewMode.QUICK -> "claude-haiku-4-5-20251001"
        ReviewMode.STANDARD -> "claude-sonnet-4-20250514"
        ReviewMode.THOROUGH -> "claude-sonnet-4-20250514"
    }
}
```

### 7.6 Error Handling

```kotlin
sealed class PRReviewError : Exception() {
    data class MCPConnectionError(val serverId: String) : PRReviewError()
    data class PRNotFound(val prNumber: Int) : PRReviewError()
    data class RateLimitExceeded(val retryAfter: Long) : PRReviewError()
    data class TokenLimitExceeded(val required: Int, val max: Int) : PRReviewError()
    data class AIProviderError(val message: String) : PRReviewError()
    data class InvalidResponse(val rawResponse: String) : PRReviewError()
}
```

---

## 8. Implementation Phases

### Phase 1: MVP (2-3 weeks)

**Goal**: Basic PR review via CLI

**Deliverables**:
- [ ] PRReviewService with MCP integration
- [ ] PRReviewRoutes API endpoint
- [ ] CLI `review` command (basic)
- [ ] Text output format
- [ ] PR reviewer assistant

**Technical Tasks**:
1. Create domain models (`PRReviewModels.kt`)
2. Implement `PRReviewService`
3. Add `/pr-review` route
4. Add `ReviewCommand` to CLI
5. Create system assistant

### Phase 2: RAG Integration (1-2 weeks)

**Goal**: Context-aware reviews using RAG

**Deliverables**:
- [ ] RAG context gathering
- [ ] Intelligent query building
- [ ] Context prioritization

**Technical Tasks**:
1. Implement `gatherRAGContext()`
2. Build query generator from PR data
3. Context ranking and deduplication

### Phase 3: GitHub Integration (1-2 weeks)

**Goal**: Full GitHub workflow integration

**Deliverables**:
- [ ] GitHub Action workflow
- [ ] Line-level comments
- [ ] Review summary posting
- [ ] Quality gate checks

**Technical Tasks**:
1. Implement `postReviewAsComment()`
2. Create GitHub Action workflow
3. Line comment mapping
4. Status check integration

### Phase 4: Advanced Features (2-3 weeks)

**Goal**: Production-ready with advanced capabilities

**Deliverables**:
- [ ] SSE streaming for long reviews
- [ ] Review history/caching
- [ ] Team analytics dashboard
- [ ] Custom rule definitions
- [ ] Multiple output formats

**Technical Tasks**:
1. SSE streaming endpoint
2. Review persistence layer
3. Web dashboard (optional)
4. Custom focus area definitions

### Phase 5: Scale & Optimize (Ongoing)

**Goal**: Production hardening

**Deliverables**:
- [ ] Rate limiting
- [ ] Cost optimization
- [ ] Monitoring/alerting
- [ ] A/B testing different models

---

## 9. File Structure

```
src/main/kotlin/com/researchai/
├── domain/
│   ├── models/
│   │   └── pr/
│   │       └── PRReviewModels.kt
│   └── usecase/
│       └── PRReviewUseCase.kt
├── services/
│   └── PRReviewService.kt
├── routes/
│   └── PRReviewRoutes.kt
└── di/
    └── AppModule.kt  (add PR review dependencies)

researchai-cli/src/main/kotlin/com/researchai/cli/
├── commands/
│   └── ReviewCommand.kt
├── handlers/
│   └── ReviewHandler.kt
└── output/
    └── ReviewOutputFormatter.kt
```

---

## 10. Summary

This architecture leverages ResearchAI's existing strengths:

1. **MCP** - GitHub API interaction via established protocol
2. **RAG** - Codebase context for informed reviews
3. **Multi-Provider AI** - Flexibility in model selection
4. **CLI Infrastructure** - Ready for CI/CD integration
5. **Assistant System** - Specialized review prompts

The CLI-first approach ensures immediate CI/CD compatibility while maintaining extensibility for future web dashboard development.

**Key Success Metrics**:
- Review latency < 2 minutes for standard PRs
- False positive rate < 20%
- Developer satisfaction > 80%
- Cost per review < $0.10 (average)
