# Multi-Provider Architecture - Руководство

## Обзор

ResearchAI теперь поддерживает множественные AI-провайдеры через новую архитектуру на основе Domain-Driven Design и Clean Architecture.

## Поддерживаемые провайдеры

1. **Claude (Anthropic)** - настроен по умолчанию через `.env`
2. **OpenAI (GPT)** - требует настройки через API
3. **Google Gemini** - требует настройки через API
4. **Custom** - возможность добавления собственных провайдеров

## Архитектура

```
┌─────────────────────────────────────────────────────────┐
│                    API Routes (HTTP)                     │
│  /api/v2/chat - отправка сообщений                       │
│  /api/v2/providers - управление провайдерами             │
└─────────────────────────────────────────────────────────┘
                           ↓ ↑
┌─────────────────────────────────────────────────────────┐
│                    Use Cases (Domain)                    │
│  SendMessageUseCase, GetModelsUseCase                    │
└─────────────────────────────────────────────────────────┘
                           ↓ ↑
┌─────────────────────────────────────────────────────────┐
│              Provider Factory (Strategy Pattern)         │
│  ClaudeProvider | OpenAIProvider | GeminiProvider        │
└─────────────────────────────────────────────────────────┘
```

## Новые API Endpoints

### 1. POST /api/v2/chat
Отправка сообщения с выбором провайдера

**Request:**
```json
{
  "message": "Explain quantum computing",
  "provider": "claude",  // или "openai", "gemini"
  "sessionId": "optional-session-id",
  "model": "claude-sonnet-4-5-20250929",  // опционально
  "temperature": 0.7,
  "maxTokens": 2048,
  "format": "PLAIN_TEXT"  // или "JSON", "XML"
}
```

**Response:**
```json
{
  "response": "Quantum computing is...",
  "sessionId": "uuid-session-id",
  "usage": {
    "inputTokens": 15,
    "outputTokens": 450,
    "totalTokens": 465
  },
  "model": "claude-sonnet-4-5-20250929",
  "provider": "claude"
}
```

### 2. POST /api/v2/providers/configure
Настройка провайдера

**Request для OpenAI:**
```json
{
  "provider": "openai",
  "apiKey": "sk-...",
  "organization": "org-...",  // опционально
  "defaultModel": "gpt-4-turbo"
}
```

**Request для Gemini:**
```json
{
  "provider": "gemini",
  "apiKey": "AIza...",
  "defaultModel": "gemini-pro"
}
```

**Response:**
```json
{
  "message": "Provider OpenAI configured successfully"
}
```

### 3. GET /api/v2/providers/{provider}/models
Получение списка доступных моделей провайдера

**Request:**
```
GET /api/v2/providers/claude/models
GET /api/v2/providers/openai/models
```

**Response:**
```json
{
  "provider": "claude",
  "models": [
    {
      "id": "claude-sonnet-4-5-20250929",
      "name": "Claude Sonnet 4.5",
      "capabilities": {
        "supportsVision": false,
        "supportsStreaming": true,
        "maxTokens": 8192,
        "contextWindow": 200000
      }
    }
  ]
}
```

### 4. GET /api/v2/providers
Получение списка всех доступных провайдеров

**Response:**
```json
{
  "providers": [
    {
      "id": "claude",
      "displayName": "Anthropic Claude"
    },
    {
      "id": "openai",
      "displayName": "OpenAI"
    },
    {
      "id": "gemini",
      "displayName": "Google Gemini"
    }
  ]
}
```

## Примеры использования

### Настройка OpenAI

```bash
curl -X POST http://localhost:8080/api/v2/providers/configure \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "openai",
    "apiKey": "sk-your-api-key-here",
    "defaultModel": "gpt-4-turbo"
  }'
```

### Отправка сообщения в Claude

```bash
curl -X POST http://localhost:8080/api/v2/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is the meaning of life?",
    "provider": "claude",
    "temperature": 0.8
  }'
```

### Отправка сообщения в OpenAI

```bash
curl -X POST http://localhost:8080/api/v2/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Write a poem about AI",
    "provider": "openai",
    "model": "gpt-4-turbo",
    "temperature": 1.0
  }'
```

### Получение списка моделей

```bash
curl http://localhost:8080/api/v2/providers/openai/models
```

## Структура кода

**Package:** `com.researchai`

### Domain Layer
- `com.researchai.domain.models` - универсальные модели (AIRequest, AIResponse, Message, etc.)
- `com.researchai.domain.provider` - интерфейс AIProvider
- `com.researchai.domain.repository` - интерфейсы репозиториев
- `com.researchai.domain.usecase` - бизнес-логика (SendMessageUseCase, GetModelsUseCase)

### Data Layer
- `com.researchai.data.provider.claude` - реализация провайдера Claude
- `com.researchai.data.provider.openai` - реализация провайдера OpenAI
- `com.researchai.data.provider.AIProviderFactoryImpl` - фабрика провайдеров
- `com.researchai.data.repository` - реализации репозиториев

### Presentation Layer
- `com.researchai.routes.ProviderRoutes` - новые API endpoints
- `com.researchai.routes.ChatRoutes` - legacy endpoints (сохранены для обратной совместимости)
- `com.researchai.di.AppModule` - Dependency Injection

## Добавление нового провайдера

### 1. Создайте реализацию AIProvider

```kotlin
class CustomProvider(
    private val httpClient: HttpClient,
    override val config: ProviderConfig.CustomConfig
) : AIProvider {
    override val providerId = ProviderType.CUSTOM

    override suspend fun sendMessage(request: AIRequest): Result<AIResponse> {
        // Ваша реализация
    }

    override suspend fun getModels(): Result<List<AIModel>> {
        // Ваша реализация
    }

    override fun validateConfig(): ValidationResult {
        // Ваша реализация
    }
}
```

### 2. Зарегистрируйте провайдера в фабрике

Добавьте в `AIProviderFactoryImpl.kt`:

```kotlin
init {
    register(ProviderType.CUSTOM) { config ->
        CustomProvider(httpClient, config as ProviderConfig.CustomConfig)
    }
}
```

## Обратная совместимость

Все старые endpoints продолжают работать:
- `POST /chat` - работает как раньше с Claude
- `GET /sessions` - управление сессиями
- `GET /assistants` - работа с ассистентами
- `GET /models` - список моделей Claude
- `GET /config` - текущая конфигурация

## Миграция с legacy API

### Было (legacy):
```bash
POST /chat
{
  "message": "Hello",
  "model": "claude-sonnet-4-5-20250929"
}
```

### Стало (новая архитектура):
```bash
POST /api/v2/chat
{
  "message": "Hello",
  "provider": "claude",
  "model": "claude-sonnet-4-5-20250929"
}
```

## Преимущества новой архитектуры

1. **Расширяемость** - легко добавлять новые провайдеры
2. **Тестируемость** - чистая архитектура с DI
3. **Гибкость** - переключение между провайдерами на лету
4. **Унификация** - единый интерфейс для всех провайдеров
5. **Обратная совместимость** - старый код продолжает работать

## Планы развития

- [ ] Streaming support для всех провайдеров
- [ ] Batch requests
- [ ] Cost tracking per provider
- [ ] Automatic fallback between providers
- [ ] Rate limiting per provider
- [ ] Provider health monitoring
