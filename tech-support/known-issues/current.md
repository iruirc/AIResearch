# Known Issues

## Active Issues

### ISSUE-001: MCP Server Connection Timeout on Cold Start
**Status:** Under Investigation
**Severity:** Medium
**Affected Versions:** 1.0.x

**Description:**
MCP servers may fail to connect on first request after server restart. This is due to lazy initialization of MCP connections.

**Symptoms:**
- First MCP tool call fails with "Connection timeout"
- Subsequent calls work normally
- Error in logs: "Failed to initialize MCP server"

**Workaround:**
Retry the request after 5-10 seconds, or call the `/mcp/health` endpoint after server startup to warm up connections.

**Fix ETA:** Next release (1.1.0)

---

### ISSUE-002: RAG Embedding Inconsistency with Special Characters
**Status:** Open
**Severity:** Low
**Affected Versions:** All

**Description:**
Documents containing certain Unicode characters may produce inconsistent embeddings, affecting search quality.

**Symptoms:**
- Search results may miss relevant documents with special characters
- Similarity scores lower than expected

**Workaround:**
Normalize text before indexing (remove or replace special characters).

---

### ISSUE-003: Session Compression May Lose Assistant Context
**Status:** Fixed in 1.0.5
**Severity:** Medium
**Affected Versions:** < 1.0.5

**Description:**
When using FULL_REPLACEMENT compression strategy with custom assistants, the assistant's system prompt context may be partially lost.

**Resolution:**
Update to version 1.0.5 or later. The fix preserves assistant context during compression.

---

## Recently Resolved

### ISSUE-000: OAuth Token Refresh Loop
**Status:** Fixed in 1.0.4
**Severity:** High

**Description:**
In rare cases, OAuth token refresh could enter an infinite loop, causing high CPU usage.

**Resolution:**
Fixed in 1.0.4 with proper token expiration handling.

---

## Reporting New Issues

To report a new issue:
1. Create a ticket in Trello support board
2. Include: version, steps to reproduce, expected vs actual behavior
3. Attach relevant logs if available

Or use the tech support assistant: `rai support "Describe your issue"`
