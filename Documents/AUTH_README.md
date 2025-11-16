# OAuth Authentication Implementation

## Обзор

В проект добавлена полная поддержка OAuth аутентификации через Google (с возможностью расширения на GitHub и Apple).

## Архитектура

### Backend

```
auth/
├── domain/
│   ├── models/          # User, AuthSession, OAuthProvider
│   └── repository/      # UserRepository
├── data/
│   ├── provider/        # GoogleAuthProvider
│   └── repository/      # UserRepositoryImpl
├── service/
│   ├── JWTService      # Генерация и валидация JWT токенов
│   └── AuthService     # Управление аутентификацией
└── routes/
    └── AuthRoutes      # OAuth endpoints
```

### Frontend

- `login.html` - Страница входа с кнопками OAuth
- `auth.js` - JavaScript модуль для управления аутентификацией
- Интеграция с существующим `app.js`

## Endpoints

### Authentication

- `GET /auth/google` - Начать авторизацию через Google
- `GET /auth/google/callback` - Callback от Google OAuth
- `GET /auth/logout` - Выход из системы
- `GET /auth/me` - Получить информацию о текущем пользователе

## Настройка

### 1. Создание Google OAuth Application

1. Перейдите в [Google Cloud Console](https://console.cloud.google.com/)
2. Создайте новый проект или выберите существующий
3. Включите Google+ API
4. Перейдите в "Credentials" → "Create Credentials" → "OAuth 2.0 Client ID"
5. Выберите "Web application"
6. Добавьте Authorized redirect URI: `http://localhost:8080/auth/google/callback`
7. Скопируйте Client ID и Client Secret

### 2. Настройка переменных окружения

Добавьте в `.env` файл:

```bash
# JWT Authentication (REQUIRED)
JWT_SECRET=your-super-secret-key-min-256-bits
JWT_ISSUER=researchai
JWT_AUDIENCE=researchai-users
JWT_REALM=ResearchAI
JWT_EXPIRATION_MS=3600000

# Google OAuth (OPTIONAL)
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GOOGLE_REDIRECT_URI=http://localhost:8080/auth/google/callback
```

**ВАЖНО**: В production обязательно замените `JWT_SECRET` на криптографически стойкий случайный ключ!

### 3. Хранение данных

Пользователи автоматически сохраняются в `data/users/` директории в формате JSON.

## Использование

### Процесс авторизации

1. Пользователь открывает `/login.html`
2. Нажимает "Войти через Google"
3. Редирект на Google для авторизации
4. После успешной авторизации редирект обратно на `/auth/google/callback`
5. Сервер:
   - Обменивает authorization code на access token
   - Получает информацию о пользователе от Google
   - Создает или обновляет пользователя в БД
   - Генерирует JWT токен
   - Создает сессию
   - Редиректит на главную страницу с токеном
6. Frontend сохраняет JWT токен в localStorage
7. Все последующие запросы включают JWT токен в заголовке `Authorization`

### Проверка аутентификации

На каждой странице (кроме `login.html`) автоматически происходит проверка:

```javascript
const isAuth = await window.authManager.isAuthenticated();
if (!isAuth) {
    window.authManager.redirectToLogin();
}
```

### Выход из системы

```javascript
window.authManager.logout();
```

Или через кнопку "Выйти" в UI.

## Безопасность

### Реализованные меры безопасности

1. **CSRF Protection**:
   - Использование state parameter в OAuth flow
   - Проверка state в callback

2. **JWT Tokens**:
   - HMAC256 подпись
   - Ограниченное время жизни (1 час по умолчанию)
   - Валидация audience и issuer

3. **Session Security**:
   - HttpOnly cookies для сессий
   - Secure flag для production (HTTPS)

4. **Password-less Authentication**:
   - Нет хранения паролей
   - Делегирование аутентификации OAuth провайдерам

### Рекомендации для Production

1. **JWT_SECRET**: Используйте криптографически стойкий случайный ключ минимум 256 бит
   ```bash
   openssl rand -base64 64
   ```

2. **HTTPS**: Обязательно включите HTTPS в production
   ```kotlin
   cookie.secure = true  // в Application.kt
   ```

3. **Cookie Settings**:
   ```kotlin
   cookie.httpOnly = true
   cookie.secure = true  // только для HTTPS
   cookie.sameSite = CookieSameSite.Strict
   ```

4. **Token Expiration**: Настройте разумное время жизни токенов
   ```bash
   JWT_EXPIRATION_MS=3600000  # 1 час
   ```

5. **Rate Limiting**: Добавьте rate limiting для auth endpoints

## Добавление новых OAuth провайдеров

### GitHub

1. Создайте GitHub OAuth App в GitHub Settings
2. Добавьте переменные окружения:
   ```bash
   GITHUB_CLIENT_ID=your-github-client-id
   GITHUB_CLIENT_SECRET=your-github-client-secret
   GITHUB_REDIRECT_URI=http://localhost:8080/auth/github/callback
   ```

3. Создайте `GitHubAuthProvider.kt` аналогично `GoogleAuthProvider.kt`

4. Добавьте routes в `AuthRoutes.kt`:
   ```kotlin
   get("/auth/github") { ... }
   get("/auth/github/callback") { ... }
   ```

5. Обновите UI в `login.html` (раскомментируйте кнопку GitHub)

### Apple Sign In

Аналогично, но требует дополнительной настройки с Apple Developer Account.

## Troubleshooting

### "OAuth state mismatch"

Проблема с session cookies. Проверьте:
- Cookie settings в Application.kt
- Браузер принимает cookies
- Нет конфликтов с другими сессиями

### "Failed to exchange code for token"

Проверьте:
- GOOGLE_CLIENT_ID и GOOGLE_CLIENT_SECRET корректны
- Redirect URI совпадает с настройками в Google Console
- Firewall не блокирует запросы к Google API

### "Not authenticated" при наличии токена

Проверьте:
- JWT_SECRET не изменился
- Токен не истек
- Формат токена корректен (Bearer token)

## Структура данных

### User

```json
{
  "id": "user_uuid",
  "email": "user@example.com",
  "name": "John Doe",
  "provider": "GOOGLE",
  "providerId": "google-user-id",
  "avatar": "https://...",
  "createdAt": 1234567890,
  "lastLoginAt": 1234567890
}
```

### AuthSession (Cookie)

```kotlin
data class AuthSession(
    val userId: String,
    val email: String,
    val name: String,
    val provider: OAuthProvider
)
```

## Миграция существующих пользователей

Если у вас уже есть пользователи без аутентификации, реализация сделана с обратной совместимостью:
- Существующие chat routes продолжают работать
- JWT middleware пока не включен для обратной совместимости
- Можно постепенно мигрировать пользователей на OAuth

## Дальнейшие улучшения

1. **Защита API endpoints**: Добавить `authenticate("jwt-auth")` middleware для всех chat routes
2. **Refresh tokens**: Реализовать refresh token mechanism
3. **User profiles**: Расширить модель User дополнительными полями
4. **Admin panel**: Добавить панель администратора для управления пользователями
5. **Rate limiting**: Добавить ограничение частоты запросов
6. **Audit logging**: Логирование всех операций аутентификации
