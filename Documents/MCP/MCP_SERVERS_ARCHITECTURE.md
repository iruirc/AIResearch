# MCP Servers Architecture

## Overview

This document describes the architecture, configuration, and operation of all MCP (Model Context Protocol) servers configured in the ResearchAI application.

**Configuration file:** `config/mcp-servers.json`
**Preferences file:** `data/mcp-preferences.json`

## Transport Types

All servers use **STDIO transport** - communication via stdin/stdout with JSON-RPC protocol.

```
┌─────────────────────────────────────────────────────────────────┐
│                    ResearchAI Application                       │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   MCPServerManager                       │   │
│  │                                                          │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │   │
│  │  │MCPClient     │  │MCPClient     │  │MCPClient     │   │   │
│  │  │Wrapper       │  │Wrapper       │  │Wrapper       │   │   │
│  │  │(github)      │  │(trello)      │  │(filesystem)  │   │   │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │   │
│  └─────────┼─────────────────┼─────────────────┼───────────┘   │
│            │                 │                 │                │
└────────────┼─────────────────┼─────────────────┼────────────────┘
             │ STDIO           │ STDIO           │ STDIO
             ▼                 ▼                 ▼
      ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
      │   Docker     │  │    Bunx      │  │     NPX      │
      │  Container   │  │   Process    │  │   Process    │
      └──────────────┘  └──────────────┘  └──────────────┘
```

---

## Configured Servers

### 1. GitHub MCP Server

| Property | Value |
|----------|-------|
| **ID** | `github` |
| **Name** | GitHub MCP Server |
| **Runtime** | Docker |
| **Image** | `ghcr.io/github/github-mcp-server` |
| **Tools Count** | ~51 tools |

#### Configuration

```json
{
  "id": "github",
  "name": "GitHub MCP Server",
  "description": "Official GitHub MCP Server with 51 tools",
  "transport": "stdio",
  "command": "docker",
  "args": ["run", "-i", "--rm", "-e", "GITHUB_PERSONAL_ACCESS_TOKEN", "ghcr.io/github/github-mcp-server"],
  "env": {
    "GITHUB_PERSONAL_ACCESS_TOKEN": "${GITHUB_TOKEN}"
  },
  "enabled": true
}
```

#### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│  .env file                                                      │
│  └─ GITHUB_TOKEN=ghp_xxxxxxxxxxxx                              │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  MCPConfigLoader.kt                                             │
│  - Reads config/mcp-servers.json                               │
│  - Substitutes ${GITHUB_TOKEN} with env value                  │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  MCPClientWrapper.connectStdio()                               │
│                                                                 │
│  ProcessBuilder command:                                        │
│  docker run -i --rm -e GITHUB_PERSONAL_ACCESS_TOKEN             │
│         ghcr.io/github/github-mcp-server                       │
│                                                                 │
│  Environment:                                                   │
│  GITHUB_PERSONAL_ACCESS_TOKEN = "ghp_xxxxxxxxxxxx"             │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Docker Container                                               │
│  ghcr.io/github/github-mcp-server                              │
│                                                                 │
│  Available Tools:                                               │
│  - pull_request_read      - get_file_contents                  │
│  - pull_request_diff      - create_issue                       │
│  - pull_request_files     - add_issue_comment                  │
│  - list_commits           - search_repositories                │
│  - create_pull_request    - ... and ~45 more                   │
│                                                                 │
│  Internal flow:                                                 │
│  JSON-RPC request → GitHub REST API → JSON-RPC response        │
└─────────────────────────────────────────────────────────────────┘
```

#### Where It Downloads

- **Image location:** Docker pulls from `ghcr.io` (GitHub Container Registry)
- **Local cache:** `~/.docker/` or Docker Desktop storage
- **No source code download** - runs as pre-built container

#### Environment Variables

| Variable | Source | Description |
|----------|--------|-------------|
| `GITHUB_TOKEN` | `.env` file | GitHub Personal Access Token |

---

### 2. Trello MCP Server

| Property | Value |
|----------|-------|
| **ID** | `trello` |
| **Name** | Trello MCP Server |
| **Runtime** | Bun (via bunx) |
| **Package** | `@delorenj/mcp-server-trello` |
| **Tools Count** | ~15 tools |

#### Configuration

```json
{
  "id": "trello",
  "name": "Trello MCP Server",
  "description": "Official Trello MCP Server (@delorenj/mcp-server-trello) - manage boards, lists, cards",
  "transport": "stdio",
  "command": "/opt/homebrew/bin/bunx",
  "args": ["@delorenj/mcp-server-trello"],
  "env": {
    "TRELLO_API_KEY": "${TRELLO_API_KEY}",
    "TRELLO_TOKEN": "${TRELLO_TOKEN}"
  },
  "enabled": true
}
```

#### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│  .env file                                                      │
│  ├─ TRELLO_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx            │
│  └─ TRELLO_TOKEN=ATTAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx           │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  MCPClientWrapper.connectStdio()                               │
│                                                                 │
│  ProcessBuilder command:                                        │
│  /opt/homebrew/bin/bunx @delorenj/mcp-server-trello            │
│                                                                 │
│  Environment:                                                   │
│  TRELLO_API_KEY = "xxxxxxxx..."                                │
│  TRELLO_TOKEN = "ATTAxxxxxx..."                                │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Bun Process                                                    │
│  @delorenj/mcp-server-trello                                   │
│                                                                 │
│  Available Tools:                                               │
│  - get_boards             - create_card                        │
│  - get_lists              - update_card                        │
│  - get_cards              - move_card                          │
│  - set_active_board       - add_comment                        │
│  - get_card_details       - delete_card                        │
│                                                                 │
│  Internal flow:                                                 │
│  JSON-RPC request → Trello REST API → JSON-RPC response        │
└─────────────────────────────────────────────────────────────────┘
```

#### Where It Downloads

- **Package location:** npm registry (`registry.npmjs.org`)
- **Local cache:** `~/.bun/install/cache/` (Bun's package cache)
- **First run:** Downloads and caches package automatically

#### Environment Variables

| Variable | Source | Description |
|----------|--------|-------------|
| `TRELLO_API_KEY` | `.env` file | Trello API Key (from https://trello.com/app-key) |
| `TRELLO_TOKEN` | `.env` file | Trello Authorization Token |

#### Why Bun Instead of NPX?

The package is optimized for Bun runtime. NPX works but may have stability issues.
Full path `/opt/homebrew/bin/bunx` is required because Java ProcessBuilder may not inherit shell PATH.

---

### 3. Filesystem Server

| Property | Value |
|----------|-------|
| **ID** | `filesystem` |
| **Name** | Filesystem Server |
| **Runtime** | Node.js (via npx) |
| **Package** | `@modelcontextprotocol/server-filesystem` |
| **Tools Count** | ~14 tools |

#### Configuration

```json
{
  "id": "filesystem",
  "name": "Filesystem Server",
  "description": "Access to local filesystem",
  "transport": "stdio",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
  "enabled": true
}
```

#### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│  MCPClientWrapper.connectStdio()                               │
│                                                                 │
│  ProcessBuilder command:                                        │
│  npx -y @modelcontextprotocol/server-filesystem /tmp           │
│                                                                 │
│  Arguments:                                                     │
│  -y = auto-confirm package installation                        │
│  /tmp = allowed directory root                                 │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Node.js Process                                                │
│  @modelcontextprotocol/server-filesystem                       │
│                                                                 │
│  Available Tools:                                               │
│  - read_file              - write_file                         │
│  - read_multiple_files    - create_directory                   │
│  - list_directory         - move_file                          │
│  - directory_tree         - search_files                       │
│  - get_file_info          - list_allowed_directories           │
│                                                                 │
│  Security:                                                      │
│  - Only /tmp directory is accessible                           │
│  - Sandboxed file operations                                   │
└─────────────────────────────────────────────────────────────────┘
```

#### Where It Downloads

- **Package location:** npm registry (`registry.npmjs.org`)
- **Local cache:** `~/.npm/_npx/` (npx cache)
- **Official package:** Part of MCP SDK from Anthropic

#### Environment Variables

None required.

---

### 4. Weather Server (Custom/Local)

| Property | Value |
|----------|-------|
| **ID** | `weather` |
| **Name** | Weather Server |
| **Runtime** | Local script |
| **Location** | `/Volumes/Data/Projects/.../weatherMCP/` |

#### Configuration

```json
{
  "id": "weather",
  "name": "Weather Server",
  "description": "Access to weather API",
  "transport": "stdio",
  "command": "/Volumes/Data/Projects/MobileDeveloper/Projects/mcpServers/localMcp/weatherMCP/run-mcp-server.sh",
  "args": [],
  "enabled": true
}
```

#### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│  MCPClientWrapper.connectStdio()                               │
│                                                                 │
│  ProcessBuilder command:                                        │
│  /Volumes/.../weatherMCP/run-mcp-server.sh                     │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Shell Script → Node.js/Python Process                         │
│  weatherMCP                                                     │
│                                                                 │
│  Available Tools:                                               │
│  - get_weather            - get_forecast                       │
│  - get_current_conditions                                      │
│                                                                 │
│  Internal flow:                                                 │
│  JSON-RPC request → Weather API → JSON-RPC response            │
└─────────────────────────────────────────────────────────────────┘
```

#### Where It's Located

- **Local path:** `/Volumes/Data/Projects/MobileDeveloper/Projects/mcpServers/localMcp/weatherMCP/`
- **No download:** Custom implementation, runs from local filesystem

---

### 5. Trello Custom Server (Custom/Local)

| Property | Value |
|----------|-------|
| **ID** | `trello-custom` |
| **Name** | Trello Custom Server |
| **Runtime** | Local script |
| **Location** | `/Volumes/Data/Projects/.../trelloMCP/` |

#### Configuration

```json
{
  "id": "trello-custom",
  "name": "Trello Custom Server",
  "description": "Access to Trello API (custom implementation)",
  "transport": "stdio",
  "command": "/Volumes/Data/Projects/MobileDeveloper/Projects/mcpServers/localMcp/trelloMCP/run-mcp-server.sh",
  "args": [],
  "enabled": true
}
```

#### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│  MCPClientWrapper.connectStdio()                               │
│                                                                 │
│  ProcessBuilder command:                                        │
│  /Volumes/.../trelloMCP/run-mcp-server.sh                      │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Shell Script → Node.js/Python Process                         │
│  trelloMCP (custom)                                            │
│                                                                 │
│  Custom implementation of Trello integration                    │
│  May have different tools than official package                │
└─────────────────────────────────────────────────────────────────┘
```

#### Where It's Located

- **Local path:** `/Volumes/Data/Projects/MobileDeveloper/Projects/mcpServers/localMcp/trelloMCP/`
- **No download:** Custom implementation, runs from local filesystem

---

## Server Types Comparison

| Server | Type | Runtime | Download Location | Requires Auth |
|--------|------|---------|-------------------|---------------|
| GitHub | Docker Container | Docker | ghcr.io | GITHUB_TOKEN |
| Trello | NPM Package | Bun | npm registry | TRELLO_API_KEY, TRELLO_TOKEN |
| Filesystem | NPM Package | Node.js | npm registry | No |
| Weather | Local Script | Node.js/Python | Local filesystem | Depends |
| Trello Custom | Local Script | Node.js/Python | Local filesystem | Depends |

---

## Startup Sequence

```
Application Start
       │
       ▼
┌──────────────────────────────────────┐
│  Application.kt:126                  │
│  appModule.initializeMCP()           │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│  AppModule.kt:382                    │
│  mcpServerManager.initialize()       │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│  MCPServerManager.kt:24-69           │
│                                      │
│  1. Load mcp-preferences.json        │
│  2. Filter enabled servers           │
│  3. For each server:                 │
│     └─ Create MCPClientWrapper       │
│     └─ Launch connect() coroutine    │
│  4. Wait 2 seconds for connections   │
│  5. Log connection status            │
└──────────────┬───────────────────────┘
               │
               ▼
┌──────────────────────────────────────┐
│  MCPClientWrapper.kt:63-120          │
│  connectStdio()                      │
│                                      │
│  1. Build command array              │
│  2. Set environment variables        │
│  3. ProcessBuilder.start()           │
│  4. Create MCP Client (SDK)          │
│  5. Setup StdioClientTransport       │
│  6. client.connect(transport)        │
└──────────────────────────────────────┘
```

---

## Environment Variable Substitution

The `MCPConfigLoader.kt` performs environment variable substitution:

```kotlin
// Pattern: ${VAR_NAME}
val envVarPattern = """\$\{([A-Za-z0-9_]+)\}""".toRegex()

// Sources (in order):
// 1. System.getenv(varName)
// 2. System.getProperty(varName)
```

**Example:**
```
Config:     "GITHUB_PERSONAL_ACCESS_TOKEN": "${GITHUB_TOKEN}"
.env file:  GITHUB_TOKEN=ghp_abc123
Result:     "GITHUB_PERSONAL_ACCESS_TOKEN": "ghp_abc123"
```

---

## User Preferences

User can enable/disable servers via UI. Preferences stored in `data/mcp-preferences.json`:

```json
{
  "enabledServers": ["github", "filesystem", "trello"]
}
```

**API Endpoints:**
- `POST /mcp/servers/{id}/enable` - Enable server
- `POST /mcp/servers/{id}/disable` - Disable server
- `GET /mcp/servers` - List all servers with status

---

## Troubleshooting

### Server Won't Connect

1. **Check environment variables:**
   ```bash
   grep GITHUB_TOKEN .env
   grep TRELLO_API_KEY .env
   ```

2. **Test manually:**
   ```bash
   # GitHub (Docker)
   docker run -i --rm -e GITHUB_PERSONAL_ACCESS_TOKEN=$GITHUB_TOKEN ghcr.io/github/github-mcp-server

   # Trello (Bunx)
   TRELLO_API_KEY=xxx TRELLO_TOKEN=xxx /opt/homebrew/bin/bunx @delorenj/mcp-server-trello

   # Filesystem (NPX)
   npx -y @modelcontextprotocol/server-filesystem /tmp
   ```

3. **Check logs:**
   - Application console shows connection status
   - stderr from processes is logged with `[ServerName stderr]` prefix

### Docker Image Not Found

```bash
# Pull image manually
docker pull ghcr.io/github/github-mcp-server
```

### NPM Package Issues

```bash
# Clear npx cache
rm -rf ~/.npm/_npx/

# Clear bun cache
rm -rf ~/.bun/install/cache/
```

---

## Adding New Servers

1. Add configuration to `config/mcp-servers.json`:
   ```json
   {
     "id": "new-server",
     "name": "New Server",
     "description": "Description",
     "transport": "stdio",
     "command": "npx",
     "args": ["-y", "@package/name"],
     "env": {
       "API_KEY": "${NEW_SERVER_API_KEY}"
     },
     "enabled": true
   }
   ```

2. Add environment variables to `.env`:
   ```
   NEW_SERVER_API_KEY=your_key_here
   ```

3. Restart application or enable via UI

---

## Related Documentation

- [MCP_INTEGRATION.md](./MCP_INTEGRATION.md) - Integration architecture
- [MCP_ORCHESTRATION.md](./MCP_ORCHESTRATION.md) - Orchestration service details
