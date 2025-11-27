# Основы безопасности веб-приложений

## Введение

Безопасность веб-приложений — критически важный аспект разработки. Уязвимости могут привести к утечке данных, финансовым потерям и репутационному ущербу. OWASP (Open Web Application Security Project) ежегодно публикует список наиболее критичных уязвимостей.

## OWASP Top 10

### 1. Broken Access Control

Недостаточный контроль доступа позволяет пользователям выполнять действия вне их полномочий.

**Примеры:**
- Изменение ID в URL для доступа к чужим данным
- Доступ к админ-панели без проверки роли
- Манипуляция JWT-токенами

**Защита:**
- Проверка прав на каждый запрос
- Deny by default — запрещать всё, кроме явно разрешённого
- Логирование попыток несанкционированного доступа

### 2. Cryptographic Failures

Ошибки в криптографии или её отсутствие.

**Примеры:**
- Хранение паролей в открытом виде
- Использование устаревших алгоритмов (MD5, SHA1)
- Передача данных без шифрования (HTTP)

**Защита:**
- HTTPS везде
- Bcrypt/Argon2 для хэширования паролей
- Современные алгоритмы (AES-256, RSA-2048+)

### 3. Injection

Внедрение вредоносного кода через входные данные.

**SQL Injection:**
```sql
-- Уязвимый код
query = "SELECT * FROM users WHERE id = " + userId
-- Атака: userId = "1 OR 1=1"

-- Безопасный код (параметризованный запрос)
query = "SELECT * FROM users WHERE id = ?"
```

**Command Injection:**
```python
# Уязвимо
os.system("ping " + user_input)

# Безопасно
subprocess.run(["ping", user_input], shell=False)
```

**Защита:**
- Параметризованные запросы
- ORM вместо сырого SQL
- Валидация и санитизация ввода
- Principle of least privilege

### 4. Insecure Design

Архитектурные уязвимости, заложенные на этапе проектирования.

**Защита:**
- Threat modeling на этапе проектирования
- Security requirements в ТЗ
- Принцип defense in depth
- Регулярные security review

### 5. Security Misconfiguration

Небезопасные настройки по умолчанию.

**Примеры:**
- Debug-режим в продакшене
- Дефолтные пароли
- Открытые порты и сервисы
- Подробные сообщения об ошибках

**Защита:**
- Hardening-гайды
- Автоматизация конфигурации (IaC)
- Регулярные аудиты

### 6. Vulnerable Components

Использование компонентов с известными уязвимостями.

**Защита:**
- Регулярное обновление зависимостей
- Сканирование (Snyk, Dependabot)
- SBOM (Software Bill of Materials)

### 7. Authentication Failures

Ошибки в системе аутентификации.

**Примеры:**
- Brute force атаки
- Слабые пароли
- Небезопасное восстановление пароля
- Session fixation

**Защита:**
- Rate limiting
- Политика паролей
- Multi-factor authentication (MFA)
- Secure session management

### 8. Data Integrity Failures

Нарушение целостности данных и кода.

**Примеры:**
- Insecure deserialization
- Подмена зависимостей
- CI/CD pipeline атаки

**Защита:**
- Подпись пакетов и образов
- Проверка целостности
- Lock-файлы зависимостей

### 9. Security Logging and Monitoring Failures

Недостаточное логирование и мониторинг.

**Что логировать:**
- Попытки входа (успешные и неуспешные)
- Изменения критичных данных
- Ошибки авторизации
- Аномальная активность

**Защита:**
- Централизованный сбор логов
- Alerting на подозрительные паттерны
- Incident response план

### 10. Server-Side Request Forgery (SSRF)

Сервер делает запросы по URL, контролируемому атакующим.

**Пример:**
```
GET /fetch?url=http://internal-server/admin
```

**Защита:**
- Whitelist разрешённых хостов
- Блокировка internal IP ranges
- Валидация URL

## Cross-Site Scripting (XSS)

Внедрение вредоносного JavaScript в страницы.

**Типы:**
- **Reflected**: Код в URL-параметрах
- **Stored**: Код сохранён в БД
- **DOM-based**: Манипуляция на клиенте

**Защита:**
- Экранирование вывода
- Content Security Policy (CSP)
- HTTPOnly cookies
- Современные фреймворки с автоэкранированием

## Cross-Site Request Forgery (CSRF)

Выполнение действий от имени авторизованного пользователя.

**Защита:**
- CSRF-токены
- SameSite cookies
- Проверка Origin/Referer заголовков

## Безопасность паролей

### Хэширование

**Плохо:**
```python
hash = md5(password)  # Быстрый, уязвим к rainbow tables
hash = sha256(password)  # Быстрый, уязвим к brute force
```

**Хорошо:**
```python
hash = bcrypt.hashpw(password, bcrypt.gensalt(rounds=12))
hash = argon2.hash(password)
```

### Требования к паролям
- Минимум 8 символов
- Проверка по словарям утечек (HaveIBeenPwned)
- Не требуйте частой смены
- Поддержка password managers

## HTTPS и TLS

**Конфигурация:**
- TLS 1.2+ (отключите TLS 1.0/1.1)
- Сильные cipher suites
- HSTS (HTTP Strict Transport Security)
- Certificate transparency

## Безопасность API

- Rate limiting
- API keys для внешних клиентов
- JWT с коротким сроком жизни
- Валидация всех входных данных
- Не раскрывайте внутренние ID

## DevSecOps

**Интеграция безопасности в CI/CD:**
1. SAST — статический анализ кода
2. DAST — динамическое тестирование
3. Dependency scanning
4. Container scanning
5. Secret scanning

## Заключение

Безопасность — это процесс, а не продукт. Регулярные аудиты, обновления, обучение команды и культура security-first помогают защитить приложение и данные пользователей.
