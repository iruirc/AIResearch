# Assistant API Documentation

API для управления пользовательскими AI ассистентами с персистентным хранением.

## Обзор

ResearchAI поддерживает создание пользовательских ассистентов с настраиваемым системным промптом. Ассистенты автоматически сохраняются в файловую систему (`data/assistants/`) и восстанавливаются при перезапуске приложения.

### Типы ассистентов

1. **Системные ассистенты** (`isSystem: true`):
   - Встроены в приложение
   - Не могут быть изменены или удалены
   - Примеры: `greeting-assistant`, `ai-tutor`

2. **Пользовательские ассистенты** (`isSystem: false`):
   - Создаются через API
   - Могут быть изменены и удалены
   - Сохраняются в `data/assistants/{id}.json`

## API Endpoints

### 1. Получить список всех ассистентов

```http
GET /assistants
```

**Response:**
```json
{
  "assistants": [
    {
      "id": "greeting-assistant",
      "name": "Ассистент Приветствия",
      "systemPrompt": "...",
      "description": "Приветствует пользователей и помогает начать диалог",
      "isSystem": true
    },
    {
      "id": "code-reviewer",
      "name": "Code Reviewer",
      "systemPrompt": "You are a helpful code reviewer...",
      "description": "Reviews code and suggests improvements",
      "isSystem": false
    }
  ]
}
```

### 2. Получить ассистента по ID

```http
GET /assistants/{id}
```

**Response (успех):**
```json
{
  "success": true,
  "message": "Assistant retrieved successfully",
  "assistant": {
    "id": "code-reviewer",
    "name": "Code Reviewer",
    "systemPrompt": "You are a helpful code reviewer...",
    "description": "Reviews code and suggests improvements",
    "isSystem": false
  }
}
```

**Response (не найден):**
```json
{
  "success": false,
  "message": "Assistant with ID 'unknown' not found",
  "assistant": null
}
```

### 3. Создать ассистента

```http
POST /assistants
Content-Type: application/json
```

**Request Body:**
```json
{
  "id": "code-reviewer",
  "name": "Code Reviewer",
  "systemPrompt": "You are a helpful code reviewer. You analyze code and provide constructive feedback.",
  "description": "Reviews code and suggests improvements"
}
```

**Response (успех):**
```json
{
  "success": true,
  "message": "Assistant created successfully",
  "assistant": {
    "id": "code-reviewer",
    "name": "Code Reviewer",
    "systemPrompt": "You are a helpful code reviewer...",
    "description": "Reviews code and suggests improvements",
    "isSystem": false
  }
}
```

**Response (ID уже существует):**
```json
{
  "success": false,
  "message": "Failed to create assistant: Assistant with ID 'code-reviewer' already exists"
}
```

**Валидация:**
- `id` - обязательное, не пустое
- `name` - обязательное, не пустое
- `systemPrompt` - обязательное, не пустое
- `description` - опциональное (по умолчанию "")

### 4. Обновить ассистента

```http
PUT /assistants/{id}
Content-Type: application/json
```

**Request Body:**
```json
{
  "id": "code-reviewer",
  "name": "Code Reviewer Pro",
  "systemPrompt": "You are an expert code reviewer...",
  "description": "Professional code reviewer with advanced analysis"
}
```

**Response (успех):**
```json
{
  "success": true,
  "message": "Assistant updated successfully",
  "assistant": {
    "id": "code-reviewer",
    "name": "Code Reviewer Pro",
    "systemPrompt": "You are an expert code reviewer...",
    "description": "Professional code reviewer with advanced analysis",
    "isSystem": false
  }
}
```

**Response (системный ассистент):**
```json
{
  "success": false,
  "message": "Failed to update assistant: Cannot modify system assistant"
}
```

**Response (не найден):**
```json
{
  "success": false,
  "message": "Failed to update assistant: Assistant with ID 'unknown' not found"
}
```

**Ограничения:**
- ID в URL должен совпадать с ID в теле запроса
- Системные ассистенты не могут быть обновлены

### 5. Удалить ассистента

```http
DELETE /assistants/{id}
```

**Response (успех):**
```json
{
  "success": true,
  "message": "Assistant deleted successfully",
  "assistant": null
}
```

**Response (системный ассистент):**
```json
{
  "success": false,
  "message": "Failed to delete assistant: Cannot delete system assistant"
}
```

**Response (не найден):**
```json
{
  "success": false,
  "message": "Failed to delete assistant: Assistant with ID 'unknown' not found"
}
```

**Ограничения:**
- Системные ассистенты не могут быть удалены

## Использование ассистентов в чате

Чтобы использовать ассистента в чате, укажите `assistantId` при создании сессии:

```http
POST /chat
Content-Type: application/json
```

```json
{
  "message": "Hello!",
  "sessionId": "my-session-123",
  "assistantId": "code-reviewer"
}
```

Ассистент применит свой `systemPrompt` к разговору, определяя стиль и поведение AI.

## Примеры использования

### cURL

**Создать ассистента:**
```bash
curl -X POST http://localhost:8080/assistants \
  -H "Content-Type: application/json" \
  -d '{
    "id": "python-tutor",
    "name": "Python Tutor",
    "systemPrompt": "You are an experienced Python tutor. Explain concepts clearly with examples.",
    "description": "Helps learn Python programming"
  }'
```

**Получить список:**
```bash
curl http://localhost:8080/assistants
```

**Обновить:**
```bash
curl -X PUT http://localhost:8080/assistants/python-tutor \
  -H "Content-Type: application/json" \
  -d '{
    "id": "python-tutor",
    "name": "Python Expert",
    "systemPrompt": "You are a Python expert with deep knowledge of advanced topics.",
    "description": "Expert-level Python guidance"
  }'
```

**Удалить:**
```bash
curl -X DELETE http://localhost:8080/assistants/python-tutor
```

## Архитектура

### Компоненты

1. **AssistantStorage** (`com.researchai.persistence.AssistantStorage`):
   - Интерфейс для хранения ассистентов

2. **JsonAssistantStorage** (`com.researchai.persistence.JsonAssistantStorage`):
   - Реализация с JSON файлами
   - Директория: `data/assistants/`
   - Atomic writes для предотвращения повреждений

3. **AssistantManager** (`com.researchai.services.AssistantManager`):
   - Управляет ассистентами в памяти
   - Загружает из хранилища при старте
   - Синхронизирует изменения с диском

4. **AssistantRoutes** (`com.researchai.routes.AssistantRoutes`):
   - REST API endpoints
   - Валидация запросов
   - Обработка ошибок

### Персистентность

- **Формат**: JSON файлы
- **Расположение**: `data/assistants/{id}.json`
- **Загрузка**: Автоматически при старте приложения
- **Сохранение**: Мгновенное при изменениях
- **Atomic writes**: Защита от повреждений при сбоях

### Пример файла ассистента

`data/assistants/code-reviewer.json`:
```json
{
    "id": "code-reviewer",
    "name": "Code Reviewer",
    "systemPrompt": "You are a helpful code reviewer. You analyze code and provide constructive feedback.",
    "description": "Reviews code and suggests improvements"
}
```

## Коды ошибок

| Код | Описание |
|-----|----------|
| 200 | Успешная операция |
| 201 | Ассистент создан |
| 400 | Неверный запрос (валидация) |
| 403 | Операция запрещена (системный ассистент) |
| 404 | Ассистент не найден |
| 409 | Конфликт (ID уже существует) |
| 500 | Внутренняя ошибка сервера |

## Ограничения

1. **ID ассистента**:
   - Должен быть уникальным
   - Не может содержать недопустимые символы для имени файла
   - Рекомендуется: kebab-case (например, `my-assistant`)

2. **Системные ассистенты**:
   - Не могут быть изменены через API
   - Не могут быть удалены
   - Всегда доступны

3. **Размер systemPrompt**:
   - Технически не ограничен
   - Рекомендуется до 10KB для оптимальной производительности

## Расширения

Для добавления новых системных ассистентов отредактируйте `AssistantManager.kt`:

```kotlin
private fun registerDefaultAssistants() {
    registerAssistant(createGreetingAssistant())
    registerAssistant(createAiTutorAssistant())
    registerAssistant(createMyCustomAssistant()) // Ваш ассистент
}

private fun createMyCustomAssistant(): Assistant {
    return Assistant(
        id = "my-custom",
        name = "My Custom Assistant",
        systemPrompt = "Your system prompt here",
        description = "Description",
        isSystem = true
    )
}
```
