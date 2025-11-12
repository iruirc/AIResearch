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
java -jar build/libs/ktor-firtsAI-0.0.1-all.jar
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
- Provider factory
- Repositories (ConfigRepository, SessionRepository)
- Use cases (SendMessageUseCase, GetModelsUseCase)
- Legacy services (ClaudeService, ChatSessionManager, AgentManager)

**Important**: Close the AppModule on application shutdown to clean up HTTP client resources.

### Session Management

Sessions are managed in-memory by `ChatSessionManager`:
- Each session maintains conversation history as `List<Message>`
- Sessions can be associated with agents (for custom system prompts)
- Messages store metadata (model, tokens used, response time)
- No persistence layer - sessions are lost on restart

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

## Important Notes

- **No tests**: The project currently has no test suite
- **In-memory sessions**: All session data is lost on restart
- **Main application class**: Entry point is `com.researchai.ApplicationKt` (Application.kt)
- **Java version**: Requires Java 17+
- **Static resources**: Web UI files are in `src/main/resources/static/`
- **CORS**: Enabled for all origins with `anyHost()`
- **Request timeout**: HTTP client has 5-minute timeout for long AI responses


## Tasks
The `../Tasks/` folder is used to store progress on completing tasks.
If the [Task] tag is specified in the prompt, then it is necessary to keep in mind that working with the specified folder