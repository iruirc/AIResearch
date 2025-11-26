# Тестовый набор для Ranking vs Reranking

Этот набор данных предназначен для демонстрации разницы между простым ранжированием (cosine similarity) и реранжированием (cross-encoder) в RAG системе.

## Структура папки

```
Reranking/
├── README.md                      # Этот файл
├── RERANKING_TEST_DISCUSSION.md   # Обсуждение подхода к тестированию
├── test_documents.json            # 9 тестовых документов для загрузки в RAG
└── test_queries.json              # 10 тестовых запросов с ожидаемыми результатами
```

## Тестовые сценарии

| Сценарий | Описание | Документы |
|----------|----------|-----------|
| `lexical-vs-semantic` | Лексическое vs семантическое сходство | python-frameworks, python-datascience, python-history |
| `negative-context` | Понимание негативного контекста | microservices-antipatterns, microservices-benefits |
| `disambiguation` | Разрешение многозначности (Apple) | apple-company, apple-fruit |
| `complex-query` | Сложные многошаговые запросы | databases-overview, mysql-to-postgresql |

## Как использовать

### 1. Загрузка документов в RAG

Используйте API для загрузки каждого документа из `test_documents.json`:

```bash
# Пример для одного документа
curl -X POST http://localhost:8080/rag/documents \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Python Frameworks",
    "content": "Django — это высокоуровневый...",
    "chunkingStrategy": "SEMANTIC",
    "enabled": true
  }'
```

### 2. Выполнение тестовых запросов

Для каждого запроса из `test_queries.json`:

```bash
# Запрос с обычным ранжированием
curl -X POST http://localhost:8080/rag/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Какой Python фреймворк для REST API?",
    "topK": 5,
    "minScore": 0.5
  }'

# Запрос с реранжированием (когда будет реализовано)
curl -X POST http://localhost:8080/rag/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Какой Python фреймворк для REST API?",
    "topK": 5,
    "minScore": 0.5,
    "rerank": true
  }'
```

### 3. Оценка результатов

Сравните результаты с полем `expectedTopResult` в `test_queries.json`.

**Метрики:**
- **Precision@1**: Правильный ли первый результат?
- **MRR**: На какой позиции правильный результат?
- **Improvement**: Насколько reranking улучшил позицию?

## Ожидаемые результаты

| Запрос | Ranking (bi-encoder) | Reranking (cross-encoder) |
|--------|---------------------|---------------------------|
| Python фреймворк для REST API | Может вернуть Data Science | FastAPI (correct) |
| Микросервисы для 3 человек | Преимущества микросервисов | Когда НЕ использовать (correct) |
| Новости Apple | 50/50 компания/фрукт | Apple Inc. (correct) |
| Калории в Apple | 50/50 компания/фрукт | Яблоки в питании (correct) |
| Миграция MySQL→PostgreSQL | Обзор баз данных | Миграция (correct) |

## Примечания

- Документы написаны на русском языке
- Используйте embedding модель с хорошей поддержкой русского (multilingual-e5, nomic-embed-text)
- Для cross-encoder рекомендуется использовать модели: `cross-encoder/ms-marco-MiniLM-L-6-v2` или `BAAI/bge-reranker-base`

---

**Создано:** 2025-11-26
