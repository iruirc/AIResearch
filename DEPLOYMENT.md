# Деплой Claude Chat на VPS через Docker

Подробная пошаговая инструкция по развертыванию приложения на VPS сервере.

## Требования

### Локально (ваш компьютер):
- Git
- SSH доступ к VPS

### На VPS сервере:
- Ubuntu 20.04+ (или другой Linux дистрибутив)
- Docker 20.10+
- Docker Compose 2.0+
- Открытый порт 80 и/или 443
- Минимум 1GB RAM
- Минимум 2GB свободного места на диске

---

## Часть 1: Подготовка VPS сервера

### Шаг 1.1: Подключение к VPS

```bash
# Замените на ваши данные
ssh root@your-vps-ip
# или с указанным пользователем
ssh username@your-vps-ip
```

### Шаг 1.2: Обновление системы

```bash
# Обновляем список пакетов
sudo apt update

# Обновляем установленные пакеты
sudo apt upgrade -y
```

### Шаг 1.3: Установка Docker

```bash
# Удаляем старые версии Docker (если есть)
sudo apt remove docker docker-engine docker.io containerd runc

# Устанавливаем зависимости
sudo apt install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Добавляем официальный GPG ключ Docker
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# Добавляем репозиторий Docker
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Устанавливаем Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io

# Проверяем установку
sudo docker --version
```

Вывод должен быть похож на:
```
Docker version 24.0.7, build afdd53b
```

### Шаг 1.4: Установка Docker Compose

```bash
# Скачиваем Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/download/v2.23.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose

# Делаем файл исполняемым
sudo chmod +x /usr/local/bin/docker-compose

# Проверяем установку
docker-compose --version
```

Вывод должен быть похож на:
```
Docker Compose version v2.23.0
```

### Шаг 1.5: Настройка пользователя для Docker (опционально)

```bash
# Добавляем текущего пользователя в группу docker
sudo usermod -aG docker $USER

# Перелогиниваемся для применения изменений
exit
ssh username@your-vps-ip

# Проверяем, что Docker работает без sudo
docker ps
```

### Шаг 1.6: Настройка firewall (UFW)

```bash
# Устанавливаем UFW если не установлен
sudo apt install -y ufw

# Разрешаем SSH (ВАЖНО! Иначе потеряете доступ)
sudo ufw allow 22/tcp
sudo ufw allow OpenSSH

# Разрешаем HTTP и HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Включаем firewall
sudo ufw enable

# Проверяем статус
sudo ufw status
```

---

## Часть 2: Подготовка приложения

### Шаг 2.1: Получение кода на VPS

**Вариант А: Через Git (рекомендуется)**

```bash
# Устанавливаем Git если не установлен
sudo apt install -y git

# Клонируем репозиторий (если он на GitHub/GitLab)
git clone https://github.com/your-username/ResearchAI.git
cd ResearchAI
```

**Вариант Б: Загрузка с локального компьютера**

На вашем локальном компьютере:

```bash
# Переходим в директорию проекта
cd /path/to/ResearchAI

# Создаем архив (исключая ненужные файлы)
tar -czf claude-chat.tar.gz \
  --exclude='build' \
  --exclude='.gradle' \
  --exclude='.kotlin' \
  --exclude='.idea' \
  --exclude='.git' \
  .

# Загружаем на VPS
scp claude-chat.tar.gz username@your-vps-ip:~/
```

На VPS:

```bash
# Создаем директорию
mkdir -p ~/claude-chat
cd ~/claude-chat

# Распаковываем архив
tar -xzf ~/claude-chat.tar.gz

# Удаляем архив
rm ~/claude-chat.tar.gz
```

### Шаг 2.2: Настройка переменных окружения

```bash
# Создаем .env файл
nano .env
```

Добавьте следующее содержимое (замените на ваши значения):

```env
# ОБЯЗАТЕЛЬНО: Ваш API ключ Claude
CLAUDE_API_KEY=sk-ant-api03-ваш_ключ_здесь

# Опционально: Настройки модели
CLAUDE_MODEL=claude-haiku-4-5-20251001
CLAUDE_MAX_TOKENS=64000
CLAUDE_TEMPERATURE=1.0
```

Сохраните файл: `Ctrl+O`, `Enter`, `Ctrl+X`

**ВАЖНО:** Убедитесь, что .env файл защищен:

```bash
chmod 600 .env
```

### Шаг 2.3: Проверка файлов

```bash
# Проверяем наличие необходимых файлов
ls -la

# Должны быть:
# - Dockerfile
# - docker-compose.yml
# - .env
# - src/
# - build.gradle.kts
# и т.д.
```

---

## Часть 3: Сборка и запуск Docker контейнера

### Шаг 3.1: Сборка образа

```bash
# Собираем Docker образ (первый раз занимает 5-10 минут)
docker-compose build

# Или с пересборкой без кэша
docker-compose build --no-cache
```

Вы увидите процесс сборки:
```
[+] Building 234.5s (18/18) FINISHED
 => [builder 1/8] FROM docker.io/library/gradle:8.5-jdk17
 => [builder 2/8] WORKDIR /app
 ...
 => exporting to image
```

### Шаг 3.2: Запуск контейнера

```bash
# Запускаем контейнер в фоновом режиме
docker-compose up -d
```

Вывод:
```
[+] Running 2/2
 ✔ Network claude-network      Created
 ✔ Container claude-chat-app   Started
```

### Шаг 3.3: Проверка запуска

```bash
# Проверяем статус контейнера
docker-compose ps

# Должно быть:
# NAME              STATE    PORTS
# claude-chat-app   Up       0.0.0.0:8080->8080/tcp
```

### Шаг 3.4: Просмотр логов

```bash
# Смотрим логи контейнера
docker-compose logs -f

# Или последние 100 строк
docker-compose logs --tail=100

# Выход из режима просмотра: Ctrl+C
```

Вы должны увидеть:
```
INFO  Application - Application started in 2.345 seconds.
INFO  Application - Responding at http://0.0.0.0:8080
```

### Шаг 3.5: Проверка работоспособности

```bash
# Проверяем health endpoint
curl http://localhost:8080/health

# Ответ должен быть:
# {"status":"ok"}

# Проверяем веб-интерфейс
curl http://localhost:8080/
```

---

## Часть 4: Настройка Nginx (Reverse Proxy)

### Шаг 4.1: Установка Nginx

```bash
# Устанавливаем Nginx
sudo apt install -y nginx

# Запускаем и добавляем в автозагрузку
sudo systemctl start nginx
sudo systemctl enable nginx

# Проверяем статус
sudo systemctl status nginx
```

### Шаг 4.2: Настройка конфигурации Nginx

```bash
# Создаем конфигурацию для приложения
sudo nano /etc/nginx/sites-available/claude-chat
```

Добавьте следующее содержимое:

```nginx
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;

    # Логи
    access_log /var/log/nginx/claude-chat-access.log;
    error_log /var/log/nginx/claude-chat-error.log;

    # Прокси к Docker контейнеру
    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;

        # Заголовки
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Таймауты
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Увеличиваем лимит размера тела запроса (для больших сообщений)
    client_max_body_size 10M;
}
```

**Замените** `your-domain.com` на ваш реальный домен или IP адрес VPS.

Сохраните: `Ctrl+O`, `Enter`, `Ctrl+X`

### Шаг 4.3: Активация конфигурации

```bash
# Создаем символическую ссылку
sudo ln -s /etc/nginx/sites-available/claude-chat /etc/nginx/sites-enabled/

# Проверяем конфигурацию
sudo nginx -t

# Должно быть:
# nginx: configuration file /etc/nginx/nginx.conf test is successful

# Перезагружаем Nginx
sudo systemctl reload nginx
```

### Шаг 4.4: Проверка доступа

Откройте браузер и перейдите по адресу:
```
http://your-domain.com
или
http://your-vps-ip
```

Вы должны увидеть веб-интерфейс чата!

---

## Часть 5: Настройка HTTPS (SSL сертификат)

### Шаг 5.1: Установка Certbot

```bash
# Устанавливаем Certbot и плагин для Nginx
sudo apt install -y certbot python3-certbot-nginx
```

### Шаг 5.2: Получение SSL сертификата

```bash
# Получаем сертификат (замените на ваш домен)
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# Следуйте инструкциям:
# 1. Введите email
# 2. Согласитесь с условиями: Y
# 3. Выберите опцию 2 (Redirect HTTP to HTTPS)
```

Certbot автоматически:
- Получит сертификат от Let's Encrypt
- Обновит конфигурацию Nginx
- Настроит автоматическое обновление

### Шаг 5.3: Проверка автоматического обновления

```bash
# Проверяем таймер автообновления
sudo systemctl status certbot.timer

# Тестируем обновление (dry-run)
sudo certbot renew --dry-run
```

### Шаг 5.4: Проверка HTTPS

Откройте браузер:
```
https://your-domain.com
```

Вы должны увидеть зеленый замок 🔒 в адресной строке!

---

## Часть 6: Управление приложением

### Основные команды Docker Compose

```bash
# Запуск контейнера
docker-compose up -d

# Остановка контейнера
docker-compose stop

# Перезапуск контейнера
docker-compose restart

# Остановка и удаление контейнера
docker-compose down

# Просмотр логов
docker-compose logs -f

# Просмотр статуса
docker-compose ps

# Выполнение команды внутри контейнера
docker-compose exec claude-chat sh
```

### Обновление приложения

```bash
# 1. Останавливаем контейнер
docker-compose down

# 2. Обновляем код (git pull или загружаем новые файлы)
git pull origin main

# 3. Пересобираем образ
docker-compose build --no-cache

# 4. Запускаем новую версию
docker-compose up -d

# 5. Проверяем логи
docker-compose logs -f
```

### Просмотр ресурсов

```bash
# Использование ресурсов контейнером
docker stats claude-chat-app

# Информация о контейнере
docker inspect claude-chat-app
```

### Очистка неиспользуемых образов

```bash
# Удаляем неиспользуемые образы
docker image prune -a

# Удаляем все неиспользуемые ресурсы
docker system prune -a
```

---

## Часть 7: Мониторинг и логи

### Логи приложения

```bash
# Все логи
docker-compose logs

# Последние 100 строк
docker-compose logs --tail=100

# Логи в реальном времени
docker-compose logs -f

# Логи с временными метками
docker-compose logs -t
```

### Логи Nginx

```bash
# Access лог (все запросы)
sudo tail -f /var/log/nginx/claude-chat-access.log

# Error лог (только ошибки)
sudo tail -f /var/log/nginx/claude-chat-error.log
```

### Системные логи

```bash
# Логи Docker daemon
sudo journalctl -u docker -f

# Системные логи
sudo journalctl -xe
```

### Healthcheck

Docker автоматически проверяет здоровье контейнера:

```bash
# Проверка статуса healthcheck
docker inspect --format='{{json .State.Health}}' claude-chat-app | jq
```

---

## Часть 8: Резервное копирование

### Создание резервной копии

```bash
# Создаем директорию для бэкапов
mkdir -p ~/backups

# Архивируем проект
tar -czf ~/backups/claude-chat-$(date +%Y%m%d).tar.gz \
  -C ~ \
  claude-chat

# Список бэкапов
ls -lh ~/backups/
```

### Автоматическое резервное копирование

Создайте cron задачу:

```bash
# Редактируем crontab
crontab -e
```

Добавьте:

```cron
# Ежедневный бэкап в 2:00 AM
0 2 * * * tar -czf ~/backups/claude-chat-$(date +\%Y\%m\%d).tar.gz -C ~ claude-chat

# Удаление старых бэкапов (старше 7 дней)
0 3 * * * find ~/backups/ -name "claude-chat-*.tar.gz" -mtime +7 -delete
```

---

## Часть 9: Безопасность

### Рекомендации по безопасности

1. **Защита .env файла:**
   ```bash
   chmod 600 .env
   ```

2. **Обновление системы:**
   ```bash
   sudo apt update && sudo apt upgrade -y
   ```

3. **Настройка fail2ban (защита от брутфорса):**
   ```bash
   sudo apt install -y fail2ban
   sudo systemctl enable fail2ban
   sudo systemctl start fail2ban
   ```

4. **Изменение SSH порта (опционально):**
   ```bash
   sudo nano /etc/ssh/sshd_config
   # Измените Port 22 на другой порт, например Port 2222
   sudo systemctl restart sshd
   ```

5. **Отключение root логина через SSH:**
   ```bash
   sudo nano /etc/ssh/sshd_config
   # Измените PermitRootLogin yes на PermitRootLogin no
   sudo systemctl restart sshd
   ```

---

## Часть 10: Устранение проблем

### Контейнер не запускается

```bash
# Проверяем логи
docker-compose logs

# Проверяем переменные окружения
docker-compose config

# Пересобираем без кэша
docker-compose build --no-cache
docker-compose up -d
```

### Нет доступа к приложению

```bash
# Проверяем, что контейнер запущен
docker-compose ps

# Проверяем порты
sudo netstat -tlnp | grep 8080

# Проверяем firewall
sudo ufw status

# Проверяем Nginx
sudo nginx -t
sudo systemctl status nginx
```

### Проблемы с памятью

```bash
# Увеличиваем лимит памяти в docker-compose.yml
# Под services -> claude-chat -> deploy -> resources -> limits:
memory: 1G  # Было 512M

# Перезапускаем
docker-compose down
docker-compose up -d
```

### Сертификат SSL не работает

```bash
# Проверяем Certbot
sudo certbot certificates

# Обновляем вручную
sudo certbot renew

# Проверяем конфигурацию Nginx
sudo nginx -t
```

---

## Часть 11: Полезные команды

```bash
# Перезапуск всех сервисов
docker-compose restart && sudo systemctl restart nginx

# Полная очистка и переустановка
docker-compose down
docker-compose build --no-cache
docker-compose up -d

# Просмотр всех запущенных контейнеров
docker ps -a

# Удаление всех остановленных контейнеров
docker container prune

# Проверка использования диска
df -h
docker system df
```

---

## Готово! 🎉

Ваше приложение Claude Chat теперь развернуто на VPS и доступно по адресу:
- HTTP: `http://your-domain.com`
- HTTPS: `https://your-domain.com`

### Следующие шаги:

1. ✅ Протестируйте веб-интерфейс
2. ✅ Настройте регулярные обновления
3. ✅ Настройте мониторинг
4. ✅ Настройте резервное копирование

## Поддержка

Если у вас возникли проблемы:

1. Проверьте логи: `docker-compose logs -f`
2. Проверьте статус: `docker-compose ps`
3. Изучите документацию: README.md, LOGGING.md
4. Откройте issue в репозитории проекта
