# Tech Support AI Assistant - User Guide

## Introduction

Tech Support AI Assistant helps answer technical questions by combining:
- Documentation and FAQ database (RAG)
- Trello bug tracking integration
- AI-powered intelligent responses

## Getting Started

### Prerequisites

1. **Server running**: ResearchAI server must be started
2. **Trello configured** (optional): For ticket integration, configure Trello MCP server
3. **RAG indexed** (optional): Index documentation for knowledge base search

### Configuration

Set environment variables in `.env`:

```bash
# Trello integration (optional)
TRELLO_SUPPORT_BOARD_ID=your-board-id
TRELLO_API_KEY=your-api-key
TRELLO_TOKEN=your-token
```

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
  1. View Related Ticket:
     Ticket: AUTH-123 - 401 errors after v1.5 update
     Reason: Similar issue reported by other users

  2. Create Ticket (if new issue):
     Title: Login failure - 401 error after v1.5 update
     Description: User reports login failures...
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

**Expected Response:**
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
  1. Create Ticket:
     Title: Feature Request: SAML Authentication Support
     Description: User requests SAML authentication support for
                  enterprise SSO integration
     Labels: [feature-request, auth]
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
  Documentation (1):
    - faq/general.md
  Known Issues (1):
    - known-issues/current.md
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

### Scenario 8: Getting JSON Output

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
- "Create Ticket" - Opens form to create new Trello card
- "View Ticket" - Links to relevant existing tickets

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

Examples:
  rai support "How do I configure OAuth?"
  rai support --session abc123 "Follow-up question"
  rai support --no-trello "Documentation only query"
  rai support --output json "Get JSON response"
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

### "No relevant context found"

1. RAG might not be indexed
2. Query might not match any documents
3. Try rephrasing the question
4. Check `ragMinScore` setting (default 0.5)

### Response too slow

1. Reduce `maxRagResults` and `maxTrelloResults`
2. Use faster AI model
3. Disable unused integrations (`--no-rag` or `--no-trello`)
