# 🚀 Быстрый старт - ResearchAI Chat

Минимальная инструкция для быстрого запуска проекта.

## Вариант 1: Локальный запуск (без Docker)

```bash
# 1. Установите API ключ
export CLAUDE_API_KEY=sk-ant-api03-ваш_ключ

# 2. Запустите сервер
./gradlew run

# 3. Откройте браузер
# http://localhost:8080
```

## Вариант 2: Docker (локально)

```bash
# 1. Создайте .env файл
echo "CLAUDE_API_KEY=sk-ant-api03-ваш_ключ" > .env

# 2. Запустите
docker-compose up -d

# 3. Откройте браузер
# http://localhost:8080

# 4. Просмотр логов
docker-compose logs -f

# 5. Остановка
docker-compose down
```

## Вариант 3: Деплой на VPS

### На вашем компьютере:

```bash
# Загрузите проект на VPS
scp -r ResearchAI username@your-vps-ip:~/
```

### На VPS сервере:

```bash
# 1. Установите Docker (один раз)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 2. Перейдите в проект
cd ~/ResearchAI

# 3. Создайте .env файл
nano .env
# Добавьте: CLAUDE_API_KEY=ваш_ключ
# Сохраните: Ctrl+O, Enter, Ctrl+X

# 4. Запустите
docker-compose up -d

# 5. Проверьте
curl http://localhost:8080/health

# 6. (Опционально) Настройте Nginx + HTTPS
# См. подробную инструкцию в DEPLOYMENT.md
```

## Тестирование

```bash
# Проверка API
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Привет!"}'

# Проверка health
curl http://localhost:8080/health
```

## Структура файлов проекта

```
ResearchAI/
├── src/main/
│   ├── kotlin/                 # Kotlin код
│   │   ├── Application.kt      # Главный файл
│   │   ├── Routing.kt          # Роутинг
│   │   ├── config/             # Конфигурация
│   │   ├── models/             # Модели данных
│   │   ├── routes/             # HTTP endpoints
│   │   └── services/           # Бизнес-логика
│   └── resources/
│       ├── static/             # Фронтенд (HTML/CSS/JS)
│       ├── application.yaml    # Настройки Ktor
│       └── logback.xml         # Настройки логов
├── Dockerfile                  # Docker образ
├── docker-compose.yml          # Docker Compose
├── .env.example                # Пример переменных окружения
├── build.gradle.kts            # Gradle конфигурация
└── README.md                   # Документация
```

## Документация

| Файл | Описание |
|------|----------|
| [README.md](README.md) | Основная документация проекта |
| [DEPLOYMENT.md](DEPLOYMENT.md) | **Подробная инструкция по деплою на VPS** |
| [DOCKER-QUICKSTART.md](DOCKER-QUICKSTART.md) | Шпаргалка по Docker командам |
| [LOGGING.md](LOGGING.md) | Информация о логах |
| [FRONTEND.md](FRONTEND.md) | Документация фронтенда |

## Основные команды

### Gradle

```bash
./gradlew run              # Запуск в режиме разработки
./gradlew build            # Сборка проекта
./gradlew buildFatJar      # Сборка executable JAR
./gradlew test             # Запуск тестов
```

### Docker Compose

```bash
docker-compose up -d       # Запуск
docker-compose down        # Остановка
docker-compose logs -f     # Логи
docker-compose restart     # Перезапуск
docker-compose build       # Сборка образа
```

### Управление на VPS

```bash
# Просмотр логов
docker-compose logs -f

# Обновление проекта
git pull origin main
docker-compose down
docker-compose build --no-cache
docker-compose up -d

# Статус
docker-compose ps

# Перезапуск
docker-compose restart
```

## Переменные окружения

Создайте файл `.env`:

```env
# ОБЯЗАТЕЛЬНО
CLAUDE_API_KEY=sk-ant-api03-ваш_ключ_здесь

# ОПЦИОНАЛЬНО (можно не указывать)
CLAUDE_MODEL=claude-haiku-4-5-20251001
CLAUDE_MAX_TOKENS=1000
CLAUDE_TEMPERATURE=1.0
```

## Порты

| Порт | Описание |
|------|----------|
| 8080 | HTTP сервер (веб-интерфейс + API) |

## API Endpoints

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/` | GET | Главная страница (перенаправляет на веб-интерфейс) |
| `/chat` | POST | Отправка сообщения в Claude |
| `/health` | GET | Проверка статуса сервера |

## Устранение проблем

### Проблема: Контейнер не запускается

```bash
# Проверьте логи
docker-compose logs

# Проверьте переменные окружения
docker-compose config

# Пересоберите
docker-compose build --no-cache
docker-compose up -d
```

### Проблема: Ошибка "Invalid API key"

```bash
# Проверьте .env файл
cat .env

# Убедитесь, что ключ правильный
# Получите новый ключ на https://console.anthropic.com/
```

### Проблема: Порт 8080 занят

```bash
# Проверьте, что занимает порт
sudo lsof -i :8080

# Остановите процесс или измените порт в docker-compose.yml:
ports:
  - "3000:8080"  # Будет доступно на порту 3000
```

### Проблема: Нет доступа с внешнего IP

```bash
# Проверьте firewall
sudo ufw status
sudo ufw allow 8080

# Проверьте, что сервер слушает 0.0.0.0, а не 127.0.0.1
docker-compose logs | grep "Responding at"
# Должно быть: Responding at http://0.0.0.0:8080
```

## Следующие шаги

1. ✅ Запустите проект локально
2. ✅ Протестируйте веб-интерфейс
3. ✅ Разверните на VPS
4. ✅ Настройте домен и HTTPS
5. ✅ Настройте мониторинг и бэкапы

## Получение помощи

1. Проверьте логи: `docker-compose logs -f`
2. Изучите [DEPLOYMENT.md](DEPLOYMENT.md)
3. Откройте issue в репозитории
4. Проверьте статус: `docker-compose ps`

## Полезные ссылки

- 🔑 [Получить API ключ Claude](https://console.anthropic.com/)
- 🐳 [Docker Documentation](https://docs.docker.com/)
- 📦 [Ktor Documentation](https://ktor.io/docs/)
- 🔒 [Let's Encrypt (SSL)](https://letsencrypt.org/)

---

**Проект готов к использованию! Просто следуйте одному из вариантов запуска выше.** 🎉
