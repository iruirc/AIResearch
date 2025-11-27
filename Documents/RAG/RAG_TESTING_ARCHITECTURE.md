# RAG Testing Architecture

Документация по архитектуре тестирования RAG-системы в ResearchAI.

## Обзор

Система тестирования RAG позволяет создавать и выполнять тесты для оценки качества поиска и ранжирования документов. Каждый тест выполняется в двух режимах:
1. **Без реранжирования** - только векторный поиск (Stage 1)
2. **С реранжированием** - двухэтапный pipeline (Stage 1 + Stage 2)

---

## 1. Модели данных

### RAGTest - Контейнер теста
Расположение: `domain/models/RAGTest.kt`

```kotlin
data class RAGTest(
    val id: String,                              // Уникальный идентификатор
    val name: String,                            // Название теста
    val queries: List<RAGTestQuery>,             // Список запросов
    val evaluationMetrics: Map<String, String>?, // Метрики оценки
    val createdAt: Instant,                      // Дата создания
    val updatedAt: Instant                       // Дата обновления
)
```

### RAGTestQuery - Тестовый запрос
```kotlin
data class RAGTestQuery(
    val id: String,                          // ID запроса
    val query: String,                       // Текст запроса
    val explanation: String,                 // Объяснение цели запроса
    val scenario: String?,                   // Контекст сценария
    val expectedTopResult: String?,          // Ожидаемый результат
    val expectedChunkKeywords: List<String>?, // Ключевые слова
    val rankingTrap: String?                 // Потенциальные ловушки
)
```

### TestExecutionResult - Результат выполнения
Расположение: `domain/models/RAGTestExecution.kt`

```kotlin
data class TestExecutionResult(
    val testId: String,
    val testName: String,
    val sessionId: String,
    val results: List<QueryExecutionResult>,  // Результаты по запросам
    val totalTimeMs: Long,
    val executedAt: Instant,
    val provider: String,
    val model: String,
    val cancelled: Boolean
)
```

### QueryExecutionResult - Результат запроса
```kotlin
data class QueryExecutionResult(
    val queryId: String,
    val query: String,
    val explanation: String,
    val withoutReranking: QueryResponseData,  // Ответ БЕЗ реранжирования
    val withReranking: QueryResponseData      // Ответ С реранжированием
)
```

### QueryResponseData - Данные ответа
```kotlin
data class QueryResponseData(
    val response: String,              // Ответ модели
    val elapsedTimeMs: Long,           // Время выполнения
    val tokensUsed: Int?,              // Всего токенов
    val inputTokens: Int?,             // Входные токены
    val outputTokens: Int?,            // Выходные токены
    val chunksCount: Int,              // Количество чанков
    val chunks: List<ChunkInfo>?       // Детали чанков
)
```

### ChunkInfo - Информация о чанке
```kotlin
data class ChunkInfo(
    val documentName: String,          // Название документа
    val chunkIndex: Int,               // Индекс в документе
    val score: Float,                  // Оценка релевантности
    val text: String                   // Текст чанка
)
```

---

## 2. Хранение тестов

### RAGTestStorage Interface
Расположение: `persistence/RAGTestStorage.kt`

```kotlin
interface RAGTestStorage {
    suspend fun save(test: RAGTest)
    suspend fun load(testId: String): RAGTest?
    suspend fun loadAll(): List<RAGTest>
    suspend fun delete(testId: String): Boolean
    suspend fun exists(testId: String): Boolean
    suspend fun existsByName(name: String): Boolean
    suspend fun findByName(name: String): RAGTest?
}
```

### JsonRAGTestStorage Implementation
Расположение: `persistence/JsonRAGTestStorage.kt`

- **Директория хранения**: `data/rag/tests/`
- **Формат файлов**: JSON (`{sanitized_name}.json`)
- **Атомарная запись**: Используется временный файл + переименование
- **Кэширование**: In-memory мапы для быстрого поиска
- **Legacy поддержка**: Автоконвертация старого формата

---

## 3. Сервис выполнения тестов

### RAGTestExecutionService
Расположение: `services/RAGTestExecutionService.kt`

Основной сервис для выполнения тестов с поддержкой:
- Dual-mode выполнения (с/без реранжирования)
- Real-time стриминга через Kotlin Flow
- Graceful отмены выполнения

### Процесс выполнения теста

```
1. Загрузка теста из хранилища
        ↓
2. Создание двух сессий:
   - rag-test-{testId}-no-rerank-{timestamp}
   - rag-test-{testId}-rerank-{timestamp}
        ↓
3. Для каждого запроса:
   a. Emit: QueryProcessingEvent
   b. Выполнение БЕЗ реранжирования
   c. Выполнение С реранжированием
   d. Emit: QueryCompletedEvent
        ↓
4. Формирование TestExecutionResult
        ↓
5. Emit: ExecutionFinishedEvent
```

### События выполнения (TestExecutionEvent)

| Событие | Описание |
|---------|----------|
| `ExecutionStartedEvent` | Начало выполнения |
| `QueryProcessingEvent` | Обработка запроса |
| `QueryCompletedEvent` | Запрос выполнен |
| `QueryErrorEvent` | Ошибка при выполнении |
| `ExecutionFinishedEvent` | Все запросы выполнены |
| `ExecutionCancelledEvent` | Выполнение отменено |

### Управление отменой

```kotlin
// Активные выполнения
val activeExecutions: ConcurrentHashMap<String, Job>

// Отмена выполнения
fun cancelExecution(executionId: String)

// Проверка статуса
fun isExecutionActive(executionId: String): Boolean
```

---

## 4. Двухэтапный поиск и реранжирование

### RAGManager - Режимы поиска

1. **searchRelevantContext()** - Только Stage 1
   - Векторный поиск по эмбеддингам
   - Фильтрация по minScore

2. **searchWithReranking()** - Stage 1 + Stage 2
   - Векторный поиск
   - Применение стратегии реранжирования

3. **compareSearchStrategies()** - Сравнение режимов
   - Один векторный поиск
   - Параллельное сравнение результатов

### Стратегии реранжирования (RerankerStrategy)

| Стратегия | Описание |
|-----------|----------|
| `NONE` | Без реранжирования |
| `SCORE_THRESHOLD` | Фильтрация по порогу (default: 0.75) |
| `STATISTICAL` | Удаление outliers по std dev |
| `CROSS_ENCODER` | LLM-based реранжирование через Ollama |

### RerankerStatistics - Статистика

```kotlin
data class RerankerStatistics(
    val inputCount: Int,           // Входных результатов
    val outputCount: Int,          // Выходных результатов
    val filteredCount: Int,        // Отфильтровано
    val avgScoreBefore: Float,     // Средний score до
    val avgScoreAfter: Float,      // Средний score после
    val scoreStdDev: Float?,       // Стандартное отклонение
    val thresholdUsed: Float?,     // Использованный порог
    val processingTimeMs: Long     // Время обработки
)
```

---

## 5. API Endpoints

### Управление тестами

| Endpoint | Method | Описание |
|----------|--------|----------|
| `/rag/tests` | POST | Создать тест |
| `/rag/tests` | GET | Список тестов |
| `/rag/tests/{id}` | GET | Получить тест |
| `/rag/tests/{id}` | PUT | Обновить тест |
| `/rag/tests/{id}` | DELETE | Удалить тест |
| `/rag/tests/{id}/execute` | GET | Запустить тест (SSE) |
| `/rag/tests/executions/{id}/cancel` | POST | Отменить выполнение |

### Формат SSE ответа

```
Content-Type: text/event-stream
X-Execution-Id: {executionId}

data: {"type":"started","testId":"..."}

data: {"type":"processing","queryIndex":0,"query":"..."}

data: {"type":"completed","result":{...}}

data: {"type":"finished","result":{...}}
```

---

## 6. Frontend интеграция

### ragTestApi.js - API клиент
Расположение: `static/js/api/ragTestApi.js`

```javascript
// CRUD операции
await ragTestApi.loadTests()
await ragTestApi.getTest(testId)
await ragTestApi.addTest(name, queries, metrics)
await ragTestApi.updateTest(testId, ...)
await ragTestApi.deleteTest(testId)

// Выполнение с callbacks
const execution = ragTestApi.executeTest(testId, {
  onStarted: (data) => { ... },
  onProcessing: (data) => { ... },
  onCompleted: (data) => { ... },
  onError: (data) => { ... },
  onFinished: (result) => { ... },
  onCancelled: (result) => { ... }
})

// Отмена
execution.cancel()
```

### RAGModal - UI компонент
Расположение: `static/js/ui/ragModal.js`

Ключевые методы:
- `loadTests()` - Загрузка тестов
- `renderTestsList()` - Отрисовка списка
- `handleExecuteTest(test)` - Запуск теста
- `openExecutionModal(test)` - Модальное окно выполнения
- `updateExecutionProgress()` - Обновление прогресса
- `handleExecutionComplete(result)` - Отображение результатов

### Экспорт результатов

Метод `downloadResults()` генерирует ZIP-архив с тремя файлами:

1. **JSON** (`{testId}-{timestamp}.json`)
   - Полные сырые данные результата

2. **Markdown** (`{testId}-{timestamp}.md`)
   - Детальный отчет с метриками
   - Суммарная статистика токенов и чанков
   - Ответы и чанки по каждому запросу

3. **Simple Markdown** (`{testId}-{timestamp}-comparison.md`)
   - Упрощенное сравнение режимов
   - Запрос → Ответ без реранжирования → Ответ с реранжированием

---

## 7. Диаграмма потока данных

```
┌─────────────────────────────────────────────────────────────────────┐
│                         USER INTERFACE                               │
├─────────────────────────────────────────────────────────────────────┤
│  RAGModal.js                                                         │
│  ├── Создание/редактирование тестов                                 │
│  ├── Запуск выполнения                                               │
│  ├── Отображение прогресса (SSE)                                    │
│  └── Экспорт результатов (ZIP)                                      │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTP/SSE
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         API LAYER                                    │
├─────────────────────────────────────────────────────────────────────┤
│  RAGTestRoutes.kt                                                    │
│  ├── CRUD endpoints для тестов                                      │
│  └── SSE endpoint для выполнения                                    │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       SERVICE LAYER                                  │
├─────────────────────────────────────────────────────────────────────┤
│  RAGTestExecutionService                                             │
│  ├── Оркестрация dual-mode выполнения                               │
│  ├── Управление Flow событиями                                      │
│  └── Обработка отмены                                                │
│                                                                      │
│  SendMessageUseCase                                                  │
│  ├── useRerankingOverride = false → Только Stage 1                  │
│  └── useRerankingOverride = true  → Stage 1 + Stage 2               │
│                                                                      │
│  RAGManager                                                          │
│  ├── searchRelevantContext() → Векторный поиск                      │
│  ├── searchWithReranking()   → Двухэтапный pipeline                 │
│  └── compareSearchStrategies() → Сравнение режимов                  │
│                                                                      │
│  RerankerService                                                     │
│  └── Применение стратегии реранжирования                            │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     PERSISTENCE LAYER                                │
├─────────────────────────────────────────────────────────────────────┤
│  JsonRAGTestStorage                                                  │
│  └── data/rag/tests/*.json                                          │
│                                                                      │
│  ChatSessionManager                                                  │
│  └── data/sessions/*.json (сессии выполнения)                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 8. Ключевые классы и их ответственности

| Класс | Расположение | Ответственность |
|-------|--------------|-----------------|
| `RAGTestExecutionService` | services/ | Оркестрация dual-mode выполнения, управление событиями |
| `RAGManager` | services/ | Двухэтапный поиск, сравнение стратегий |
| `RerankerService` | services/ | Выбор и применение стратегии реранжирования |
| `JsonRAGTestStorage` | persistence/ | Файловое хранение тестов с кэшированием |
| `RAGTestRoutes` | routes/ | API endpoints для тестов |
| `ScoreThresholdReranker` | data/rag/ | Пороговая фильтрация |
| `StatisticalReranker` | data/rag/ | Статистическое реранжирование |
| `CrossEncoderReranker` | data/rag/ | LLM-based реранжирование |
| `RAGModal` | static/js/ui/ | UI для управления тестами |
| `ragTestApi` | static/js/api/ | HTTP клиент для API |

---

## 9. Важные особенности

1. **Dual-Mode Execution** - Каждый запрос выполняется дважды для сравнения
2. **Отдельные сессии** - Избегание контаминации истории между режимами
3. **Real-Time Streaming** - SSE события для мгновенного обновления UI
4. **Graceful Cancellation** - Возможность остановки на любом запросе
5. **Детальное сравнение** - Статистика фильтрации и улучшения качества
6. **Множество стратегий** - Pluggable pattern для разных подходов
7. **Экспорт в ZIP** - JSON + детальный MD + упрощенный MD
8. **Backward Compatibility** - Поддержка legacy формата тестов
