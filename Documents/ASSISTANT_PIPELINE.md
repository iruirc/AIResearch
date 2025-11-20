# Assistant Pipeline - Chain of Function Calling

**Версия:** 1.0
**Дата:** 2025-11-20
**Статус:** Production Ready

---

## Содержание

1. [Обзор](#обзор)
2. [Архитектура](#архитектура)
3. [Модели данных](#модели-данных)
4. [REST API](#rest-api)
5. [Примеры использования](#примеры-использования)
6. [Персистентность](#персистентность)
7. [Интеграция с сессиями](#интеграция-с-сессиями)
8. [Обработка ошибок](#обработка-ошибок)
9. [Ограничения и лимиты](#ограничения-и-лимиты)
10. [Расширение функциональности](#расширение-функциональности)

---

## Обзор

### Что такое Assistant Pipeline?

**Assistant Pipeline** (цепочка вызовов ассистентов) - это система последовательного выполнения AI-ассистентов, где выходные данные одного ассистента автоматически становятся входными данными для следующего ассистента в цепочке.

### Основные возможности

- ✅ **Последовательное выполнение** - ассистенты выполняются строго по порядку
- ✅ **Персистентность** - конфигурации и история сохраняются на диске
- ✅ **REST API** - полный набор endpoints для управления
- ✅ **Отслеживание прогресса** - детальная информация о каждом шаге
- ✅ **Обработка ошибок** - graceful handling с частичными результатами
- ✅ **Timeout защита** - автоматическое прерывание долгих операций
- ✅ **Интеграция с сессиями** - один chat session для всей цепочки

### Кейсы использования

**1. Многоступенчатая обработка данных:**
```
User Input → Data Validator → Data Analyzer → Report Generator → Final Output
```

**2. Образовательные сценарии:**
```
Question → Tutor (Explain) → Quiz Generator → Answer Validator → Result
```

**3. Контент-генерация:**
```
Topic → Research Assistant → Writer → Editor → Formatter → Article
```

**4. Комплексный анализ:**
```
Code → Syntax Checker → Security Analyzer → Performance Analyzer → Report
```

---

## Архитектура

### Clean Architecture Layers

```
┌─────────────────────────────────────────────────────┐
│              Presentation Layer                      │
│  ┌────────────────────────────────────────────┐     │
│  │  PipelineRoutes.kt                         │     │
│  │  - POST /execute                           │     │
│  │  - GET /configs                            │     │
│  │  - GET /executions                         │     │
│  └────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│                Domain Layer                          │
│  ┌────────────────────────────────────────────┐     │
│  │  AssistantPipelineUseCase.kt               │     │
│  │  - Sequential execution logic              │     │
│  │  - Error handling                          │     │
│  │  - Timeout management                      │     │
│  └────────────────────────────────────────────┘     │
│  ┌────────────────────────────────────────────┐     │
│  │  Domain Models                             │     │
│  │  - AssistantPipeline                       │     │
│  │  - PipelineExecution                       │     │
│  │  - ExecutePipelineRequest                  │     │
│  └────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│               Data/Persistence Layer                 │
│  ┌────────────────────────────────────────────┐     │
│  │  JsonPipelineStorage.kt                    │     │
│  │  - Save/Load configurations                │     │
│  │  - Save/Load execution history             │     │
│  └────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────┘
```

### Основные компоненты

#### 1. Domain Models (`domain/models/`)
- **AssistantPipeline** - конфигурация pipeline
- **PipelineExecution** - состояние выполнения
- **ExecutePipelineRequest** - запрос на выполнение
- **PipelineExecutionResult** - результат выполнения

#### 2. Use Case (`domain/usecase/`)
- **AssistantPipelineUseCase** - бизнес-логика выполнения

#### 3. Persistence (`persistence/`)
- **AssistantPipelineStorage** - интерфейс хранилища
- **JsonPipelineStorage** - JSON-based реализация

#### 4. REST API (`routes/`)
- **PipelineRoutes** - HTTP endpoints

#### 5. Integration (`di/`)
- **AppModule** - dependency injection контейнер

---

## Модели данных

### AssistantPipeline

Конфигурация pipeline - описывает последовательность ассистентов и параметры выполнения.

```kotlin
data class AssistantPipeline(
    val id: String,                              // Уникальный ID
    val name: String,                            // Название pipeline
    val description: String,                     // Описание
    val assistantIds: List<String>,              // Список ID ассистентов (по порядку)
    val providerId: ProviderType = CLAUDE,       // AI provider (CLAUDE, OPENAI, etc.)
    val model: String? = null,                   // Конкретная модель (опционально)
    val defaultParameters: RequestParameters,    // Параметры по умолчанию
    val createdAt: Long,                         // Timestamp создания
    val updatedAt: Long                          // Timestamp обновления
) {
    companion object {
        const val MAX_PIPELINE_LENGTH = 10       // Максимум 10 ассистентов
    }
}
```

**Валидация:**
- `assistantIds.size` должен быть в диапазоне 1..10
- Все assistant IDs должны существовать в системе

### ExecutePipelineRequest

Запрос на выполнение pipeline.

```kotlin
data class ExecutePipelineRequest(
    val initialMessage: String,                  // Первоначальное сообщение

    // Один из двух вариантов (взаимоисключающие):
    val pipelineId: String? = null,              // ID сохранённого pipeline
    val assistantIds: List<String>? = null,      // Или список ассистентов (ad-hoc)

    val providerId: ProviderType = CLAUDE,       // Provider
    val model: String? = null,                   // Модель (опционально)
    val parameters: RequestParameters = RequestParameters(),

    // Опции сохранения:
    val savePipeline: Boolean = false,           // Сохранить как конфигурацию
    val pipelineName: String? = null,            // Название при сохранении
    val pipelineDescription: String? = null      // Описание при сохранении
)
```

**Варианты использования:**

1. **Выполнить сохранённый pipeline:**
```json
{
  "pipelineId": "education-pipeline-123",
  "initialMessage": "Explain quantum computing"
}
```

2. **Выполнить ad-hoc pipeline:**
```json
{
  "assistantIds": ["greeting-assistant", "ai-tutor"],
  "initialMessage": "Teach me about async/await"
}
```

3. **Выполнить и сохранить:**
```json
{
  "assistantIds": ["validator", "analyzer"],
  "initialMessage": "Check this code",
  "savePipeline": true,
  "pipelineName": "Code Review Pipeline"
}
```

### PipelineExecution

Runtime состояние выполнения pipeline.

```kotlin
data class PipelineExecution(
    val id: String,                              // Уникальный ID выполнения
    val pipelineId: String?,                     // ID конфигурации (если saved)
    val pipelineName: String,                    // Название pipeline
    val sessionId: String,                       // ID chat session
    val initialMessage: String,                  // Первоначальное сообщение
    val assistantIds: List<String>,              // Список ассистентов
    val providerId: ProviderType,                // Provider
    val model: String,                           // Модель
    val parameters: RequestParameters,           // Параметры
    val steps: List<AssistantStep>,              // Список выполненных шагов
    val status: PipelineExecutionStatus,         // Статус выполнения
    val startTime: Long,                         // Timestamp начала
    val endTime: Long? = null,                   // Timestamp завершения
    val error: PipelineError? = null             // Информация об ошибке
)
```

### AssistantStep

Результат выполнения одного шага в pipeline.

```kotlin
data class AssistantStep(
    val stepIndex: Int,                          // Индекс шага (0-based)
    val assistantId: String,                     // ID ассистента
    val assistantName: String,                   // Название ассистента
    val input: String,                           // Входные данные
    val output: String,                          // Выходные данные
    val tokensUsed: TokenUsage,                  // Использованные токены
    val executionTimeMs: Long,                   // Время выполнения (мс)
    val timestamp: Long,                         // Timestamp выполнения
    val error: PipelineError? = null             // Ошибка (если есть)
)
```

### PipelineExecutionStatus

Статусы выполнения pipeline.

```kotlin
enum class PipelineExecutionStatus {
    PENDING,        // Ожидает выполнения
    IN_PROGRESS,    // Выполняется
    COMPLETED,      // Успешно завершён (все шаги)
    FAILED,         // Провалился на первом шаге
    PARTIAL         // Частично выполнен (были успешные шаги, но произошла ошибка)
}
```

### PipelineExecutionResult

Финальный результат выполнения (возвращается API).

```kotlin
data class PipelineExecutionResult(
    val executionId: String,                     // ID выполнения
    val pipelineId: String?,                     // ID конфигурации
    val pipelineName: String,                    // Название
    val sessionId: String,                       // ID сессии
    val status: PipelineExecutionStatus,         // Статус
    val steps: List<AssistantStepResult>,        // Результаты шагов
    val finalOutput: String?,                    // Финальный результат
    val totalTokensUsed: TokenUsage,             // Всего токенов
    val totalExecutionTimeMs: Long,              // Общее время выполнения
    val error: PipelineError?                    // Ошибка (если есть)
)
```

---

## REST API

Базовый путь: `/api/v2/pipeline`

### 1. Выполнить Pipeline

#### POST `/api/v2/pipeline/execute`

Выполнить ad-hoc или сохранённый pipeline.

**Request:**
```json
{
  "initialMessage": "Explain quantum computing",
  "assistantIds": ["greeting-assistant", "ai-tutor"],
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5-20250929",
  "parameters": {
    "temperature": 1.0,
    "maxTokens": 8192
  }
}
```

**Response (200 OK):**
```json
{
  "executionId": "exec-123-456",
  "pipelineId": null,
  "pipelineName": "Ad-hoc Pipeline",
  "sessionId": "session-789",
  "status": "COMPLETED",
  "steps": [
    {
      "stepIndex": 0,
      "assistantId": "greeting-assistant",
      "assistantName": "Ассистент Приветствия",
      "input": "Explain quantum computing",
      "output": "Hello! I'd be happy to explain quantum computing...",
      "tokensUsed": {
        "inputTokens": 15,
        "outputTokens": 250,
        "totalTokens": 265
      },
      "executionTimeMs": 1500
    },
    {
      "stepIndex": 1,
      "assistantId": "ai-tutor",
      "assistantName": "AI Репетитор",
      "input": "Hello! I'd be happy to explain quantum computing...",
      "output": "Let me break down quantum computing for you...",
      "tokensUsed": {
        "inputTokens": 250,
        "outputTokens": 500,
        "totalTokens": 750
      },
      "executionTimeMs": 2800
    }
  ],
  "finalOutput": "Let me break down quantum computing for you...",
  "totalTokensUsed": {
    "inputTokens": 265,
    "outputTokens": 750,
    "totalTokens": 1015
  },
  "totalExecutionTimeMs": 4300,
  "error": null
}
```

**Error Response (500 Internal Server Error):**
```json
{
  "error": "Pipeline execution failed: Assistant 'ai-tutor' not found"
}
```

---

#### POST `/api/v2/pipeline/execute/{id}`

Выполнить сохранённый pipeline по ID.

**Request:**
```json
{
  "initialMessage": "Teach me about async/await",
  "model": "claude-sonnet-4-5-20250929",
  "parameters": {
    "temperature": 0.7
  }
}
```

**Response:** Аналогичен `/execute`

---

### 2. Управление конфигурациями

#### POST `/api/v2/pipeline/config`

Создать или обновить конфигурацию pipeline.

**Request:**
```json
{
  "id": "education-pipeline",
  "name": "Education Pipeline",
  "description": "Greeting + AI Tutor for educational content",
  "assistantIds": ["greeting-assistant", "ai-tutor"],
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5-20250929",
  "defaultParameters": {
    "temperature": 1.0,
    "maxTokens": 8192
  }
}
```

**Response (200 OK):**
```json
{
  "id": "education-pipeline",
  "name": "Education Pipeline",
  "description": "Greeting + AI Tutor for educational content",
  "assistantIds": ["greeting-assistant", "ai-tutor"],
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5-20250929",
  "defaultParameters": {
    "temperature": 1.0,
    "maxTokens": 8192
  },
  "createdAt": 1732108800000,
  "updatedAt": 1732108800000
}
```

---

#### GET `/api/v2/pipeline/configs`

Получить список всех сохранённых pipelines.

**Response (200 OK):**
```json
{
  "pipelines": [
    {
      "id": "education-pipeline",
      "name": "Education Pipeline",
      "description": "Greeting + AI Tutor",
      "assistantCount": 2,
      "providerId": "CLAUDE",
      "createdAt": 1732108800000,
      "updatedAt": 1732108800000
    },
    {
      "id": "code-review-pipeline",
      "name": "Code Review Pipeline",
      "description": "Validator + Analyzer",
      "assistantCount": 2,
      "providerId": "CLAUDE",
      "createdAt": 1732108900000,
      "updatedAt": 1732108900000
    }
  ]
}
```

---

#### GET `/api/v2/pipeline/config/{id}`

Получить конкретную конфигурацию pipeline.

**Response (200 OK):**
```json
{
  "id": "education-pipeline",
  "name": "Education Pipeline",
  "description": "Greeting + AI Tutor for educational content",
  "assistantIds": ["greeting-assistant", "ai-tutor"],
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5-20250929",
  "defaultParameters": {
    "temperature": 1.0,
    "maxTokens": 8192
  },
  "createdAt": 1732108800000,
  "updatedAt": 1732108800000
}
```

**Error (404 Not Found):**
```json
{
  "error": "Pipeline not found"
}
```

---

#### DELETE `/api/v2/pipeline/config/{id}`

Удалить конфигурацию pipeline.

**Response (200 OK):**
```json
{
  "message": "Pipeline deleted"
}
```

---

### 3. История выполнений

#### GET `/api/v2/pipeline/executions`

Получить историю выполнений.

**Query Parameters:**
- `pipelineId` (optional) - фильтр по ID pipeline
- `limit` (optional, default: 50) - количество записей

**Examples:**
```bash
# Все выполнения (последние 50)
GET /api/v2/pipeline/executions

# Выполнения конкретного pipeline
GET /api/v2/pipeline/executions?pipelineId=education-pipeline

# Последние 10 выполнений
GET /api/v2/pipeline/executions?limit=10
```

**Response (200 OK):**
```json
{
  "executions": [
    {
      "id": "exec-123",
      "pipelineId": "education-pipeline",
      "pipelineName": "Education Pipeline",
      "sessionId": "session-789",
      "status": "COMPLETED",
      "startTime": 1732108900000,
      "endTime": 1732108904300,
      "totalExecutionTimeMs": 4300
    },
    {
      "id": "exec-124",
      "pipelineId": "education-pipeline",
      "pipelineName": "Education Pipeline",
      "sessionId": "session-790",
      "status": "PARTIAL",
      "startTime": 1732109000000,
      "endTime": 1732109002100,
      "totalExecutionTimeMs": 2100
    }
  ]
}
```

---

#### GET `/api/v2/pipeline/execution/{id}`

Получить детали конкретного выполнения.

**Response (200 OK):**
```json
{
  "id": "exec-123",
  "pipelineId": "education-pipeline",
  "pipelineName": "Education Pipeline",
  "sessionId": "session-789",
  "initialMessage": "Explain quantum computing",
  "assistantIds": ["greeting-assistant", "ai-tutor"],
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5-20250929",
  "parameters": {...},
  "steps": [...],
  "status": "COMPLETED",
  "startTime": 1732108900000,
  "endTime": 1732108904300,
  "error": null
}
```

**Error (404 Not Found):**
```json
{
  "error": "Execution not found"
}
```

---

## Примеры использования

### Пример 1: Простой educational pipeline

**Шаг 1: Создать конфигурацию**

```bash
curl -X POST http://localhost:8080/api/v2/pipeline/config \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Learning Assistant",
    "description": "Helps users learn new topics",
    "assistantIds": ["greeting-assistant", "ai-tutor"],
    "providerId": "CLAUDE"
  }'
```

**Шаг 2: Выполнить pipeline**

```bash
curl -X POST http://localhost:8080/api/v2/pipeline/execute/{pipeline-id} \
  -H "Content-Type: application/json" \
  -d '{
    "initialMessage": "I want to learn about machine learning"
  }'
```

**Результат:**
1. Greeting Assistant приветствует и подготавливает контекст
2. AI Tutor создаёт персонализированный учебный план
3. Оба ответа сохраняются в одной сессии

---

### Пример 2: Ad-hoc pipeline для code review

```bash
curl -X POST http://localhost:8080/api/v2/pipeline/execute \
  -H "Content-Type: application/json" \
  -d '{
    "initialMessage": "function add(a, b) { return a + b }",
    "assistantIds": [
      "syntax-validator",
      "security-analyzer",
      "performance-analyzer"
    ],
    "providerId": "CLAUDE",
    "savePipeline": true,
    "pipelineName": "Code Review Pipeline"
  }'
```

**Поток выполнения:**
1. Syntax Validator проверяет синтаксис
2. Security Analyzer ищет уязвимости
3. Performance Analyzer даёт рекомендации
4. Pipeline автоматически сохраняется для повторного использования

---

### Пример 3: Получение истории

```bash
# Получить все выполнения education pipeline
curl http://localhost:8080/api/v2/pipeline/executions?pipelineId=education-pipeline

# Получить детали конкретного выполнения
curl http://localhost:8080/api/v2/pipeline/execution/exec-123
```

---

## Персистентность

### Структура файлов

```
data/
├── assistant_pipelines/          # Конфигурации pipelines
│   ├── education-pipeline.json
│   ├── code-review-pipeline.json
│   └── research-pipeline.json
│
└── pipeline_executions/          # История выполнений
    ├── exec-123-456.json
    ├── exec-124-457.json
    └── exec-125-458.json
```

### Формат JSON - Pipeline Config

```json
{
  "id": "education-pipeline",
  "name": "Education Pipeline",
  "description": "Greeting + AI Tutor for educational content",
  "assistantIds": ["greeting-assistant", "ai-tutor"],
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5-20250929",
  "defaultParameters": {
    "temperature": 1.0,
    "maxTokens": 8192,
    "topP": 1.0
  },
  "createdAt": 1732108800000,
  "updatedAt": 1732108800000
}
```

### Формат JSON - Execution

```json
{
  "id": "exec-123-456",
  "pipelineId": "education-pipeline",
  "pipelineName": "Education Pipeline",
  "sessionId": "session-789",
  "initialMessage": "Explain quantum computing",
  "assistantIds": ["greeting-assistant", "ai-tutor"],
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5-20250929",
  "parameters": {...},
  "steps": [
    {
      "stepIndex": 0,
      "assistantId": "greeting-assistant",
      "assistantName": "Ассистент Приветствия",
      "input": "Explain quantum computing",
      "output": "Hello! I'd be happy to explain...",
      "tokensUsed": {...},
      "executionTimeMs": 1500,
      "timestamp": 1732108900000,
      "error": null
    }
  ],
  "status": "COMPLETED",
  "startTime": 1732108900000,
  "endTime": 1732108904300,
  "error": null
}
```

### Atomic Writes

JsonPipelineStorage использует атомарную запись через temporary файлы:

```kotlin
// 1. Записать во временный файл
tempFile.writeText(json.encodeToString(pipeline))

// 2. Atomic move
Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
```

Это гарантирует:
- Нет частично записанных файлов
- Нет потери данных при крэше
- Thread-safety

---

## Интеграция с сессиями

### Session Fields

Pipeline выполнение связано с chat session через дополнительные поля:

```kotlin
data class ChatSession(
    // ... existing fields ...
    val pipelineExecutionId: String? = null,     // ID выполнения pipeline
    val currentPipelineStep: Int? = null         // Текущий шаг (0-based)
)
```

### Message Metadata

Каждое сообщение в сессии содержит pipeline metadata:

```kotlin
data class MessageMetadata(
    // ... existing fields ...
    val pipelineExecutionId: String? = null,     // ID выполнения
    val pipelineStepIndex: Int? = null,          // Индекс шага
    val pipelineAssistantId: String? = null      // ID ассистента
)
```

### Lifecycle

**1. Создание сессии:**
```kotlin
val session = sessionRepository.createSession(providerId)
val updatedSession = session.copy(
    pipelineExecutionId = executionId,
    currentPipelineStep = 0
)
sessionRepository.updateSession(updatedSession)
```

**2. Выполнение шагов:**
```kotlin
for ((index, assistantId) in assistantIds.withIndex()) {
    // Execute step
    val result = sendMessageUseCase(...)

    // Update session step
    session.currentPipelineStep = index + 1
    sessionRepository.updateSession(session)
}
```

**3. Завершение:**
```kotlin
// Session сохраняется автоматически через PersistenceManager
// Execution history сохраняется в pipelineStorage
pipelineStorage.saveExecution(execution)
```

---

## Обработка ошибок

### Стратегия: Stop on First Error

При возникновении ошибки на любом шаге:
1. Pipeline немедленно останавливается
2. Возвращаются частичные результаты успешных шагов
3. Сохраняется детальная информация об ошибке

### Типы ошибок

#### 1. Assistant Not Found

```json
{
  "status": "FAILED",
  "steps": [],
  "error": {
    "message": "Assistant 'unknown-assistant' not found",
    "stepIndex": 0,
    "assistantId": "unknown-assistant"
  }
}
```

#### 2. Execution Error

```json
{
  "status": "PARTIAL",
  "steps": [
    {
      "stepIndex": 0,
      "assistantId": "greeting-assistant",
      "output": "Hello!",
      "error": null
    }
  ],
  "error": {
    "message": "API rate limit exceeded",
    "stepIndex": 1,
    "assistantId": "ai-tutor"
  }
}
```

#### 3. Timeout

```json
{
  "status": "PARTIAL",
  "steps": [...],
  "error": {
    "message": "Pipeline execution timeout (300000ms)",
    "stepIndex": 5
  }
}
```

### Error Response Codes

| Status Code | Описание |
|-------------|----------|
| 200 OK | Pipeline выполнен (может быть PARTIAL с ошибкой) |
| 400 Bad Request | Невалидный request (missing fields, validation) |
| 404 Not Found | Pipeline config/execution не найден |
| 500 Internal Server Error | Неожиданная ошибка сервера |

---

## Ограничения и лимиты

### Системные лимиты

| Параметр | Значение | Описание |
|----------|----------|----------|
| **MAX_PIPELINE_LENGTH** | 10 | Максимум ассистентов в цепочке |
| **MAX_EXECUTION_TIME** | 300,000 ms (5 min) | Timeout для всей цепочки |
| **Storage Format** | JSON | Формат персистентности |

### Валидация

**AssistantPipeline:**
```kotlin
init {
    require(assistantIds.size in 1..MAX_PIPELINE_LENGTH) {
        "Pipeline must contain 1-$MAX_PIPELINE_LENGTH assistants"
    }
}
```

**ExecutePipelineRequest:**
```kotlin
init {
    require(pipelineId != null || assistantIds != null) {
        "Either pipelineId or assistantIds must be provided"
    }
    require(!(pipelineId != null && assistantIds != null)) {
        "Only one of pipelineId or assistantIds can be provided"
    }
}
```

### Рекомендации

**Оптимальная длина цепочки:** 2-5 ассистентов
- Лучший баланс между функциональностью и производительностью
- Меньше вероятность timeout
- Проще отладка

**Обработка timeout:**
- Разбейте длинные цепочки на несколько коротких
- Используйте асинхронное выполнение (future feature)
- Оптимизируйте system prompts ассистентов

---

## Расширение функциональности

### Потенциальные улучшения

#### 1. Parallel Execution

Выполнение нескольких ассистентов параллельно:

```json
{
  "pipeline": [
    {"assistantId": "greeting-assistant"},
    {
      "parallel": [
        {"assistantId": "analyzer-1"},
        {"assistantId": "analyzer-2"}
      ]
    },
    {"assistantId": "aggregator"}
  ]
}
```

#### 2. Conditional Branching

Условное выполнение на основе результатов:

```json
{
  "steps": [
    {"assistantId": "validator"},
    {
      "condition": "validation.success",
      "then": {"assistantId": "processor"},
      "else": {"assistantId": "error-handler"}
    }
  ]
}
```

#### 3. Retry Mechanism

Автоматический retry при ошибках:

```json
{
  "retryPolicy": {
    "maxAttempts": 3,
    "backoffMs": 1000,
    "retryOn": ["rate_limit", "timeout"]
  }
}
```

#### 4. Streaming Support

Потоковая передача результатов:

```http
GET /api/v2/pipeline/execute/{id}/stream
Content-Type: text/event-stream

data: {"stepIndex": 0, "status": "in_progress"}
data: {"stepIndex": 0, "status": "completed", "output": "..."}
data: {"stepIndex": 1, "status": "in_progress"}
```

#### 5. Pipeline Templates

Готовые шаблоны для типовых задач:

```bash
POST /api/v2/pipeline/templates/apply
{
  "templateId": "code-review",
  "parameters": {
    "language": "kotlin",
    "strictness": "high"
  }
}
```

#### 6. Monitoring & Analytics

Dashboard с метриками:
- Среднее время выполнения
- Success/failure rate
- Использование токенов
- Популярные pipelines

---

## Примеры сложных сценариев

### Пример 1: Research + Analysis + Report

```json
{
  "name": "Research Pipeline",
  "assistantIds": [
    "web-researcher",      // Собирает информацию
    "data-analyzer",       // Анализирует данные
    "fact-checker",        // Проверяет факты
    "report-writer",       // Пишет отчёт
    "editor"               // Редактирует текст
  ],
  "initialMessage": "Research the impact of AI on education"
}
```

**Результат:**
Полный исследовательский отчёт с проверенными фактами и профессиональным форматированием.

---

### Пример 2: Multi-language Translation Chain

```json
{
  "name": "Translation Pipeline",
  "assistantIds": [
    "en-to-es-translator",    // English → Spanish
    "es-to-fr-translator",    // Spanish → French
    "fr-to-de-translator",    // French → German
    "de-quality-checker"      // Quality check
  ],
  "initialMessage": "The quick brown fox jumps over the lazy dog"
}
```

---

### Пример 3: Content Creation Workflow

```json
{
  "name": "Blog Post Generator",
  "assistantIds": [
    "topic-researcher",       // Исследует тему
    "outline-creator",        // Создаёт структуру
    "content-writer",         // Пишет контент
    "seo-optimizer",          // SEO оптимизация
    "image-suggester",        // Предлагает изображения
    "meta-generator"          // Создаёт meta tags
  ],
  "initialMessage": "Write blog post about sustainable living"
}
```

---

## Troubleshooting

### Проблема: Pipeline timeout

**Симптомы:**
```json
{
  "error": {
    "message": "Pipeline execution timeout (300000ms)"
  }
}
```

**Решения:**
1. Уменьшите количество ассистентов
2. Оптимизируйте system prompts
3. Уменьшите maxTokens параметр
4. Разбейте на несколько коротких pipelines

---

### Проблема: Assistant not found

**Симптомы:**
```json
{
  "error": {
    "message": "Assistant 'my-assistant' not found"
  }
}
```

**Решения:**
1. Проверьте ID ассистента: `GET /api/v2/assistants`
2. Убедитесь, что ассистент зарегистрирован в AssistantManager
3. Проверьте spelling ID

---

### Проблема: Partial execution

**Симптомы:**
```json
{
  "status": "PARTIAL",
  "steps": [...]
}
```

**Причины:**
- API rate limit
- Network timeout
- Assistant error
- Context window overflow

**Решения:**
1. Проверьте error details
2. Retry с меньшими параметрами
3. Добавьте обработку ошибок в ассистента

---

## Best Practices

### 1. Naming Conventions

```kotlin
// Pipeline IDs: kebab-case
"education-pipeline"
"code-review-pipeline"

// Pipeline Names: Title Case
"Education Pipeline"
"Code Review Pipeline"

// Assistant IDs: kebab-case
"greeting-assistant"
"ai-tutor"
```

### 2. Организация ассистентов

**Хорошо:**
```
1. Input Validator
2. Main Processor
3. Output Formatter
```

**Плохо:**
```
1. General Assistant
2. Another General Assistant
3. Yet Another Assistant
```

### 3. Error Handling

```kotlin
// В ассистенте всегда валидируйте вход
if (input.isBlank()) {
    return "Error: Empty input"
}

// Предоставляйте чёткие error messages
return "Error: Invalid JSON format at line 5"
```

### 4. Documentation

Документируйте каждый pipeline:

```json
{
  "name": "Code Review Pipeline",
  "description": "Validates syntax → Checks security → Analyzes performance",
  "expectedInput": "Source code in any language",
  "expectedOutput": "Comprehensive code review report"
}
```

---

## Заключение

Assistant Pipeline - это мощный инструмент для создания сложных AI workflows. Система предоставляет:

✅ **Гибкость** - ad-hoc и сохранённые pipelines
✅ **Надёжность** - error handling и timeout protection
✅ **Персистентность** - история и конфигурации
✅ **Простота** - REST API для интеграции
✅ **Масштабируемость** - до 10 ассистентов в цепочке

**Готово к production использованию!**

---

**Дополнительная документация:**
- [Task Scheduler](./TASK_SCHEDULER.md)
- [MCP Integration](./MCP.md)
- [Authentication](./AUTH.md)

**API Reference:**
- [OpenAPI Specification](../api/openapi.yaml)
- [Postman Collection](../api/postman_collection.json)
