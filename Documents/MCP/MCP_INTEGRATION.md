# MCP (Model Context Protocol) Integration

## Статус интеграции

MCP интеграция добавлена в ResearchAI, но находится в статусе **в разработке** из-за следующих причин:

1. **MCP Kotlin SDK v0.6.0** - API еще нестабильный и документация неполная
2. **Отсутствие production-ready транспортов** - STDIO, SSE, WebSocket транспорты требуют дополнительной настройки
3. **Требуется тестирование** с реальными MCP серверами

## Что уже реализовано

### 1. Domain модели (✅ Готово)

Созданы модели для работы с MCP:
- `MCPServerConfig` - конфигурация MCP сервера
- `MCPTool` - инструменты, предоставляемые MCP серверами
- `MCPResource` - ресурсы (файлы, данные, API)
- `MCPPrompt` - промпт-шаблоны
- `MCPContent` - контент (text, image, resource)

Файлы:
- `src/main/kotlin/com/researchai/domain/models/mcp/MCPServerConfig.kt`
- `src/main/kotlin/com/researchai/domain/models/mcp/MCPTool.kt`
- `src/main/kotlin/com/researchai/domain/models/mcp/MCPResource.kt`
- `src/main/kotlin/com/researchai/domain/models/mcp/MCPPrompt.kt`

### 2. MCP Client Wrapper (⚠️ Требует доработки)

Создан `MCPClientWrapper` для управления подключениями к MCP серверам.

**Файл:** `src/main/kotlin/com/researchai/data/mcp/MCPClientWrapper.kt`

**Проблема:** API MCP SDK 0.6.0 отличается от документации. Требуется обновление после стабилизации SDK.

### 3. MCP Server Manager (✅ Готово)

`MCPServerManager` управляет несколькими MCP серверами одновременно.

**Файл:** `src/main/kotlin/com/researchai/data/mcp/MCPServerManager.kt`

**Функции:**
- Подключение к нескольким MCP серверам
- Получение списка tools/resources/prompts
- Вызов tools
- Чтение resources
- Получение prompts

### 4. Configuration (✅ Готово)

Конфигурация MCP серверов загружается из JSON файла.

**Файл:** `src/main/kotlin/com/researchai/config/MCPConfig.kt`

**Путь к конфигурации:** `config/mcp-servers.json`

**Пример конфигурации:**

```json
{
  "servers": [
    {
      "id": "filesystem",
      "name": "Filesystem Server",
      "description": "Access to local filesystem",
      "transport": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
      "enabled": true
    },
    {
      "id": "github",
      "name": "GitHub Server",
      "description": "Access to GitHub API",
      "transport": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_TOKEN": "${GITHUB_TOKEN}"
      },
      "enabled": false
    }
  ]
}
```

### 5. API Routes (✅ Готово)

API endpoints для работы с MCP серверами.

**Файл:** `src/main/kotlin/com/researchai/routes/MCPRoutes.kt`

**Endpoints:**

- `GET /api/mcp/servers` - список MCP серверов
- `GET /api/mcp/servers/{serverId}` - информация о сервере
- `POST /api/mcp/servers/{serverId}/reconnect` - переподключение
- `GET /api/mcp/tools` - список всех tools
- `POST /api/mcp/tools/call` - вызов tool
- `GET /api/mcp/resources` - список всех resources
- `POST /api/mcp/resources/read` - чтение resource
- `GET /api/mcp/prompts` - список всех prompts
- `POST /api/mcp/prompts/get` - получение prompt

### 6. AppModule Integration (✅ Готово)

MCP интегрирован в DI контейнер приложения.

**Изменения:**
- `AppModule.mcpServerManager` - менеджер MCP серверов
- `AppModule.initializeMCP()` - инициализация MCP серверов при старте
- `Application.module()` - запуск MCP инициализации в фоне

## Что нужно доработать

### 1. Обновить MCPClientWrapper

После стабилизации MCP Kotlin SDK API нужно обновить `MCPClientWrapper.kt` с правильными вызовами:

```kotlin
// Пример правильного API (из документации):
val client = Client(
    clientInfo = Implementation(name = "sample-client", version = "1.0.0")
)
client.connect(WebSocketClientTransport("ws://localhost:8080/mcp"))
val tools = client.listTools()
val result = client.callTool(
    name = "echo",
    arguments = mapOf("text" to "Hello, MCP!")
)
```

### 2. Добавить транспорты

Текущий код предполагает использование `StdioServerTransport` и `SSEClientTransport`, но их API может отличаться. Нужно проверить:

- Как создавать STDIO транспорт для subprocess-based серверов
- Как создавать SSE транспорт для HTTP-based серверов
- WebSocket транспорт (если нужен)

### 3. Тестирование

Протестировать с реальными MCP серверами:
- `@modelcontextprotocol/server-filesystem`
- `@modelcontextprotocol/server-github`
- `@modelcontextprotocol/server-postgres`

### 4. Интеграция с AI провайдерами

Добавить tool calling в AI провайдерах (Claude, OpenAI) с использованием MCP tools.

**План:**
1. Получить список MCP tools
2. Преобразовать в формат Claude/OpenAI tool definitions
3. При вызове tool - делегировать в MCP server
4. Вернуть результат обратно в AI model

## Как использовать (когда будет готово)

### 1. Создать конфигурацию

Создать файл `config/mcp-servers.json` с настройками ваших MCP серверов.

### 2. Установить MCP серверы

Например, для filesystem server:

```bash
npm install -g @modelcontextprotocol/server-filesystem
```

### 3. Запустить приложение

```bash
./gradlew run
```

При старте вы увидите:

```
✅ MCP Servers: Initialized
```

### 4. Проверить статус

```bash
curl http://localhost:8080/api/mcp/servers
```

### 5. Использовать tools

```bash
curl -X POST http://localhost:8080/api/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "toolName": "read_file",
    "serverId": "filesystem",
    "arguments": {
      "path": "/tmp/test.txt"
    }
  }'
```

## Dependencies

**MCP Kotlin SDK:**
```kotlin
implementation("io.modelcontextprotocol:kotlin-sdk:0.6.0")
```

**Официальная документация:**
- GitHub: https://github.com/modelcontextprotocol/kotlin-sdk
- Docs: https://modelcontextprotocol.github.io/kotlin-sdk/

## TODO

- [ ] Обновить MCPClientWrapper после стабилизации API
- [ ] Протестировать с реальными MCP серверами
- [ ] Добавить WebSocket транспорт
- [ ] Интегрировать MCP tools с Claude/OpenAI tool calling
- [ ] Добавить примеры использования
- [ ] Написать тесты
- [ ] Добавить обработку ошибок и retry логику
- [ ] Добавить логирование MCP операций

## Примечания

Интеграция MCP готова на 70%. Основная архитектура создана, но требуется финализация после стабилизации MCP Kotlin SDK API.

Если вы хотите помочь с доработкой, начните с обновления `MCPClientWrapper.kt` согласно актуальной документации MCP SDK.
