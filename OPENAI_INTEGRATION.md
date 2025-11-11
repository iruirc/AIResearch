# OpenAI API Integration

Этот документ описывает интеграцию OpenAI API в ResearchAI SDK.

## Обзор

ResearchAI теперь поддерживает работу с OpenAI API наряду с Claude API. Архитектура позволяет легко переключаться между провайдерами и использовать их одновременно.

## Настройка

### 1. Получение API ключей

Для работы с OpenAI API вам нужны следующие ключи:

1. **API Key** (обязательно) - получите на https://platform.openai.com/api-keys
2. **Organization ID** (опционально) - найдите на https://platform.openai.com/settings/organization
3. **Project ID** (опционально) - найдите на https://platform.openai.com/settings/organization/projects

### 2. Конфигурация через .env файл

Скопируйте `.env.example` в `.env` и добавьте ваши ключи:

```bash
cp .env.example .env
```

Отредактируйте `.env` файл:

```bash
# =============================================================================
# OpenAI API Configuration
# =============================================================================

# ОБЯЗАТЕЛЬНАЯ ПЕРЕМЕННАЯ для использования OpenAI
OPENAI_API_KEY=sk-proj-ваш_реальный_ключ

# ОПЦИОНАЛЬНЫЕ ПЕРЕМЕННЫЕ
OPENAI_ORGANIZATION_ID=org-ваш_organization_id
OPENAI_PROJECT_ID=proj_ваш_project_id
OPENAI_MODEL=gpt-4-turbo
OPENAI_MAX_TOKENS=4096
OPENAI_TEMPERATURE=1.0
```

**⚠️ ВАЖНО**:
- Файл `.env` добавлен в `.gitignore` и не попадет в систему контроля версий
- Никогда не сохраняйте API ключи в открытом виде в коде
- Не публикуйте API ключи в публичных репозиториях

### 3. Проверка конфигурации

При запуске приложения вы увидите информацию о доступных провайдерах:

```
✅ Claude API: Configured
✅ OpenAI API: Configured
   - Organization: org-94oRd0oSYpjqF5NW0cJmZWnm
   - Project: proj_Qvkh9BJOfM0wuiiG62zywkXY
   - Model: gpt-4-turbo
```

Если OpenAI не настроен:

```
✅ Claude API: Configured
⚠️  OpenAI API: Not configured (add OPENAI_API_KEY to .env)
```

## Использование API

### REST API Endpoints

#### 1. Получить список провайдеров

```bash
GET /api/v2/providers
```

Ответ:
```json
{
  "providers": ["CLAUDE", "OPENAI"]
}
```

#### 2. Получить модели OpenAI

```bash
GET /api/v2/providers/openai/models
```

Ответ:
```json
{
  "models": [
    {
      "id": "gpt-4-turbo",
      "name": "gpt-4-turbo",
      "providerId": "OPENAI",
      "capabilities": {
        "supportsVision": true,
        "supportsStreaming": true,
        "maxTokens": 4096,
        "contextWindow": 128000
      }
    }
  ]
}
```

#### 3. Отправить сообщение через OpenAI

```bash
POST /api/v2/chat
Content-Type: application/json

{
  "provider": "openai",
  "messages": [
    {
      "role": "user",
      "content": "Привет! Как дела?"
    }
  ],
  "model": "gpt-4-turbo",
  "parameters": {
    "temperature": 0.7,
    "maxTokens": 1000,
    "topP": 1.0,
    "frequencyPenalty": 0.0,
    "presencePenalty": 0.0
  }
}
```

Ответ:
```json
{
  "id": "chatcmpl-...",
  "content": "Привет! У меня всё отлично, спасибо!",
  "role": "ASSISTANT",
  "model": "gpt-4-turbo",
  "usage": {
    "inputTokens": 15,
    "outputTokens": 12,
    "totalTokens": 27
  },
  "finishReason": "STOP",
  "sessionId": "session-123",
  "timestamp": 1699999999999
}
```

#### 4. Настроить OpenAI провайдер динамически

Если вы не хотите использовать `.env` файл, можно настроить провайдер через API:

```bash
POST /api/v2/providers/configure
Content-Type: application/json

{
  "provider": "openai",
  "apiKey": "sk-proj-...",
  "organization": "org-...",
  "projectId": "proj_...",
  "defaultModel": "gpt-4-turbo"
}
```

## Тестирование

### Автоматический тест

Используйте готовый скрипт для тестирования:

```bash
./test-openai.sh
```

Скрипт выполнит:
1. Получение списка провайдеров
2. Получение списка моделей OpenAI
3. Отправку тестового сообщения
4. Проверку конфигурации

### Ручное тестирование с curl

```bash
# 1. Проверка доступности API
curl http://localhost:8080/api/v2/providers

# 2. Получение моделей
curl http://localhost:8080/api/v2/providers/openai/models

# 3. Отправка сообщения
curl -X POST http://localhost:8080/api/v2/chat \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "openai",
    "messages": [{"role": "user", "content": "Hello!"}],
    "model": "gpt-4-turbo"
  }'
```

## Доступные модели

ResearchAI автоматически определяет следующие модели OpenAI:

| Модель | Max Tokens | Context Window | Vision Support |
|--------|------------|----------------|----------------|
| gpt-4o | 16,384 | 128,000 | ✅ |
| gpt-4-turbo | 4,096 | 128,000 | ✅ |
| gpt-4 | 8,192 | 8,192 | ❌ |
| gpt-3.5-turbo | 4,096 | 4,096 | ❌ |

## Параметры запроса

### Обязательные параметры

- `provider` - "openai"
- `messages` - массив сообщений
- `model` - идентификатор модели

### Опциональные параметры

```typescript
{
  "parameters": {
    "temperature": 1.0,          // 0.0 - 2.0, контролирует случайность
    "maxTokens": 4096,           // максимальное количество токенов в ответе
    "topP": 1.0,                 // 0.0 - 1.0, nucleus sampling
    "frequencyPenalty": 0.0,     // -2.0 - 2.0, штраф за частоту
    "presencePenalty": 0.0,      // -2.0 - 2.0, штраф за присутствие
    "stopSequences": ["END"]     // последовательности для остановки генерации
  },
  "systemPrompt": "You are a helpful assistant",
  "sessionId": "optional-session-id"
}
```

## Архитектура

### Компоненты

```
Application.kt
    ↓ (загружает конфигурацию)
OpenAIConfig.kt (getOpenAIConfig)
    ↓
AppModule.kt
    ↓
ConfigRepositoryImpl.kt
    ↓ (создает провайдера)
OpenAIProvider.kt
    ├─ OpenAIMapper.kt (маппинг моделей)
    └─ OpenAIApiModels.kt (API модели)
```

### Файлы реализации

- `config/OpenAIConfig.kt` - конфигурация и загрузка из env
- `domain/models/ProviderConfig.kt` - OpenAIConfig data class
- `data/provider/openai/OpenAIProvider.kt` - основная логика
- `data/provider/openai/OpenAIApiModels.kt` - API модели
- `data/provider/openai/OpenAIMapper.kt` - маппинг domain ↔ API

## Обработка ошибок

Все ошибки OpenAI API обрабатываются и возвращаются в стандартном формате:

```json
{
  "error": {
    "message": "OpenAI API Error: Invalid API key",
    "type": "NETWORK_EXCEPTION"
  }
}
```

Типы ошибок:
- `NETWORK_EXCEPTION` - ошибки сети или API
- `VALIDATION_EXCEPTION` - ошибки валидации конфигурации
- `PARSE_EXCEPTION` - ошибки парсинга ответа

## Безопасность

### Рекомендации

1. **Хранение ключей**
   - Используйте `.env` файл для локальной разработки
   - Используйте переменные окружения в production
   - Никогда не коммитьте `.env` файл

2. **Ротация ключей**
   - Регулярно обновляйте API ключи
   - Используйте разные ключи для разработки и production

3. **Ограничения**
   - Настройте rate limiting в OpenAI dashboard
   - Установите лимиты на использование токенов

4. **Мониторинг**
   - Отслеживайте использование токенов
   - Проверяйте логи на подозрительную активность

## Миграция с Claude на OpenAI

Если вы хотите переключиться с Claude на OpenAI, достаточно:

1. Добавить `OPENAI_API_KEY` в `.env`
2. Изменить `provider` в запросах с `"claude"` на `"openai"`

Пример:

```diff
{
-  "provider": "claude",
+  "provider": "openai",
   "messages": [...],
-  "model": "claude-sonnet-4-5-20250929"
+  "model": "gpt-4-turbo"
}
```

## Производительность

### Таймауты

Настроены следующие таймауты для OpenAI API:

- Connect timeout: 10 секунд
- Read timeout: 5 минут (300 секунд)
- Write timeout: 5 минут (300 секунд)

### Оптимизация

1. **Уменьшение latency**
   - Используйте `gpt-3.5-turbo` для быстрых ответов
   - Уменьшите `maxTokens` для коротких ответов

2. **Снижение стоимости**
   - Используйте `gpt-3.5-turbo` вместо `gpt-4`
   - Оптимизируйте промпты для уменьшения токенов

## Поддержка

При возникновении проблем:

1. Проверьте логи приложения
2. Убедитесь, что API ключ корректен
3. Проверьте баланс в OpenAI dashboard
4. Обратитесь к документации OpenAI: https://platform.openai.com/docs

## Пример полного запроса

```bash
curl -X POST http://localhost:8080/api/v2/chat \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "openai",
    "messages": [
      {
        "role": "system",
        "content": "You are a helpful coding assistant."
      },
      {
        "role": "user",
        "content": "Write a simple Hello World in Python"
      }
    ],
    "model": "gpt-4-turbo",
    "parameters": {
      "temperature": 0.7,
      "maxTokens": 500,
      "topP": 0.9,
      "frequencyPenalty": 0.0,
      "presencePenalty": 0.0
    },
    "systemPrompt": "You are an expert Python developer",
    "sessionId": "coding-session-001"
  }'
```

## Changelog

### v1.0.0 (2025-11-11)
- ✅ Базовая интеграция OpenAI API
- ✅ Поддержка всех GPT моделей
- ✅ Конфигурация через .env файл
- ✅ Organization ID и Project ID support
- ✅ Автоматическое определение capabilities моделей
- ✅ Полный маппинг параметров
- ✅ Обработка ошибок
- ✅ Тестовый скрипт
