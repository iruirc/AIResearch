# ResearchAI - Multi-Provider AI Chat API Server

Мощный чат-сервер на Ktor с поддержкой нескольких AI провайдеров, MCP интеграцией и расширенными возможностями автоматизации.

## Возможности

### Ядро
- ✅ **Веб-интерфейс чата** - современный UI для общения с AI
- ✅ **Мульти-провайдер поддержка** - Claude, OpenAI, HuggingFace, Ollama
- ✅ **CLI клиент** - терминальный интерфейс для чата
- ✅ REST API для чата с AI провайдерами
- ✅ Поддержка JSON запросов/ответов
- ✅ CORS для фронтенд-приложений
- ✅ Конфигурация через переменные окружения

### Расширенные возможности
- ✅ **MCP (Model Context Protocol)** - подключение внешних инструментов
- ✅ **Function Calling** - выполнение функций через AI
- ✅ **Кастомные ассистенты** - персонализированные AI-персоны
- ✅ **Пайплайны ассистентов** - цепочки обработки сообщений
- ✅ **Планировщик задач** - автоматические recurring-сообщения
- ✅ **Компрессия чата** - управление длинными разговорами
- ✅ **RAG** - работа с документами и контекстом

### Аутентификация и хранение
- ✅ **OAuth/JWT аутентификация** - Google OAuth support
- ✅ **PostgreSQL** - продакшн-хранилище данных
- ✅ **JSON persistence** - локальное хранение сессий

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

### Assistants API

| Метод | Endpoint | Описание |
|-------|----------|----------|
| GET | `/assistants` | Список всех ассистентов |
| GET | `/assistants/{id}` | Получить ассистента по ID |
| POST | `/assistants` | Создать ассистента |
| PUT | `/assistants/{id}` | Обновить ассистента |
| DELETE | `/assistants/{id}` | Удалить ассистента |

### Scheduler API

| Метод | Endpoint | Описание |
|-------|----------|----------|
| GET | `/scheduler/tasks` | Список всех задач |
| GET | `/scheduler/tasks/{id}` | Получить задачу по ID |
| POST | `/scheduler/tasks` | Создать задачу |
| POST | `/scheduler/tasks/{id}/start` | Запустить задачу |
| POST | `/scheduler/tasks/{id}/stop` | Остановить задачу |
| DELETE | `/scheduler/tasks/{id}` | Удалить задачу |

### Compression API

| Метод | Endpoint | Описание |
|-------|----------|----------|
| POST | `/compression/compress` | Сжать сессию |
| GET | `/compression/config/{sessionId}` | Получить конфиг компрессии |
| POST | `/compression/config` | Обновить конфиг компрессии |
| GET | `/compression/check/{sessionId}` | Проверить необходимость компрессии |
| GET | `/compression/archived/{sessionId}` | Получить архивные сообщения |

### MCP API

| Метод | Endpoint | Описание |
|-------|----------|----------|
| GET | `/mcp/servers` | Список MCP серверов |
| POST | `/mcp/servers` | Добавить MCP сервер |
| DELETE | `/mcp/servers/{id}` | Удалить MCP сервер |
| GET | `/mcp/tools` | Список доступных инструментов |

## Веб-интерфейс

Сервер включает встроенный веб-интерфейс для общения с AI.

**Доступ:** Откройте браузер и перейдите по адресу `http://localhost:8080`

**Возможности веб-интерфейса:**
- 💬 Удобная переписка с AI в режиме чата
- 🔄 Выбор провайдера и модели на лету
- 🤖 Кастомные ассистенты с уникальными системными промптами
- ⏰ Создание и управление запланированными задачами
- 🔧 Настройка MCP серверов
- 📱 Адаптивный дизайн для мобильных устройств

## CLI клиент

Терминальный интерфейс для взаимодействия с ResearchAI сервером.

```bash
# Сборка CLI
cd researchai-cli
../gradlew shadowJar

# Запуск
java -jar build/libs/researchai-cli-0.0.1-all.jar chat "Привет!"

# С выбором модели
java -jar build/libs/researchai-cli-0.0.1-all.jar chat -m claude-sonnet-4-20250514 "Расскажи о Kotlin"
```

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
ResearchAI/
├── src/main/kotlin/com/researchai/
│   ├── auth/                   # OAuth/JWT аутентификация
│   │   ├── domain/models/      # User, AuthSession, JWTConfig
│   │   ├── data/               # GoogleAuthProvider, UserRepositoryImpl
│   │   ├── routes/             # AuthRoutes
│   │   └── service/            # AuthService, JWTService
│   ├── config/                 # Конфигурации провайдеров
│   ├── data/
│   │   ├── mcp/                # MCPServerManager, MCPClientWrapper
│   │   ├── provider/           # Реализации провайдеров
│   │   │   ├── claude/         # ClaudeProvider, ClaudeMapper
│   │   │   ├── openai/         # OpenAIProvider, OpenAIMapper
│   │   │   └── huggingface/    # HuggingFaceProvider
│   │   └── repository/         # SessionRepositoryImpl
│   ├── domain/
│   │   ├── compression/        # Алгоритмы компрессии чата
│   │   ├── mcp/                # MCPOrchestrationService
│   │   ├── models/             # AIRequest, AIResponse, Message, etc.
│   │   ├── provider/           # AIProviderFactory
│   │   ├── repository/         # Interfaces
│   │   ├── tokenizer/          # TokenCounter
│   │   └── usecase/            # SendMessageUseCase, GetModelsUseCase
│   ├── models/                 # ChatSession, Assistant, SchedulerResponses
│   ├── persistence/
│   │   ├── sql/                # PostgreSQL implementations
│   │   └── *.kt                # JSON storage implementations
│   ├── routes/                 # REST API endpoints
│   ├── scheduler/              # Task Scheduler
│   └── services/               # ChatSessionManager, AssistantManager, etc.
├── researchai-cli/             # CLI клиент
│   └── src/main/kotlin/com/researchai/cli/
├── Documents/                  # Документация
└── data/                       # Runtime данные (sessions, assistants, tasks)
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

**Backend:**
- **Kotlin** - язык программирования
- **Ktor 3.x** - веб-фреймворк
- **Kotlinx Serialization** - JSON сериализация
- **Kotlinx Coroutines** - асинхронное программирование
- **Exposed** - SQL ORM для PostgreSQL
- **Flyway** - миграции базы данных
- **JTokkit** - токенизация для подсчета токенов

**Инфраструктура:**
- **Netty** - HTTP сервер
- **PostgreSQL** - база данных (опционально)
- **Docker** - контейнеризация
- **MCP SDK** - Model Context Protocol

**CLI:**
- **Clikt** - парсер командной строки
- **Mordant** - форматирование терминала

## Документация

Подробная документация находится в папке `Documents/`:

| Документ | Описание |
|----------|----------|
| [QUICK-START.md](Documents/QUICK-START.md) | Быстрый старт |
| [ARCHITECTURE_SUMMARY.md](Documents/ARCHITECTURE_SUMMARY.md) | Обзор архитектуры |
| [MULTI_PROVIDER_ARCHITECTURE.md](Documents/MULTI_PROVIDER_ARCHITECTURE.md) | Мульти-провайдер архитектура |
| [ASSISTANT_API.md](Documents/ASSISTANT_API.md) | API ассистентов |
| [ASSISTANT_PIPELINE.md](Documents/ASSISTANT_PIPELINE.md) | Пайплайны ассистентов |
| [TASK_SCHEDULER.md](Documents/TASK_SCHEDULER.md) | Планировщик задач |
| [COMPRESSION_MECHANISM.md](Documents/COMPRESSION_MECHANISM.md) | Компрессия чата |
| [MCP_INTEGRATION.md](Documents/MCP_INTEGRATION.md) | MCP интеграция |
| [MCP_ORCHESTRATION.md](Documents/MCP_ORCHESTRATION.md) | MCP оркестрация |
| [FUNCTION_CALLING.md](Documents/FUNCTION_CALLING.md) | Function calling |
| [AUTH_README.md](Documents/AUTH_README.md) | Аутентификация |
| [POSTGRESQL_ARCHITECTURE.md](Documents/POSTGRESQL_ARCHITECTURE.md) | PostgreSQL |
| [DEPLOYMENT.md](Documents/DEPLOYMENT.md) | Деплой |
| [DOCKER-QUICKSTART.md](Documents/DOCKER-QUICKSTART.md) | Docker quick start |
| [CLI](Documents/CLI/) | Документация CLI |

## Полезные ссылки

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Claude API Documentation](https://docs.anthropic.com/claude/reference/getting-started-with-the-api)
- [OpenAI API Documentation](https://platform.openai.com/docs)
- [MCP Specification](https://modelcontextprotocol.io/)

## Лицензия

MIT


TEST
