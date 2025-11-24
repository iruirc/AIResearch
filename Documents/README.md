# ResearchAI - Multi-Provider AI Chat API Server

Мощный чат-сервер на Ktor с поддержкой нескольких AI провайдеров (Claude API, OpenAI API и другие).

## Возможности

- ✅ **Веб-интерфейс чата** - красивый UI для общения с AI
- ✅ **Мульти-провайдер поддержка** - Claude, OpenAI, HuggingFace, Ollama
- ✅ REST API для чата с AI провайдерами
- ✅ Поддержка JSON запросов/ответов
- ✅ CORS для фронтенд-приложений
- ✅ Health check endpoint
- ✅ Конфигурация через переменные окружения
- ✅ Логирование запросов и ответов
- ✅ Управление сессиями чата
- ✅ Динамическая конфигурация провайдеров

## AI Providers

### Поддерживаемые провайдеры

| Провайдер | Статус | Документация |
|-----------|---------|--------------|
| **Claude (Anthropic)** | ✅ Активен | [Claude API](https://docs.anthropic.com/claude/reference/getting-started-with-the-api) |
| **OpenAI** | ✅ Активен | [OPENAI_INTEGRATION.md](OPENAI_INTEGRATION.md) |
| **HuggingFace** | ✅ Активен | - |
| **Ollama (Local)** | ✅ Активен | - |

### Быстрая настройка OpenAI

```bash
# Добавьте в .env файл
OPENAI_API_KEY=sk-proj-ваш_ключ
OPENAI_ORGANIZATION_ID=org-ваш_org_id (опционально)
OPENAI_PROJECT_ID=proj_ваш_project_id (опционально)
```

📖 **Полная документация по OpenAI**: [OPENAI_INTEGRATION.md](OPENAI_INTEGRATION.md)

## API Endpoints

### Legacy API (v1)

#### POST /chat
Отправка сообщения в чат с Claude (legacy endpoint).

**Request:**
```json
{
  "message": "Привет, Claude!"
}
```

**Response:**
```json
{
  "response": "Здравствуйте! Как я могу помочь вам сегодня?"
}
```

### New API (v2) - Мульти-провайдер

#### POST /api/v2/chat
Отправка сообщения через выбранный провайдер.

**Request:**
```json
{
  "provider": "openai",
  "messages": [
    {
      "role": "user",
      "content": "Привет!"
    }
  ],
  "model": "gpt-4-turbo",
  "parameters": {
    "temperature": 0.7,
    "maxTokens": 1000
  }
}
```

**Response:**
```json
{
  "id": "chatcmpl-...",
  "content": "Привет! Как дела?",
  "role": "ASSISTANT",
  "model": "gpt-4-turbo",
  "usage": {
    "inputTokens": 10,
    "outputTokens": 15,
    "totalTokens": 25
  }
}
```

#### GET /api/v2/providers
Получить список доступных провайдеров.

#### GET /api/v2/providers/{provider}/models
Получить список моделей провайдера.

### GET /health
Проверка статуса сервера.

**Response:**
```json
{
  "status": "ok"
}
```

### GET /
Перенаправляет на веб-интерфейс чата (`/index.html`).

## Веб-интерфейс

Сервер включает встроенный веб-интерфейс для общения с Claude.

**Доступ:** Откройте браузер и перейдите по адресу `http://localhost:8080`

**Возможности веб-интерфейса:**
- 💬 Удобная переписка с Claude в режиме чата
- ⚡ Автоматическая отправка по Enter (Shift+Enter для новой строки)
- 🎨 Современный дизайн с анимациями
- ⏱️ Индикатор загрузки при ожидании ответа
- ❌ Обработка ошибок и таймаутов (30 секунд)
- 📱 Адаптивный дизайн для мобильных устройств

![Веб-интерфейс чата](https://via.placeholder.com/800x500.png?text=Claude+Chat+Interface)

## Установка и запуск

### 1. Получите API ключ Claude

Зарегистрируйтесь на [console.anthropic.com](https://console.anthropic.com/) и получите API ключ.

### 2. Настройте переменные окружения

Скопируйте `.env.example` в `.env` и установите ваш API ключ:

```bash
cp .env.example .env
```

Отредактируйте `.env`:
```
CLAUDE_API_KEY=sk-ant-api03-...
```

### 3. Загрузите переменные окружения

```bash
export $(cat .env | xargs)
```

### 4. Запустите сервер

**Режим разработки:**
```bash
./gradlew run
```

**Сборка JAR:**
```bash
./gradlew buildFatJar
java -jar build/libs/ResearchAI-0.0.1-all.jar
```

Сервер будет доступен по адресу: `http://localhost:8080`

### 5. Откройте веб-интерфейс

Откройте браузер и перейдите по адресу:
```
http://localhost:8080
```

Вы увидите веб-интерфейс чата. Введите вопрос в нижнем поле и нажмите кнопку отправки или клавишу Enter!

## Тестирование API

### С помощью curl:

```bash
# Отправить сообщение в чат
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Привет, Claude!"}'

# Проверить health check
curl http://localhost:8080/health
```

### С помощью HTTPie:

```bash
# Отправить сообщение в чат
http POST localhost:8080/chat message="Привет, Claude!"

# Проверить health check
http GET localhost:8080/health
```

## Запуск через Docker

### Локальный запуск

```bash
# Создайте .env файл с вашим API ключом
echo "CLAUDE_API_KEY=your_key_here" > .env

# Запустите контейнер
docker-compose up -d

# Просмотр логов
docker-compose logs -f

# Остановка
docker-compose down
```

Приложение будет доступно по адресу: `http://localhost:8080`

### Сборка Docker образа

```bash
# Сборка образа
docker-compose build

# Или без кэша
docker-compose build --no-cache

# Запуск
docker-compose up -d
```

## Деплой на VPS через Docker

### Быстрый старт

**На VPS сервере:**

```bash
# 1. Установите Docker и Docker Compose
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 2. Клонируйте проект
git clone https://github.com/your-repo/ResearchAI.git
cd ResearchAI

# 3. Настройте переменные окружения
nano .env
# Добавьте: CLAUDE_API_KEY=your_key_here

# 4. Запустите
docker-compose up -d

# 5. Настройте Nginx (опционально, для домена и HTTPS)
# См. подробную инструкцию в DEPLOYMENT.md
```

### Подробная инструкция

📖 **Полное руководство по деплою находится в [DEPLOYMENT.md](DEPLOYMENT.md)**

Инструкция включает:
- Подготовку VPS сервера (Ubuntu)
- Установку Docker и Docker Compose
- Настройку Nginx как reverse proxy
- Получение SSL сертификата (Let's Encrypt)
- Мониторинг и управление контейнерами
- Резервное копирование
- Устранение проблем

## Структура проекта

```
src/main/kotlin/com/example/
├── config/
│   └── ClaudeConfig.kt         # Конфигурация Claude API
├── models/
│   ├── ChatRequest.kt          # Модель запроса от пользователя
│   ├── ChatResponse.kt         # Модель ответа
│   └── ClaudeModels.kt         # Модели Claude API
├── routes/
│   └── ChatRoutes.kt           # HTTP endpoints
├── services/
│   └── ClaudeService.kt        # Сервис для работы с Claude API
├── Application.kt              # Главный файл приложения
└── Routing.kt                  # Конфигурация роутинга
```

## Переменные окружения

### Claude API (обязательно)

| Переменная | Обязательная | По умолчанию | Описание |
|-----------|--------------|--------------|----------|
| `CLAUDE_API_KEY` | ✅ Да | - | API ключ Claude |
| `CLAUDE_MODEL` | ❌ Нет | `claude-haiku-4-5-20251001` | Модель Claude |
| `CLAUDE_MAX_TOKENS` | ❌ Нет | `8192` | Максимум токенов в ответе |
| `CLAUDE_TEMPERATURE` | ❌ Нет | `1.0` | Температура генерации (0.0-1.0) |

### OpenAI API (опционально)

| Переменная | Обязательная | По умолчанию | Описание |
|-----------|--------------|--------------|----------|
| `OPENAI_API_KEY` | ✅ Да* | - | API ключ OpenAI |
| `OPENAI_ORGANIZATION_ID` | ❌ Нет | - | Organization ID |
| `OPENAI_PROJECT_ID` | ❌ Нет | - | Project ID |
| `OPENAI_MODEL` | ❌ Нет | `gpt-4-turbo` | Модель OpenAI |
| `OPENAI_MAX_TOKENS` | ❌ Нет | `4096` | Максимум токенов в ответе |
| `OPENAI_TEMPERATURE` | ❌ Нет | `1.0` | Температура генерации (0.0-2.0) |

*Обязательно только если вы хотите использовать OpenAI провайдер

## Gradle Tasks

| Задача | Описание |
|--------|----------|
| `./gradlew test` | Запустить тесты |
| `./gradlew build` | Собрать проект |
| `./gradlew buildFatJar` | Собрать executable JAR со всеми зависимостями |
| `./gradlew run` | Запустить сервер в режиме разработки |

## Технологии

- **Kotlin** - язык программирования
- **Ktor 3.x** - веб-фреймворк
- **Kotlinx Serialization** - JSON сериализация
- **Ktor Client** - HTTP клиент для запросов к Claude API
- **Netty** - HTTP сервер

## Полезные ссылки

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Claude API Documentation](https://docs.anthropic.com/claude/reference/getting-started-with-the-api)
- [Ktor GitHub page](https://github.com/ktorio/ktor)

## Лицензия

MIT
