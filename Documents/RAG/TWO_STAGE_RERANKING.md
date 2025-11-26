# Двухэтапная фильтрация релевантности в RAG

## Обзор

Реализована двухэтапная система поиска с переранжированием (reranking) для улучшения качества результатов RAG.

---

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                      Пользовательский запрос                 │
└─────────────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│              ЭТАП 1: Vector Search (Retrieval)              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  • Генерация embedding запроса (nomic-embed-text)   │   │
│  │  • Cosine similarity с чанками документов           │   │
│  │  • Фильтр по minScore (default: 0.7)               │   │
│  │  • Возврат topK результатов (default: 5)           │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│              ЭТАП 2: Reranking (Filtering)                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Стратегии:                                          │   │
│  │  • NONE - без переранжирования                       │   │
│  │  • SCORE_THRESHOLD - фильтр по вторичному порогу    │   │
│  │  • STATISTICAL - статистический фильтр              │   │
│  │  • CROSS_ENCODER - LLM-based переранжирование       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│                    Финальные результаты                      │
│           (с метаданными и статистикой фильтрации)           │
└─────────────────────────────────────────────────────────────┘
```

---

## Стратегии переранжирования

### 1. NONE (без фильтрации)

Возвращает результаты первого этапа без изменений.

**Когда использовать:**
- Когда первого этапа достаточно
- Для сравнения с другими стратегиями

---

### 2. SCORE_THRESHOLD (порог релевантности)

Простой фильтр по вторичному порогу `secondaryThreshold`.

**Параметры:**
| Параметр | Default | Описание |
|----------|---------|----------|
| `secondaryThreshold` | 0.75 | Минимальный score после первого этапа |

**Алгоритм:**
```
results.filter { score >= secondaryThreshold }
```

**Когда использовать:**
- Когда нужно убрать "серые зоны" (0.7-0.75)
- Быстрый и предсказуемый результат

---

### 3. STATISTICAL (статистический фильтр)

Динамический порог на основе распределения scores.

**Параметры:**
| Параметр | Default | Описание |
|----------|---------|----------|
| `stdDevMultiplier` | 1.0 | Множитель стандартного отклонения |
| `minResultsToKeep` | 1 | Минимум результатов для сохранения |

**Алгоритм:**
```kotlin
val mean = scores.average()
val stdDev = calculateStdDev(scores)
val threshold = mean - (stdDevMultiplier * stdDev)

results.filter { score >= threshold }
```

**Когда использовать:**
- Когда scores распределены неравномерно
- Для автоматического определения "выбросов"

---

### 4. CROSS_ENCODER (LLM-переранжирование)

Использует LLM (Ollama) для оценки релевантности каждого результата.

**Параметры:**
| Параметр | Default | Описание |
|----------|---------|----------|
| `crossEncoderModel` | llama3.2:latest | Модель Ollama для оценки |
| `crossEncoderMinScore` | 6.0 | Минимальный score (0-10 scale) |

**Алгоритм:**
1. Для каждого результата отправляем запрос к LLM:
   ```
   Query: <query>
   Document: <chunk_text>
   Rate relevance 0-10:
   ```
2. Парсим ответ LLM (число 0-10)
3. Фильтруем по `crossEncoderMinScore`
4. Сортируем по новому score

**Когда использовать:**
- Когда нужна максимальная точность
- Для сложных запросов с неоднозначной семантикой
- **Внимание**: медленнее в ~10x из-за LLM calls

---

## API Endpoints

### Двухэтапный поиск

```http
POST /rag/search/rerank
Content-Type: application/json

{
  "query": "Python web frameworks",
  "topK": 10,
  "minScore": 0.6,
  "rerankerStrategy": "SCORE_THRESHOLD",
  "secondaryThreshold": 0.75
}
```

**Ответ:**
```json
{
  "results": [...],
  "originalResults": [...],
  "strategy": "SCORE_THRESHOLD",
  "statistics": {
    "inputCount": 10,
    "outputCount": 5,
    "filteredCount": 5,
    "avgScoreBefore": 0.72,
    "avgScoreAfter": 0.82,
    "thresholdUsed": 0.75,
    "processingTimeMs": 2
  }
}
```

---

### Сравнение стратегий

```http
POST /rag/search/compare
Content-Type: application/json

{
  "query": "Django vs Flask",
  "topK": 10,
  "minScore": 0.6,
  "rerankerStrategy": "STATISTICAL",
  "stdDevMultiplier": 1.5
}
```

**Ответ:**
```json
{
  "query": "Django vs Flask",
  "withoutReranking": {...},
  "withReranking": {...},
  "comparison": {
    "removedCount": 3,
    "removedResults": [...],
    "keptCount": 7,
    "reorderedCount": 2,
    "qualityImprovement": 0.08,
    "processingOverheadMs": 5
  }
}
```

---

### Доступные стратегии

```http
GET /rag/reranker/strategies
```

**Ответ:**
```json
[
  {
    "name": "NONE",
    "description": "No reranking - use first-stage results as-is"
  },
  {
    "name": "SCORE_THRESHOLD",
    "description": "Score threshold filter - removes results below secondary threshold"
  },
  {
    "name": "STATISTICAL",
    "description": "Statistical filter - removes outliers based on score distribution"
  },
  {
    "name": "CROSS_ENCODER",
    "description": "Cross-encoder reranker - uses LLM to rerank results (slower but more accurate)"
  }
]
```

---

## Настройка порогов

### Рекомендуемые значения

| Сценарий | minScore (Stage 1) | Strategy | Secondary |
|----------|-------------------|----------|-----------|
| Широкий поиск | 0.5 | SCORE_THRESHOLD | 0.65 |
| Стандартный | 0.7 | SCORE_THRESHOLD | 0.75 |
| Строгий | 0.7 | STATISTICAL | stdDev=1.5 |
| Максимальная точность | 0.6 | CROSS_ENCODER | minScore=7.0 |

### Как подобрать пороги

1. **Начните с `/rag/search/compare`** для анализа распределения scores
2. **Посмотрите на `removedResults`** - если удаляются релевантные, понизьте порог
3. **Посмотрите на `qualityImprovement`** - если отрицательный, стратегия не подходит
4. **Для CROSS_ENCODER**: начните с `crossEncoderMinScore: 5.0` и повышайте

---

## Примеры использования

### Пример 1: Базовый двухэтапный поиск

```bash
curl -X POST http://localhost:8080/rag/search/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "query": "как создать API на Python",
    "topK": 10,
    "minScore": 0.6,
    "rerankerStrategy": "SCORE_THRESHOLD",
    "secondaryThreshold": 0.7
  }'
```

### Пример 2: Статистический фильтр

```bash
curl -X POST http://localhost:8080/rag/search/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Django ORM queries",
    "topK": 15,
    "minScore": 0.5,
    "rerankerStrategy": "STATISTICAL",
    "stdDevMultiplier": 1.0,
    "minResultsToKeep": 3
  }'
```

### Пример 3: LLM-переранжирование

```bash
curl -X POST http://localhost:8080/rag/search/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "query": "best practices for REST API design",
    "topK": 10,
    "minScore": 0.5,
    "rerankerStrategy": "CROSS_ENCODER",
    "crossEncoderModel": "llama3.2:latest",
    "crossEncoderMinScore": 6.0
  }'
```

---

## Сравнение качества

### Без фильтра vs С фильтром

| Метрика | Без фильтра | SCORE_THRESHOLD | STATISTICAL | CROSS_ENCODER |
|---------|-------------|-----------------|-------------|---------------|
| Precision | Средняя | Выше | Выше | Высокая |
| Recall | Высокий | Ниже | Адаптивный | Адаптивный |
| Latency | ~100ms | +2ms | +5ms | +500-2000ms |
| Сложность | Простая | Простая | Средняя | Высокая |

### Когда какую стратегию использовать

```
Скорость важнее качества? → NONE или SCORE_THRESHOLD
Неравномерное распределение scores? → STATISTICAL
Нужна максимальная точность? → CROSS_ENCODER
Неизвестный домен? → Начните с SCORE_THRESHOLD, тюньте пороги
```

---

## Файлы реализации

| Компонент | Путь |
|-----------|------|
| RerankerConfig | `domain/models/RerankerConfig.kt` |
| Reranker Interface | `domain/rag/Reranker.kt` |
| ScoreThresholdReranker | `data/rag/ScoreThresholdReranker.kt` |
| StatisticalReranker | `data/rag/StatisticalReranker.kt` |
| CrossEncoderReranker | `data/rag/CrossEncoderReranker.kt` |
| RerankerService | `services/RerankerService.kt` |
| RAGManager (updated) | `services/RAGManager.kt` |
| RAGRoutes (updated) | `routes/RAGRoutes.kt` |

---

*Дата создания: 2025-11-26*
