# ResearchAI CLI Architecture

## Overview

ResearchAI CLI (`rai`) is a command-line interface for interacting with the ResearchAI backend server. It provides an interactive chat experience directly from the terminal.

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
│       │   └── ChatCommand.kt   # Interactive chat
│       ├── api/
│       │   └── ResearchAiClient.kt  # HTTP client
│       └── config/
│           └── CliConfig.kt     # Configuration
└── gradle/
    └── libs.versions.toml       # Shared dependencies
```

## Architecture

### Components

1. **ResearchAiCli** - Main entry point using Clikt framework
2. **ChatCommand** - Interactive chat session handler
3. **ResearchAiClient** - HTTP client for server communication
4. **CliConfig** - Configuration management

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

### Command Line Options

```bash
# Start chat with default settings
rai chat

# Specify server URL
rai chat --server http://myserver:8080
rai chat -s http://myserver:8080

# Specify model
rai chat --model gpt-4-turbo
rai chat -m claude-sonnet-4-5-20241022
rai chat -m llama3:latest

# Continue existing session
rai chat --session abc123-def456

# Combine options
rai chat -s http://myserver:8080 -m gpt-4-turbo --session abc123

# Show help
rai --help
rai chat --help
```

### Model Selection

The model determines which AI provider is used:

| Model Pattern | Provider | Examples |
|---------------|----------|----------|
| `gpt-*` | OpenAI | `gpt-4-turbo`, `gpt-4o` |
| `claude-*` | Claude | `claude-sonnet-4-5-20241022`, `claude-haiku-4-5-20251001` |
| `*:*` | Ollama | `llama3:latest`, `mistral:7b`, `codellama:13b` |
| `*/` or `deepseek` | HuggingFace | `deepseek-ai/DeepSeek-R1` |

## Interactive Commands

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

## Example Session

```
$ rai chat
ResearchAI CLI v0.1.0
Model: gpt-4-turbo
Connecting to http://localhost:8080...
Connected!
Type /exit to quit, /help for commands

You: Hello, how are you?

AI: I'm doing well, thank you for asking! How can I assist you today?

You: /model
Current model: gpt-4-turbo

You: /model llama3:latest
Model changed to: llama3:latest

You: Tell me a joke

AI: Why do programmers prefer dark mode? Because light attracts bugs!

You: /session
Current session: abc123-def456-789

You: /exit
Goodbye!
```

## API Integration

The CLI communicates with the server using the existing REST API:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/chat` | POST | Send message |
| `/sessions/{id}/clear` | POST | Clear session |
| `/health` | GET | Health check |

### Request Example
```json
POST /chat
{
  "message": "Hello",
  "sessionId": "abc123",
  "model": "gpt-4-turbo"
}
```

### Response Example
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
