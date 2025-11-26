# Фильтрация релевантности в RAG

## Общая схема RAG в проекте

Проект использует **in-memory vector search** с cosine similarity для семантического поиска по базе знаний.

---

## Как работает фильтр релевантности

### 1. Генерация embedding запроса

Когда пользователь отправляет сообщение, сначала генерируется 768-мерный вектор через Ollama:

```
Запрос: "Python web frameworks"
    ↓
Ollama nomic-embed-text
    ↓
[0.123, 0.456, ..., 0.789]  // 768 floats
```

### 2. Cosine Similarity - основа фильтрации

Для каждого чанка в каждом документе вычисляется косинусное сходство:

```kotlin
fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
    val dotProduct = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
    val magnitudeA = sqrt(a.sumOf { (it * it).toDouble() })
    val magnitudeB = sqrt(b.sumOf { (it * it).toDouble() })
    return dotProduct / (magnitudeA * magnitudeB)
}
```

### 3. Параметры фильтрации

| Параметр | По умолчанию | Назначение |
|----------|--------------|------------|
| `topK` | 5 | Максимальное количество результатов |
| `minScore` | 0.7 | Минимальный порог релевантности |

### 4. Интерпретация score

```
1.0      → Идеальное совпадение
0.7-1.0  → Высокая релевантность (используется для RAG)
0.5-0.7  → Частичное сходство
0.0-0.5  → Разные темы
```

---

## Алгоритм поиска в VectorSearchEngine

```kotlin
fun search(
    query: List<Float>,
    documents: List<RAGDocument>,
    topK: Int = 5,
    minScore: Double = 0.7
): List<SearchResult> {
    // 1. Для КАЖДОГО чанка в КАЖДОМ документе
    //    вычислить cosine similarity

    // 2. Отфильтровать: оставить только score >= minScore

    // 3. Отсортировать по score (убывание)

    // 4. Вернуть первые topK результатов
}
```

---

## Полный flow фильтрации при chat запросе

```
1. POST /chat с сообщением
   ↓
2. RAGManager.search(query, topK=3, minScore=0.7)
   ↓
3. Embedding генерируется для query
   ↓
4. Загружаются ТОЛЬКО enabled документы
   ↓
5. O(n × m) проход: n документов × m чанков
   ↓
6. Фильтр: score >= 0.7
   ↓
7. Сортировка по score DESC
   ↓
8. Берём top 3 результата
   ↓
9. Контекст инжектируется в system prompt
```

---

## Особенности текущей реализации

### Плюсы

- **Простота**: brute-force поиск, легко отлаживать
- **Точность**: 100% recall (проверяются все чанки)

### Ограничения

- Сложность O(n×m) — не масштабируется на миллионы документов
- Нет гибридного поиска (vector + keyword BM25)
- Нет reranking для улучшения top-K

---

## Как настроить фильтрацию

Через API endpoint `/rag/search`:

```json
{
  "query": "Python web frameworks",
  "topK": 5,
  "minScore": 0.5  // понизить порог для большего охвата
}
```

> Если поиск возвращает мало результатов — рекомендуется понизить `minScore` до 0.5.

---

## Ссылки

- Исходный документ: [RAG_ARCHITECTURE.md](../RAG_ARCHITECTURE.md)
- Cosine Similarity: строки 251-256 в RAG_ARCHITECTURE.md

---

*Дата создания: 2025-11-26*
