# Docker - Быстрый старт

Шпаргалка по командам Docker для проекта Claude Chat.

## Первый запуск

```bash
# 1. Создайте .env файл
echo "CLAUDE_API_KEY=sk-ant-api03-ваш_ключ" > .env

# 2. Соберите и запустите
docker-compose up -d

# 3. Проверьте логи
docker-compose logs -f

# 4. Откройте браузер
# http://localhost:8080
```

## Основные команды

### Запуск и остановка

```bash
# Запуск (фоновый режим)
docker-compose up -d

# Запуск с выводом логов
docker-compose up

# Остановка
docker-compose stop

# Остановка и удаление
docker-compose down

# Перезапуск
docker-compose restart
```

### Сборка

```bash
# Сборка образа
docker-compose build

# Сборка без кэша (полная пересборка)
docker-compose build --no-cache

# Пересборка и запуск
docker-compose up -d --build
```

### Просмотр информации

```bash
# Статус контейнеров
docker-compose ps

# Логи (в реальном времени)
docker-compose logs -f

# Последние 100 строк логов
docker-compose logs --tail=100

# Использование ресурсов
docker stats claude-chat-app
```

### Отладка

```bash
# Зайти внутрь контейнера
docker-compose exec claude-chat sh

# Внутри контейнера:
ls -la             # Просмотр файлов
ps aux             # Запущенные процессы
wget http://localhost:8080/health  # Проверка API
exit               # Выход

# Просмотр конфигурации
docker-compose config
```

### Очистка

```bash
# Остановить и удалить контейнеры
docker-compose down

# Удалить образы
docker rmi claude-chat:latest

# Очистить все неиспользуемые образы
docker image prune -a

# Полная очистка Docker
docker system prune -a --volumes
```

## Локальная разработка

### При изменении кода

```bash
# 1. Остановите контейнер
docker-compose down

# 2. Внесите изменения в код
# ...

# 3. Пересоберите и запустите
docker-compose up -d --build

# 4. Проверьте логи
docker-compose logs -f
```

### Быстрый рестарт

```bash
# Если изменили только статические файлы (HTML/CSS/JS)
docker-compose restart

# Если изменили Kotlin код
docker-compose up -d --build
```

## Деплой на VPS

### Первый деплой

```bash
# На VPS сервере:

# 1. Клонируйте проект
git clone https://github.com/your-repo/ResearchAI.git
cd ResearchAI

# 2. Создайте .env
nano .env
# CLAUDE_API_KEY=ваш_ключ

# 3. Запустите
docker-compose up -d

# 4. Проверьте
curl http://localhost:8080/health
```

### Обновление на VPS

```bash
# На VPS сервере:

# 1. Обновите код
git pull origin main

# 2. Пересоберите и перезапустите
docker-compose down
docker-compose build --no-cache
docker-compose up -d

# 3. Проверьте логи
docker-compose logs -f
```

## Переменные окружения

```bash
# .env файл
CLAUDE_API_KEY=sk-ant-api03-...           # ОБЯЗАТЕЛЬНО
CLAUDE_MODEL=claude-haiku-4-5-20251001   # Опционально
CLAUDE_MAX_TOKENS=1024                   # Опционально
CLAUDE_TEMPERATURE=1.0                   # Опционально
```

## Порты

```bash
# По умолчанию: 8080:8080
# Изменить в docker-compose.yml:
ports:
  - "80:8080"     # Открыть на порту 80
  - "3000:8080"   # Открыть на порту 3000
```

## Проблемы и решения

### Контейнер не запускается

```bash
# Проверьте логи
docker-compose logs

# Проверьте переменные окружения
docker-compose config

# Пересоберите без кэша
docker-compose build --no-cache
docker-compose up -d
```

### Порт занят

```bash
# Проверьте, что занимает порт 8080
sudo lsof -i :8080

# Остановите процесс или измените порт в docker-compose.yml
```

### Ошибка "Cannot connect to Docker daemon"

```bash
# Запустите Docker
sudo systemctl start docker

# Добавьте пользователя в группу docker
sudo usermod -aG docker $USER

# Перелогиньтесь
exit
```

### Нет свободного места

```bash
# Очистите неиспользуемые образы
docker system prune -a

# Проверьте использование места
docker system df
```

## Healthcheck

```bash
# Статус healthcheck
docker inspect --format='{{json .State.Health}}' claude-chat-app | jq

# Ручная проверка health endpoint
curl http://localhost:8080/health
```

## Логи и мониторинг

```bash
# Логи в реальном времени с временными метками
docker-compose logs -f -t

# Логи только ошибок
docker-compose logs | grep ERROR

# Логи за последний час
docker-compose logs --since 1h

# Экспорт логов в файл
docker-compose logs > logs.txt
```

## Резервное копирование

```bash
# Бэкап .env файла
cp .env .env.backup

# Бэкап всего проекта
tar -czf backup-$(date +%Y%m%d).tar.gz \
  --exclude='build' \
  --exclude='.gradle' \
  --exclude='.kotlin' \
  .

# Восстановление
tar -xzf backup-20250115.tar.gz
```

## Полезные алиасы

Добавьте в `~/.bashrc` или `~/.zshrc`:

```bash
# Docker Compose алиасы
alias dc='docker-compose'
alias dcup='docker-compose up -d'
alias dcdown='docker-compose down'
alias dclogs='docker-compose logs -f'
alias dcrestart='docker-compose restart'
alias dcbuild='docker-compose build --no-cache'

# Docker алиасы
alias dps='docker ps'
alias dlog='docker logs -f'
alias dexec='docker exec -it'
alias dprune='docker system prune -a'
```

После добавления:
```bash
source ~/.bashrc  # или source ~/.zshrc
```

Использование:
```bash
dcup          # вместо docker-compose up -d
dclogs        # вместо docker-compose logs -f
dcbuild       # вместо docker-compose build --no-cache
```

## Ссылки

- 📖 [Подробная инструкция по деплою](DEPLOYMENT.md)
- 📖 [Документация по логам](LOGGING.md)
- 📖 [Документация по фронтенду](FRONTEND.md)
- 📖 [Основной README](README.md)

## Контакты

При возникновении проблем:
1. Проверьте логи: `docker-compose logs -f`
2. Проверьте статус: `docker-compose ps`
3. Изучите DEPLOYMENT.md
4. Откройте issue в репозитории
