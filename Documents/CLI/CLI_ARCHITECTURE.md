# ResearchAI CLI Architecture

## Overview

ResearchAI CLI (`rai`) is a command-line interface for interacting with the ResearchAI backend server. It provides an interactive chat experience directly from the terminal, with support for RAG (Retrieval-Augmented Generation) and GitHub MCP tools.

## Project Structure

The CLI is implemented as a separate Gradle module within the ResearchAI monorepo:

```
ResearchAI/
├── settings.gradle.kts          # Multi-module configuration
├── build.gradle.kts             # Server module
├── src/                         # Server source code
├── researchai-cli/              # CLI module
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/researchai/cli/
│       ├── ResearchAiCli.kt     # Entry point
│       ├── commands/
│       │   ├── ChatCommand.kt   # Interactive chat
│       │   ├── InitCommand.kt   # Project initialization
│       │   ├── AskCommand.kt    # RAG questions
│       │   └── GitCommand.kt    # GitHub MCP tools
│       ├── handlers/
│       │   ├── GitHandler.kt    # Git logic (shared)
│       │   ├── AskHandler.kt    # Ask logic (shared)
│       │   └── InitHandler.kt   # Init logic (shared)
│       ├── api/
│       │   └── ResearchAiClient.kt  # HTTP client
│       ├── config/
│       │   ├── CliConfig.kt     # CLI configuration
│       │   └── ProjectConfig.kt # Project RAG config
│       ├── strategy/
│       │   └── FileDiscoveryStrategy.kt  # File discovery for RAG
│       └── util/
│           └── GitRepositoryDetector.kt  # Git repo detection
└── gradle/
    └── libs.versions.toml       # Shared dependencies
```

## Architecture

### Components

1. **ResearchAiCli** - Main entry point using Clikt framework
2. **Commands** - Top-level CLI commands (chat, init, ask, git)
3. **Handlers** - Reusable logic shared between commands and slash commands
4. **ResearchAiClient** - HTTP client for server communication
5. **CliConfig** - Global CLI configuration
6. **ProjectConfig** - Per-project RAG configuration

### Handlers Layer

Handlers extract common logic for reuse in both top-level commands and slash commands:

| Handler | Purpose | Used By |
|---------|---------|---------|
| `GitHandler` | GitHub MCP tool execution | `GitCommand`, `/git` |
| `AskHandler` | RAG search and question | `AskCommand`, `/ask` |
| `InitHandler` | Project initialization | `InitCommand`, `/init` |

### Dependencies

- **Clikt** (4.2.1) - Kotlin CLI framework
- **Ktor Client** - HTTP client for API calls
- **Kotlinx Serialization** - JSON serialization

## Building

### Build CLI module
```bash
./gradlew :researchai-cli:build
```

### Build Fat JAR (all dependencies included)
```bash
./gradlew :researchai-cli:buildFatJar
```

Output: `researchai-cli/build/libs/researchai-cli-0.0.1-all.jar`

## Installation

### Option 1: Run directly with Java
```bash
java -jar researchai-cli/build/libs/researchai-cli-0.0.1-all.jar chat
```

### Option 2: Create shell alias
```bash
# Add to ~/.bashrc or ~/.zshrc
alias rai='java -jar /path/to/researchai-cli-0.0.1-all.jar'
```

### Option 3: Create wrapper script
```bash
# Create /usr/local/bin/rai
cat > /usr/local/bin/rai << 'EOF'
#!/bin/bash
java -jar ~/.researchai/researchai-cli.jar "$@"
EOF
chmod +x /usr/local/bin/rai

# Copy JAR to home directory
mkdir -p ~/.researchai
cp researchai-cli/build/libs/researchai-cli-0.0.1-all.jar ~/.researchai/researchai-cli.jar
```

## Configuration

CLI configuration is stored in `~/.researchai/config.properties`:

```properties
# ResearchAI CLI Configuration
server.url=http://localhost:8080
default.model=gpt-4-turbo
```

### Configuration Priority

1. Command-line options (highest priority)
2. Config file (`~/.researchai/config.properties`)
3. Built-in defaults

### Create Config File
```bash
mkdir -p ~/.researchai
cat > ~/.researchai/config.properties << 'EOF'
# ResearchAI CLI Configuration
server.url=http://localhost:8080
default.model=gpt-4-turbo
EOF
```

## Usage

### Top-Level Commands

```bash
# Show all commands
rai --help

# Start interactive chat
rai chat
rai chat --server http://myserver:8080
rai chat --model gpt-4-turbo
rai chat --session abc123-def456

# Initialize project RAG
rai init
rai init --force      # Reinitialize
rai init --yes        # Skip confirmation

# Ask question using RAG
rai ask "What is this project about?"
rai ask --model claude-sonnet-4-5-20241022 "How does authentication work?"

# GitHub MCP tools
rai git                      # List available tools
rai git tools                # List available tools
rai git get_me               # Get current user
rai git list_issues          # List issues (auto-detects repo)
rai git list_issues --owner user --repo myrepo
rai git create_issue --title "Bug" --body "Description"
```

### Model Selection

The model determines which AI provider is used:

| Model Pattern | Provider | Examples |
|---------------|----------|----------|
| `gpt-*` | OpenAI | `gpt-4-turbo`, `gpt-4o` |
| `claude-*` | Claude | `claude-sonnet-4-5-20241022`, `claude-haiku-4-5-20251001` |
| `*:*` | Ollama | `llama3:latest`, `mistral:7b`, `codellama:13b` |
| `*/` or `deepseek` | HuggingFace | `deepseek-ai/DeepSeek-R1` |

## Interactive Commands (Slash Commands)

During chat session, you can use these commands:

| Command | Description |
|---------|-------------|
| `/exit` | Exit the chat |
| `/help` | Show available commands |
| `/clear` | Clear current session history |
| `/session` | Show current session ID |
| `/new` | Start a new session |
| `/model` | Show current model |
| `/model <name>` | Change model (e.g., `/model gpt-4-turbo`) |
| `/git` | List available GitHub tools |
| `/git <tool> [args]` | Execute GitHub tool (e.g., `/git get_me`) |
| `/ask <question>` | Ask question using RAG knowledge |
| `/init` | Initialize/reinitialize project RAG |

### Slash Command Arguments

For `/git` commands, arguments are passed as `key=value` pairs:

```
/git list_issues state=open
/git create_issue title=Bug body=Description
/git search_repositories query=language:kotlin
```

## Example Session

```
$ rai chat
ResearchAI CLI v0.1.0
Model: gpt-4-turbo
RAG context: myproject (5 files)
Connecting to http://localhost:8080...
Connected!
Type /exit to quit, /help for commands

You: /help
Available commands:
  /exit              - Exit the chat
  /help              - Show this help message
  /clear             - Clear current session history
  /session           - Show current session ID
  /new               - Start a new session
  /model             - Show current model
  /model <name>      - Change model (e.g., /model gpt-4-turbo)
  /git               - List available GitHub tools
  /git <tool> [args] - Execute GitHub tool (e.g., /git get_me)
  /ask <question>    - Ask question using RAG knowledge
  /init              - Initialize/reinitialize project RAG

You: /git get_me
Repository: user/myproject
Executing: get_me

Login: username
Name: User Name
...

You: /ask What does this project do?
Searching knowledge base...
Asking AI...

AI: Based on the project documentation, this project is...

You: Hello, how are you?

AI: I'm doing well, thank you for asking! How can I assist you today?

You: /model llama3:latest
Model changed to: llama3:latest

You: /exit
Goodbye!
```

## Project Initialization

The `init` command creates a RAG knowledge base from project files:

```bash
$ rai init
Project directory: /path/to/project
Index this project? [y/N]: y
Scanning for files...
Found 5 file(s):
  - README.md
  - Documents/API.md
  - Documents/ARCHITECTURE.md
...
Creating RAG knowledge base...
[██████████████████████████████] 100% - Saving to storage
Project initialized successfully!
RAG Document ID: abc123-def456
Chunks created: 42
Config saved to: .researchCLI/config.json
```

### File Discovery

Default strategy discovers:
- `README.md` in project root
- All `.md`, `.txt`, `.json`, `.xml`, `.log` files in `Documents/` folder

## API Integration

The CLI communicates with the server using the REST API:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/chat` | POST | Send message |
| `/sessions/{id}/clear` | POST | Clear session |
| `/health` | GET | Health check |
| `/rag/documents` | POST | Create RAG document |
| `/rag/documents/stream` | POST | Create RAG document with SSE progress |
| `/rag/search` | POST | Search RAG |
| `/mcp/tools` | GET | Get MCP tools |
| `/mcp/tools/call` | POST | Call MCP tool |

### Chat Request Example
```json
POST /chat
{
  "message": "Hello",
  "sessionId": "abc123",
  "model": "gpt-4-turbo"
}
```

### Chat Response Example
```json
{
  "response": "Hello! How can I help you?",
  "sessionId": "abc123",
  "tokensUsed": 150
}
```

## Future Enhancements

Potential features for future versions:

- `/models` - List available models from server
- `/assistant` - Select custom assistants
- `/config` - View/edit CLI configuration
- Command history (readline support)
- Colored output (ANSI)
- Tab completion
- Streaming responses
- File input support (`rai chat < input.txt`)
- Pipe support (`echo "Hello" | rai chat`)
