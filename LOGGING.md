# Логирование в Claude Chat API Server

## Где смотреть логи?

Все логи выводятся в **консоль (STDOUT)** при запуске сервера.

### При запуске через Gradle:
```bash
./gradlew run
```
Логи будут видны непосредственно в терминале.

### При запуске JAR файла:
```bash
java -jar build/libs/ktor-firtsAI-0.0.1-all.jar
```
Логи также выводятся в терминал.

### Перенаправление логов в файл:
```bash
./gradlew run > logs/app.log 2>&1
# или
java -jar build/libs/ktor-firtsAI-0.0.1-all.jar > logs/app.log 2>&1
```

## Что логируется?

### 1. HTTP запросы к серверу
```
INFO  i.k.s.p.c.ApplicationEngineEnvironmentReloading - Responding at http://0.0.0.0:8080
INFO  Application - POST /chat - 200 OK
```

### 2. Запросы к Claude API
```
INFO  c.e.services.ClaudeService - Sending message to Claude API: Привет!
INFO  c.e.services.ClaudeService - Claude Request: model=claude-haiku-4-5-20251001, maxTokens=1024
```

### 3. Ответы от Claude API
```
INFO  c.e.services.ClaudeService - Claude API response status: 200 OK
INFO  c.e.services.ClaudeService - Successfully received response from Claude API
```

### 4. Ошибки Claude API
```
ERROR c.e.services.ClaudeService - Claude API error response: {"type":"error","error":{"type":"authentication_error","message":"Invalid API key"}}
```

### 5. HTTP Client детали (опционально)
Для более подробных логов HTTP клиента измените уровень в `ClaudeService.kt`:
```kotlin
install(Logging) {
    logger = Logger.DEFAULT
    level = LogLevel.ALL  // Вместо INFO
}
```

## Формат логов

```
YYYY-MM-DD HH:mm:ss.SSS [thread-name] LEVEL logger-name - message
```

Пример:
```
2025-01-15 14:32:45.123 [DefaultDispatcher-worker-1] INFO  c.e.services.ClaudeService - Sending message to Claude API: Hello
```

## Настройка уровня логирования

Отредактируйте файл `src/main/resources/logback.xml`:

### Уровни логирования:
- **TRACE** - Максимально подробные логи (включая тела запросов/ответов)
- **DEBUG** - Отладочная информация
- **INFO** - Информационные сообщения (по умолчанию)
- **WARN** - Предупреждения
- **ERROR** - Только ошибки

### Примеры настройки:

**Включить DEBUG для ClaudeService:**
```xml
<logger name="com.example.services.ClaudeService" level="DEBUG"/>
```

**Включить подробные логи HTTP клиента:**
```xml
<logger name="io.ktor.client" level="DEBUG"/>
```

**Включить все логи приложения:**
```xml
<logger name="com.example" level="DEBUG"/>
```

**Полностью отключить логи Netty:**
```xml
<logger name="io.netty" level="OFF"/>
```

## Диагностика проблем

### Проблема: "Invalid API key"
**Лог:**
```
ERROR c.e.services.ClaudeService - Claude API error response: {"type":"error","error":{"type":"authentication_error","message":"Invalid API key"}}
```
**Решение:** Проверьте переменную окружения `CLAUDE_API_KEY`

### Проблема: Сервер не отвечает
**Проверьте логи:**
```
INFO  Application - Application started in 0.303 seconds.
INFO  Application - Responding at http://0.0.0.0:8080
```
Если этого сообщения нет - проверьте порт 8080 (возможно занят).

### Проблема: Ошибка десериализации
**Лог:**
```
ERROR c.e.services.ClaudeService - Failed to parse error response: ...
```
**Причина:** Claude API вернул неожиданный формат ответа.
**Решение:** Включите DEBUG уровень для просмотра полного ответа.

## Логи в продакшене (VPS)

### Systemd service с логами:
```bash
# Просмотр логов службы
sudo journalctl -u claude-chat -f

# Логи за последний час
sudo journalctl -u claude-chat --since "1 hour ago"

# Логи с ошибками
sudo journalctl -u claude-chat -p err
```

### Сохранение логов в файл на VPS:
Обновите systemd service:
```ini
[Service]
StandardOutput=append:/var/log/claude-chat/app.log
StandardError=append:/var/log/claude-chat/error.log
```

Создайте директорию:
```bash
sudo mkdir -p /var/log/claude-chat
sudo chown www-data:www-data /var/log/claude-chat
```

### Ротация логов (logrotate):
```bash
sudo nano /etc/logrotate.d/claude-chat
```

Содержимое:
```
/var/log/claude-chat/*.log {
    daily
    rotate 14
    compress
    delaycompress
    notifempty
    create 0644 www-data www-data
}
```

## Отладка в реальном времени

### Следить за логами:
```bash
# Gradle
./gradlew run | grep -i "claude\|error"

# JAR
java -jar build/libs/ktor-firtsAI-0.0.1-all.jar | tee logs.txt
```

### Фильтрация логов:
```bash
# Только ошибки
./gradlew run 2>&1 | grep ERROR

# Только логи ClaudeService
./gradlew run 2>&1 | grep ClaudeService

# Логи API запросов
./gradlew run 2>&1 | grep "Claude API"
```

## Мониторинг производительности

Логи содержат временные метки для анализа задержек:
```
2025-01-15 14:32:45.123 [thread] INFO  c.e.services.ClaudeService - Sending message to Claude API
2025-01-15 14:32:46.789 [thread] INFO  c.e.services.ClaudeService - Claude API response status: 200 OK
```
Разница: ~1.6 секунды на запрос к Claude API.
