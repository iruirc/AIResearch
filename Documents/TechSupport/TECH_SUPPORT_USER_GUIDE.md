# Tech Support AI Assistant - User Guide

## Introduction

Tech Support AI Assistant helps answer technical questions by combining:
- Documentation and FAQ database (RAG)
- Trello bug tracking integration
- **GitHub Integration**: Repository info (branches, commits, issues, PRs)
- AI-powered intelligent responses
- **Project Management**: Task prioritization and project status

## Getting Started

### Prerequisites

1. **Server running**: ResearchAI server must be started
2. **Trello configured** (optional): For ticket integration, configure Trello MCP server
3. **GitHub configured** (optional): For repository info, configure GitHub MCP server
4. **RAG indexed** (optional): Index documentation for knowledge base search

### Configuration

Set environment variables in `.env`:

```bash
# Trello integration (optional)
TRELLO_SUPPORT_BOARD_ID=your-board-id
TRELLO_API_KEY=your-api-key
TRELLO_TOKEN=your-token

# GitHub integration (optional)
GH_DEFAULT_OWNER=your-github-owner
GH_DEFAULT_REPO=your-repo-name
GITHUB_TOKEN=ghp_your_token
```

### Trello Defaults

| Operation | Default Value | Override |
|-----------|---------------|----------|
| **Ticket Search** | All boards accessible to token | `trelloBoardId` in request |
| **Ticket Creation - Board** | `TRELLO_SUPPORT_BOARD_ID` env variable | `boardId` in request |
| **Ticket Creation - List** | Depends on query type (see below) | `listName` in request |

**Automatic List Selection by Query Type:**

| Query Type | Default List | Labels |
|------------|--------------|--------|
| `BUG_REPORT` | `Bugs` | bug, needs-triage |
| `FEATURE_REQUEST` | `Ideas` | feature-request, needs-review |
| `GENERAL` / Other | `Inbox` | support, needs-triage |

> **Note:** Ensure the board specified in `TRELLO_SUPPORT_BOARD_ID` has the required lists ("Inbox", "Bugs", "Ideas"), or provide a custom `listName` when creating tickets.

### GitHub Defaults

| Operation | Default Value | Override |
|-----------|---------------|----------|
| **Repository Owner** | `GH_DEFAULT_OWNER` env variable | `githubOwner` in request |
| **Repository Name** | `GH_DEFAULT_REPO` env variable | `githubRepo` in request |

> **Note:** GitHub integration requires `GITHUB_TOKEN` environment variable for authentication.

## User Scenarios

### Scenario 1: Asking a Technical Question

**User Story**: As a developer, I want to ask how authentication works so I can integrate it correctly.

**Web UI:**
1. Enable Tech Support mode (click sidebar button)
2. Type: "How do I configure JWT authentication?"
3. View response with documentation references
4. Check "Sources Used" panel for referenced docs

**CLI:**
```bash
rai support "How do I configure JWT authentication?"
```

**API:**
```bash
curl -X POST http://localhost:8080/api/v2/tech-support \
  -H "Content-Type: application/json" \
  -d '{"query": "How do I configure JWT authentication?"}'
```

**Expected Response:**
```
Query Type: [HOW-TO]

Answer:
To configure JWT authentication, you need to set the following
environment variables:
- JWT_SECRET: Your secret key for signing tokens
- JWT_ISSUER: Token issuer identifier
- JWT_AUDIENCE: Expected audience claim

[Referenced from: authentication.md]

Sources Used:
  Documentation (2):
    - authentication.md
    - faq/general.md
```

---

### Scenario 2: Reporting a Bug

**User Story**: As a user, I want to report that login is failing so the team can investigate.

**Web UI:**
1. Enable Tech Support mode
2. Type: "Login is failing with error 401 after updating to version 1.5"
3. AI classifies as BUG_REPORT
4. Check "Suggested Actions" for "Create Ticket" option
5. Click to create Trello ticket

**CLI:**
```bash
rai support "Login is failing with error 401 after updating to version 1.5"
```

**Expected Response:**
```
Query Type: [BUG]

Answer:
I understand you're experiencing login failures with a 401 error
after updating. This could be related to a known issue with token
validation in version 1.5.

Related Tickets:
  - AUTH-123: "401 errors after v1.5 update" (In Progress)

Suggested Actions:
  1. 📝 Create Ticket:
     Title: Bug: Login failure - 401 error after v1.5 update
     List: Bugs
     Labels: [bug, needs-triage]
     Description: User reports login failures...

  2. 👀 View Related Ticket:
     Ticket: AUTH-123 - 401 errors after v1.5 update
     Reason: Similar issue reported by other users
```

---

### Scenario 3: Checking Issue Status

**User Story**: As a user, I want to check the status of my reported bug.

**Web UI:**
1. Enable Tech Support mode
2. Type: "What's the status of the OAuth redirect issue I reported?"
3. AI searches Trello for matching tickets
4. View status in "Related Tickets" panel

**CLI:**
```bash
rai support "What's the status of the OAuth redirect issue?"
```

**Expected Response (ticket found):**
```
Query Type: [STATUS]

Answer:
I found a ticket that matches your query about OAuth redirect issues.

Related Tickets:
  - OAUTH-456: "OAuth redirect mismatch error"
    Status: In Progress
    Labels: [bug, oauth, high-priority]
    URL: https://trello.com/c/xyz789

The team is currently working on this issue. It's marked as
high-priority and is expected to be resolved in the next release.

Suggested Actions:
  1. 👀 View Ticket: OAUTH-456 - OAuth redirect mismatch error
```

**Expected Response (ticket not found):**
```
Query Type: [STATUS]

Answer:
I couldn't find a ticket matching your query about OAuth redirect issues.
It's possible the ticket was renamed or closed.

Suggested Actions:
  1. 📧 Contact Support
     Reason: Unable to find ticket matching your query
     Channel: email
```

---

### Scenario 4: Requesting a Feature

**User Story**: As a developer, I want to suggest adding SAML support.

**CLI:**
```bash
rai support "Can you add SAML authentication support?"
```

**Expected Response:**
```
Query Type: [FEATURE]

Answer:
Thank you for your feature request for SAML authentication support.
Currently, ResearchAI supports JWT and OAuth authentication methods.

I can create a feature request ticket for the development team.

Suggested Actions:
  1. 📝 Create Ticket:
     Title: Feature: SAML Authentication Support
     List: Ideas
     Labels: [feature-request, needs-review]
     Description: User requests SAML authentication support for
                  enterprise SSO integration
```

---

### Scenario 5: Troubleshooting with Documentation

**User Story**: As a user, I'm getting timeout errors and need help.

**CLI:**
```bash
rai support "My API requests keep timing out after 30 seconds"
```

**Expected Response:**
```
Query Type: [HOW-TO]

Answer:
Timeout issues are typically caused by request timeout settings.
By default, the HTTP client has a 5-minute timeout.

Based on the documentation:
1. Check your client-side timeout settings
2. For long-running requests, increase timeout:
   - Set CLAUDE_TIMEOUT environment variable
   - Or configure per-request timeout in your client

Related Known Issues:
  - ISSUE-001: MCP Server Connection Timeout on Cold Start
    This affects first requests after server restart.
    Workaround: Retry after 5-10 seconds.

Sources Used:
  Documentation (3):
    - faq/general.md
    - configuration.md
    - known-issues/current.md

Suggested Actions:
  1. 📖 Add to FAQ
     Question: My API requests keep timing out after 30 seconds
     Category: how-to
```

---

### Scenario 5.1: Escalating Complex Issues

**User Story**: As a user, I have a unique problem that isn't covered in documentation.

**CLI:**
```bash
rai support "My custom OAuth provider returns a weird error code 0x4F2A"
```

**Expected Response (no RAG or Trello context):**
```
Query Type: [GENERAL]

Answer:
I don't have specific information about error code 0x4F2A for custom
OAuth providers. This appears to be a vendor-specific error.

Suggested Actions:
  1. 📢 Escalate to Support
     Reason: No relevant documentation or tickets found
     Priority: normal
     Team: support

  2. 📝 Create Ticket:
     Title: Support: Custom OAuth error 0x4F2A
     List: Inbox
     Labels: [support, needs-triage]
```

---

### Scenario 6: Continuing a Support Session

**User Story**: I want to continue my previous support conversation.

**CLI:**
```bash
# First question
rai support --session my-support-123 "How do I enable RAG?"

# Follow-up question (same session)
rai support --session my-support-123 "What embedding model does it use?"
```

**API:**
```json
{
  "query": "What embedding model does it use?",
  "sessionId": "my-support-123"
}
```

---

### Scenario 7: Disabling RAG or Trello

**User Story**: I only want to search documentation, not Trello tickets.

**CLI:**
```bash
# Only RAG, no Trello
rai support --no-trello "How does compression work?"

# Only Trello, no RAG
rai support --no-rag "Show me recent auth bugs"

# Neither (direct AI question)
rai support --no-rag --no-trello "Explain OAuth 2.0 flow"
```

**API:**
```json
{
  "query": "How does compression work?",
  "includeTrello": false,
  "includeRag": true
}
```

---

### Scenario 8: Project Management - Task Prioritization

**User Story**: As a developer, I want to see high priority tasks and get recommendations on what to do first.

**CLI:**
```bash
# Show high priority tasks
rai support --priority high

# Get AI recommendations on task order
rai support --recommend

# Show project status summary
rai support --status

# Combined query
rai support "Show me high priority tasks and recommend what to do first"
```

**Expected Response:**
```
Query Type: [PROJECT]

Answer:
Based on the current tasks with high priority, I recommend the following order:

1. **AUTH-789: Fix token refresh logic** - This is blocking other features
   and affects user experience directly.

2. **API-456: Add rate limiting** - Security improvement that should be
   done before the upcoming release.

3. **DB-123: Optimize queries** - Performance improvement, can be done
   after critical issues are resolved.

Suggested Actions:
  1. 📋 Tasks (high): 3 total
     [!] 1. AUTH-789: Fix token refresh logic
         Status: To Do
         URL: https://trello.com/c/auth789

     [!] 2. API-456: Add rate limiting
         Status: In Progress
         URL: https://trello.com/c/api456

     [!] 3. DB-123: Optimize queries
         Status: To Do
         URL: https://trello.com/c/db123

  2. 🎯 AI Recommendations:
     #1. AUTH-789: Fix token refresh logic
         Reason: Recommended by AI analysis
     #2. API-456: Add rate limiting
         Reason: Recommended by AI analysis
     #3. DB-123: Optimize queries
         Reason: Recommended by AI analysis

  3. 👀 View Ticket: AUTH-789 - Fix token refresh logic
     URL: https://trello.com/c/auth789
```

---

### Scenario 9: Project Management - Project Status

**User Story**: As a project manager, I want to see an overview of the project status.

**CLI:**
```bash
rai support --status
# or
rai support "What is the project status?"
```

**Expected Response:**
```
Query Type: [PROJECT]

Answer:
Here's the current project status:

**Summary:**
- Total Tasks: 24
- High Priority: 5 (21%)
- Medium Priority: 12 (50%)
- Low Priority: 7 (29%)

**By Status:**
- To Do: 10 tasks
- In Progress: 8 tasks
- Review: 4 tasks
- Done: 2 tasks

**Recommendations:**
- 5 high priority tasks need attention
- Consider completing the 4 tasks in Review before starting new work

Suggested Actions:
  1. 📊 Project Status:
     Total Tasks: 24
     By Priority:
       - high: 5
       - medium: 12
       - low: 7
     By Status:
       - To Do: 10
       - In Progress: 8
       - Review: 4
       - Done: 2
```

---

### Scenario 10: Project Management - Filter by Priority

**User Story**: I want to see all tasks with a specific priority level.

**CLI:**
```bash
# High priority tasks
rai support --priority high

# Medium priority tasks
rai support --priority medium

# Low priority tasks
rai support --priority low
```

**Expected Response (--priority medium):**
```
Query Type: [PROJECT]

Answer:
Here are the medium priority tasks:

1. FEAT-001: Add dark mode support
2. REFACTOR-002: Clean up legacy code
3. DOCS-003: Update API documentation
...

Suggested Actions:
  1. 📋 Tasks (medium): 12 total
     [~] 1. FEAT-001: Add dark mode support
         Status: In Progress

     [~] 2. REFACTOR-002: Clean up legacy code
         Status: To Do

     [~] 3. DOCS-003: Update API documentation
         Status: To Do
     ...
```

---

### Scenario 11: GitHub - Viewing Repository Branches

**User Story**: As a developer, I want to see all branches in the repository.

**CLI:**
```bash
rai support "Какие ветки в репозитории?"
# or in English
rai support "What branches are in the repository?"
```

**Expected Response:**
```
Query Type: [GITHUB]

Answer:
В репозитории iruirc/AIResearch обнаружены следующие ветки:

1. **main** [DEFAULT] - основная ветка разработки
2. **feature/trello_git** - текущая feature ветка
3. **develop** - ветка разработки

Suggested Actions:
  1. 📂 Repository: iruirc/AIResearch
     URL: https://github.com/iruirc/AIResearch

  2. 🌿 Branches (3 total):
     [*] main (default)
         SHA: abc1234
         URL: https://github.com/iruirc/AIResearch/tree/main

     [ ] feature/trello_git
         SHA: def5678
         URL: https://github.com/iruirc/AIResearch/tree/feature/trello_git
```

---

### Scenario 12: GitHub - Viewing Recent Commits

**User Story**: As a developer, I want to see recent commits in the repository.

**CLI:**
```bash
rai support "Покажи последние коммиты"
# or
rai support "Show recent commits"
```

**Expected Response:**
```
Query Type: [GITHUB]

Answer:
Последние коммиты в репозитории iruirc/AIResearch:

1. [398675c] feat(mcp): add configurable toolChoice for OpenAI and Claude
   Author: Developer | Date: 2025-12-04

2. [7451883] fix(tech-support): restrict Trello access to TRELLO_SUPPORT_BOARD_ID
   Author: Developer | Date: 2025-12-04

Suggested Actions:
  1. 📝 Recent Commits:
     [398675c] feat(mcp): add configurable toolChoice...
         URL: https://github.com/iruirc/AIResearch/commit/398675c

  2. 👀 View Commit: 398675c
```

---

### Scenario 13: Getting JSON Output

**User Story**: I need to programmatically process the response.

**CLI:**
```bash
rai support --output json "What providers are supported?"
```

**Output:**
```json
{
  "answer": "ResearchAI supports multiple AI providers...",
  "sessionId": "session-xyz",
  "queryType": "HOW_TO",
  "sourcesUsed": {
    "ragSourceCount": 2,
    "trelloTicketCount": 0,
    "ragSources": ["faq/general.md"],
    "trelloSources": []
  },
  "suggestedActions": [],
  "relatedTickets": [],
  "processingTimeMs": 1234
}
```

## Web UI Guide

### Enabling Tech Support Mode

1. Look for the headphones icon in the sidebar
2. Click "Tech Support" button
3. Side panel opens with:
   - RAG status indicator (green = enabled)
   - Trello status indicator (green = connected)
   - Related Tickets section
   - Suggested Actions section
   - Sources Used section

### Panel Sections

**Related Tickets:**
- Shows Trello cards matching your query
- Click card name to open in Trello
- Shows status (list name) and labels

**Suggested Actions:**

The panel intelligently suggests actions based on query type and context:

| Action | Icon | When Appears | Description |
|--------|------|--------------|-------------|
| **Create Ticket** | 📝 | Bug reports, feature requests, or when no other actions apply | Creates a new Trello card with pre-filled title and description |
| **View Ticket** | 👀 | When similar tickets found in Trello | Links to existing related tickets |
| **Escalate** | 🚨/⚠/📢 | Bug reports or questions without RAG/Trello context | Suggests escalating to human support (with priority indicator) |
| **Add to FAQ** | 📖 | HOW_TO questions with good RAG matches (3+ sources) | Suggests adding Q&A to documentation |
| **Contact Support** | 📧 | Status check queries when no matching tickets found | Suggests contacting support via email/chat/phone |
| **List Tasks** | 📋 | PROJECT_MANAGEMENT queries | Shows filtered tasks with priority indicators |
| **Prioritize** | 🎯 | PROJECT_MANAGEMENT queries with multiple tasks | AI recommendations for task order |
| **Project Status** | 📊 | PROJECT_MANAGEMENT status queries | Shows statistics by priority and status |
| **View Branch** | 🌿 | GITHUB_INFO queries about branches | Links to specific branch on GitHub |
| **View Commit** | 📝 | GITHUB_INFO queries about commits | Links to specific commit on GitHub |
| **View Issue** | 🐛 | GITHUB_INFO queries about issues | Links to specific issue on GitHub |
| **View Pull Request** | 🔀 | GITHUB_INFO queries about PRs | Links to specific pull request on GitHub |
| **List Branches** | 📂 | GITHUB_INFO queries requesting branch list | Shows all branches in repository |
| **View Repository** | 🏠 | GITHUB_INFO queries about repo info | Links to repository homepage |

**Priority Indicators (Escalate action):**
- 🚨 **Urgent** - Critical issues requiring immediate attention
- ⚠ **High** - Bug reports without available context
- 📢 **Normal** - General questions needing human help
- 💬 **Low** - Minor issues

**Action Logic by Query Type:**

| Query Type | Primary Actions |
|------------|-----------------|
| `BUG_REPORT` | Create Ticket (Bugs list) + Escalate (if no context) |
| `FEATURE_REQUEST` | Create Ticket (Ideas list) |
| `HOW_TO` | Add to FAQ (if good RAG match) |
| `STATUS_CHECK` | Contact Support (if no tickets found) |
| `PROJECT_MANAGEMENT` | List Tasks + Prioritize + View Ticket (top tasks) |
| `GITHUB_INFO` | List Branches + View Branch/Commit/Issue/PR + View Repository |
| `GENERAL` | Escalate (if no context) or Create Ticket (fallback) |

**Task Priority Indicators (List Tasks action):**
- `[!]` **High** - Critical tasks (red border)
- `[~]` **Medium** - Normal priority tasks (yellow border)
- `[-]` **Low** - Low priority tasks (blue border)
- `[ ]` **None** - Tasks without priority label

**Sources Used:**
- Lists documentation files used for the answer
- Lists Trello tickets referenced

## CLI Reference

```bash
rai support [OPTIONS] [QUERY]

Arguments:
  QUERY  Your support question (optional, interactive if omitted)

Options:
  -s, --session TEXT     Session ID for conversation continuity
  -c, --customer TEXT    Customer ID for context
  -b, --board TEXT       Trello board ID override
  --no-rag              Disable RAG documentation search
  --no-trello           Disable Trello ticket search
  -o, --output TEXT     Output format: text (default), json
  --server TEXT         ResearchAI server URL
  -m, --model TEXT      AI model to use
  -h, --help            Show help message

  # Project Management Options
  -p, --priority TEXT   Filter tasks by priority: high, medium, low
  --status              Show project status summary
  -r, --recommend       Get AI recommendations for task prioritization

  # GitHub Options
  --github-owner TEXT   Override GitHub repository owner
  --github-repo TEXT    Override GitHub repository name
  --no-github           Disable GitHub integration

Examples:
  rai support "How do I configure OAuth?"
  rai support --session abc123 "Follow-up question"
  rai support --no-trello "Documentation only query"
  rai support --output json "Get JSON response"

  # Project Management Examples
  rai support --priority high          # Show high priority tasks
  rai support --status                 # Show project status
  rai support --recommend              # Get AI recommendations
  rai support "What tasks should I do first?"

  # GitHub Examples
  rai support "What branches are in the repo?"
  rai support --github-owner owner --github-repo repo "Show recent commits"
  rai support --no-github "Query without GitHub context"
```

## Best Practices

### Writing Effective Queries

**Good queries:**
- "How do I configure JWT authentication with refresh tokens?"
- "Why am I getting 401 errors after updating to v1.5?"
- "What's the status of the OAuth bug I reported last week?"

**Less effective queries:**
- "Help" (too vague)
- "Error" (no context)
- "Fix it" (no information)

### Using Sessions

- Use sessions for multi-turn conversations
- Session maintains context between questions
- Generate meaningful session IDs: `support-${timestamp}`

### Indexing Documentation

For best results, ensure RAG contains:
1. **API documentation** - Endpoint descriptions, parameters
2. **Configuration guides** - Environment variables, settings
3. **FAQ** - Common questions and answers
4. **Known issues** - Bugs and workarounds
5. **Troubleshooting** - Common problems and solutions

## Troubleshooting

### "Cannot connect to server"

```bash
# Check if server is running
curl http://localhost:8080/health

# Specify server URL
rai support --server http://your-server:8080 "query"
```

### "Trello not connected"

1. Check MCP server configuration
2. Verify Trello API credentials in `.env`
3. Ensure Trello MCP server is registered

### "GitHub not connected"

1. Check MCP server configuration for `github` server
2. Verify `GITHUB_TOKEN` is set in `.env`
3. Ensure GitHub MCP server is registered in `mcp-config.json`
4. For repository access, configure:
   - `GH_DEFAULT_OWNER` - Repository owner (e.g., `iruirc`)
   - `GH_DEFAULT_REPO` - Repository name (e.g., `AIResearch`)
5. Check health endpoint: `GET /api/v2/tech-support/health`

### "No relevant context found"

1. RAG might not be indexed
2. Query might not match any documents
3. Try rephrasing the question
4. Check `ragMinScore` setting (default 0.5)

### Response too slow

1. Reduce `maxRagResults` and `maxTrelloResults`
2. Use faster AI model
3. Disable unused integrations (`--no-rag` or `--no-trello`)

---

## Task Workflow

### Overview

Task Workflow обеспечивает **полную автоматизацию рабочего процесса разработчика** с синхронизацией между Trello, GitHub и AI-powered код-ревью. Управляйте всем жизненным циклом задачи через естественные языковые команды.

### Full Workflow Pipeline

```
START → SYNC → COMPLETE → APPROVE

[START]    "Беру Task_123"        → Создать ветку + показать связанные файлы (RAG)
[SYNC]     "Синхронизируй Task_123" → Merge main в feature branch
[COMPLETE] "Task_123 готов"       → Создать PR + автоматический AI код-ревью
[APPROVE]  "Мержи Task_123"       → Merge PR + удалить ветку + карточка в Done
```

### Supported Commands

**Start Task (START):**
- "Выполняю задачу Task_123"
- "Начинаю работу над Task_123"
- "Беру в работу тикет #123"
- "Starting Task_123"
- "Working on task 123"

**Sync Task (SYNC):**
- "Синхронизируй Task_123"
- "Обнови ветку Task_123"
- "Подтяни main в Task_123"
- "Sync Task_123"
- "Update branch Task_123"

**Complete Task (COMPLETE):**
- "Завершил задачу Task_123"
- "Готово Task_123"
- "Закончил работу над #123"
- "Finished Task_123"
- "Completed task 123"

**Approve Task (APPROVE):**
- "Task_123 approved"
- "Мержи Task_123"
- "Сливай Task_123"
- "LGTM Task_123"
- "Merge Task_123"

**Cancel Task (CANCEL):**
- "Отменяю задачу Task_123"
- "Cancel Task_123"

---

### Scenario 14: Starting a Task

**User Story**: As a developer, I want to start working on a task, automatically creating a Git branch and updating Trello.

**Web UI:**
1. Enable Tech Support mode
2. Type: "Выполняю задачу Task_48"
3. System automatically:
   - Finds card Task_48 in Trello
   - Creates branch `feature/Task_48` from main
   - Moves card to InProgress
   - Adds comment with branch name

**CLI:**
```bash
rai support "Выполняю задачу Task_48"
```

**API:**
```bash
curl -X POST http://localhost:8080/api/v2/tech-support/workflow \
  -H "Content-Type: application/json" \
  -d '{"query": "Выполняю задачу Task_48"}'
```

**Expected Response (with RAG context):**
```
Query Type: [TASK_WORKFLOW]

✅ Задача Task_48 взята в работу!

Trello:
  - Карточка: Task_48: Implement user authentication
  - Перемещено: ToDo → InProgress
  - Комментарий добавлен: "Started work. Branch: feature/Task_48"

GitHub:
  - Ветка создана: feature/Task_48
  - База: main

📁 Связанные файлы:
  • AuthService.kt
  • LoginController.kt
  • UserRepository.kt

Suggested Actions:
  1. 🌿 Open Branch: feature/Task_48
     URL: https://github.com/owner/repo/tree/feature/Task_48

  2. 👀 View Card: Task_48
     URL: https://trello.com/c/xxx
```

---

### Scenario 14.1: Syncing a Task Branch

**User Story**: As a developer, I want to synchronize my feature branch with main to get latest changes.

**CLI:**
```bash
rai support "Синхронизируй Task_48"
```

**API:**
```bash
curl -X POST http://localhost:8080/api/v2/tech-support/workflow \
  -H "Content-Type: application/json" \
  -d '{"query": "Синхронизируй Task_48"}'
```

**Expected Response (success):**
```
Query Type: [TASK_WORKFLOW]

✅ Ветка feature/Task_48 синхронизирована с main.

GitHub:
  - Merge: main → feature/Task_48
  - Статус: Успешно
```

**Expected Response (conflicts):**
```
Query Type: [TASK_WORKFLOW]

⚠️ Конфликты при синхронизации feature/Task_48 с main:
  - AuthService.kt
  - LoginController.kt

Требуется ручное разрешение конфликтов.
```

---

### Scenario 15: Completing a Task

**User Story**: As a developer, I want to mark a task as complete, automatically creating a PR with AI code review and updating Trello.

**CLI:**
```bash
rai support "Завершил задачу Task_48"
```

**Expected Response (with AI Code Review):**
```
Query Type: [TASK_WORKFLOW]

✅ Задача Task_48 завершена!

Trello:
  - Карточка: Task_48: Implement user authentication
  - Перемещено: InProgress → Review
  - Комментарий добавлен: "Task completed. PR: #123"

GitHub:
  - PR создан: #123
  - feature/Task_48 → main
  - URL: https://github.com/owner/repo/pull/123

🤖 AI Code Review:
  - Оценка: 85/100
  - Критических проблем: 0
  - Важных замечаний: 2
  - Предложений: 5
  - Ревью опубликован как комментарий к PR

Suggested Actions:
  1. 🔀 View Pull Request: #123
     URL: https://github.com/owner/repo/pull/123

  2. 👀 View Card: Task_48
     URL: https://trello.com/c/xxx
```

**Note:** AI Code Review выполняется автоматически при создании PR через PRReviewService в режиме STANDARD.

---

### Scenario 15.1: Approving a Task (Merge PR)

**User Story**: As a reviewer, I want to approve and merge a completed task, automatically merging the PR, deleting the branch, and moving the card to Done.

**CLI:**
```bash
rai support "Task_48 approved"
# or
rai support "Мержи Task_48"
# or
rai support "LGTM Task_48"
```

**API:**
```bash
curl -X POST http://localhost:8080/api/v2/tech-support/workflow \
  -H "Content-Type: application/json" \
  -d '{"query": "Task_48 approved"}'
```

**Expected Response:**
```
Query Type: [TASK_WORKFLOW]

✅ Задача Task_48 принята и смержена!

Trello:
  - Карточка: Task_48: Implement user authentication
  - Перемещено: Review → Done
  - Комментарий добавлен: "PR merged and task completed"

GitHub:
  - PR #123 merged (squash)
  - Ветка feature/Task_48 удалена

Suggested Actions:
  1. 👀 View Card: Task_48
     URL: https://trello.com/c/xxx

  2. 🏠 View Repository
     URL: https://github.com/owner/repo
```

**Expected Response (card not in Review):**
```
Query Type: [TASK_WORKFLOW]

❌ Не удалось выполнить approve

Ошибка: Карточка Task_48 не находится в списке Review
Текущий список: InProgress

Для approve карточка должна находиться в Review.

Suggested Actions:
  1. ✅ Complete Task First: Task_48
     Move task to Review before approving
```

**Expected Response (PR not found):**
```
Query Type: [TASK_WORKFLOW]

❌ Не удалось выполнить approve

Ошибка: PR для ветки feature/Task_48 не найден

Suggested Actions:
  1. 🔀 Create PR: feature/Task_48 → main
     Create a pull request first
```

---

### Scenario 16: Task Workflow with Existing Branch

**User Story**: The branch already exists from a previous session.

**CLI:**
```bash
rai support "Начинаю Task_48"
```

**Expected Response:**
```
Query Type: [TASK_WORKFLOW]

✅ Задача Task_48 взята в работу!

Trello:
  - Карточка перемещена в InProgress
  - Комментарий добавлен

GitHub:
  - Ветка feature/Task_48 уже существует
  - Используется существующая ветка

Note: Branch already existed, using existing branch.
```

---

### Scenario 17: Task Workflow Error Handling

**User Story**: The task card is not found in Trello.

**CLI:**
```bash
rai support "Выполняю задачу Task_999"
```

**Expected Response:**
```
Query Type: [TASK_WORKFLOW]

❌ Не удалось выполнить workflow

Ошибка: Карточка с Task_999 не найдена в Trello

Suggested Actions:
  1. 📝 Create Card: Task_999
     Create a new card for this task in Trello

  2. 🔍 Search Tasks
     Search for similar task names
```

---

### Scenario 18: Task Already in Progress

**User Story**: Trying to start a task that's already in progress.

**CLI:**
```bash
rai support "Начинаю Task_48"  # Card already in InProgress
```

**Expected Response:**
```
Query Type: [TASK_WORKFLOW]

⚠️ Задача Task_48 уже в работе

Карточка находится в списке: InProgress

Нельзя начать задачу, которая уже выполняется.

Suggested Actions:
  1. ✅ Complete Task: Task_48
     Mark this task as complete

  2. 👀 View Card: Task_48
     URL: https://trello.com/c/xxx
```

---

### Scenario 19: Direct Workflow API Call

**User Story**: Call workflow API with explicit parameters.

**API:**
```bash
curl -X POST http://localhost:8080/api/v2/tech-support/workflow \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Start task",
    "action": "START",
    "taskId": "Task_48",
    "githubOwner": "custom-owner",
    "githubRepo": "custom-repo"
  }'
```

**Response:**
```json
{
  "success": true,
  "taskId": "Task_48",
  "action": "START",
  "trelloResult": {
    "success": true,
    "cardId": "abc123",
    "cardName": "Task_48: Feature implementation",
    "fromList": "ToDo",
    "toList": "InProgress",
    "cardUrl": "https://trello.com/c/abc123",
    "commentAdded": true
  },
  "githubResult": {
    "success": true,
    "branchName": "feature/Task_48",
    "branchCreated": true,
    "branchAlreadyExists": false
  },
  "message": "Задача Task_48 взята в работу. Ветка feature/Task_48 создана."
}
```

---

### Task Workflow Configuration

**Environment Variables:**
```bash
# Trello (required)
TRELLO_SUPPORT_BOARD_ID=your-board-id
TRELLO_API_KEY=your-api-key
TRELLO_TOKEN=your-token

# GitHub (required)
GH_DEFAULT_OWNER=your-github-owner
GH_DEFAULT_REPO=your-repo-name
GITHUB_TOKEN=ghp_your_token
```

**Trello Board Structure:**
Your Trello board must have lists in this order:
1. Inbox
2. Backlog
3. ToDo
4. InProgress
5. Review
6. Done

**Card Naming Convention:**
Cards should include Task ID in the name:
- `Task_48: Implement feature`
- `#48 Fix authentication bug`
- `Task 48 - Update documentation`

---

### Task Workflow Troubleshooting

**"Card not found":**
- Check card name contains Task ID (Task_N, #N, etc.)
- Verify TRELLO_SUPPORT_BOARD_ID is correct
- Ensure Trello MCP server is connected

**"Cannot start task from this list":**
- Card must be in Inbox, Backlog, or ToDo
- Cards in InProgress/Review/Done cannot be started

**"Branch creation failed":**
- Check GITHUB_TOKEN has repo permissions
- Verify GH_DEFAULT_OWNER and GH_DEFAULT_REPO
- Ensure GitHub MCP server is connected

**"PR creation failed":**
- Verify branch exists: `feature/Task_N`
- Check for merge conflicts with main
- Ensure GitHub token has PR create permissions
