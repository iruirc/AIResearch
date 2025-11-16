/**
 * Модуль для работы с аутентификацией
 */

class AuthManager {
    constructor() {
        this.jwtToken = null;
        this.user = null;
        this.loadTokenFromStorage();
    }

    /**
     * Загрузить JWT токен из localStorage
     */
    loadTokenFromStorage() {
        const storedToken = localStorage.getItem('jwt_token');
        if (storedToken) {
            this.jwtToken = storedToken;
        }
    }

    /**
     * Сохранить JWT токен
     */
    setToken(token) {
        this.jwtToken = token;
        localStorage.setItem('jwt_token', token);
    }

    /**
     * Получить JWT токен
     */
    getToken() {
        return this.jwtToken;
    }

    /**
     * Проверить, аутентифицирован ли пользователь
     */
    async isAuthenticated() {
        if (!this.jwtToken) {
            return false;
        }

        try {
            // Проверяем токен через API
            const response = await fetch('/auth/me', {
                headers: {
                    'Authorization': `Bearer ${this.jwtToken}`
                }
            });

            if (response.ok) {
                this.user = await response.json();
                return true;
            } else {
                // Токен невалиден - удаляем
                this.clearToken();
                return false;
            }
        } catch (error) {
            console.error('Ошибка проверки аутентификации:', error);
            return false;
        }
    }

    /**
     * Получить информацию о текущем пользователе
     */
    getUser() {
        return this.user;
    }

    /**
     * Выход из системы
     */
    async logout() {
        try {
            await fetch('/auth/logout');
        } catch (error) {
            console.error('Ошибка при выходе:', error);
        } finally {
            this.clearToken();
            window.location.href = '/login.html?logged_out=true';
        }
    }

    /**
     * Очистить токен
     */
    clearToken() {
        this.jwtToken = null;
        this.user = null;
        localStorage.removeItem('jwt_token');
    }

    /**
     * Редирект на страницу логина
     */
    redirectToLogin() {
        window.location.href = '/login.html';
    }

    /**
     * Добавить JWT токен к запросу
     */
    getAuthHeaders() {
        if (this.jwtToken) {
            return {
                'Authorization': `Bearer ${this.jwtToken}`,
                'Content-Type': 'application/json'
            };
        }
        return {
            'Content-Type': 'application/json'
        };
    }
}

// Создаем глобальный экземпляр
window.authManager = new AuthManager();

// Проверяем аутентификацию при загрузке страницы
document.addEventListener('DOMContentLoaded', async () => {
    // Проверяем, находимся ли мы на login.html
    if (window.location.pathname.includes('login.html')) {
        // Проверяем, пришли ли мы после успешной авторизации
        const urlParams = new URLSearchParams(window.location.search);
        const success = urlParams.get('success');
        const token = urlParams.get('token');

        if (success === 'true' && token) {
            // Успешная авторизация - сохраняем токен и редиректим
            console.log('✅ OAuth успешен, сохраняем токен и редиректим на главную...');
            window.authManager.setToken(token);

            // Показываем сообщение об успехе
            const container = document.querySelector('.login-container');
            if (container) {
                container.innerHTML = '<div style="text-align: center; padding: 40px;"><h2>✅ Вход выполнен!</h2><p>Перенаправление...</p></div>';
            }

            // Небольшая задержка для визуального feedback и редирект
            setTimeout(() => {
                window.location.href = '/';
            }, 500);
            return;
        }

        // Обычная страница логина - проверяем, может пользователь уже авторизован
        const isAuth = await window.authManager.isAuthenticated();
        if (isAuth) {
            // Уже авторизован - редиректим на главную
            window.location.href = '/';
        }
        return;
    }

    // На других страницах (index.html) - требуем авторизацию
    const isAuth = await window.authManager.isAuthenticated();
    if (!isAuth) {
        window.authManager.redirectToLogin();
    } else {
        // Показываем информацию о пользователе
        displayUserInfo();
    }
});

/**
 * Отобразить информацию о пользователе в UI
 */
function displayUserInfo() {
    const user = window.authManager.getUser();
    if (!user) return;

    // Добавляем информацию о пользователе в шапку
    const header = document.querySelector('.chat-header .header-left');
    if (header) {
        const userInfo = document.createElement('div');
        userInfo.className = 'user-info';
        userInfo.style.cssText = 'display: flex; align-items: center; gap: 8px; margin-left: 16px;';
        userInfo.innerHTML = `
            <span style="font-size: 14px; color: #666;">${user.name}</span>
            <button id="logoutButton" style="padding: 6px 12px; background: #f44336; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">
                Выйти
            </button>
        `;
        header.appendChild(userInfo);

        // Добавляем обработчик на кнопку выхода
        document.getElementById('logoutButton').addEventListener('click', () => {
            if (confirm('Вы уверены, что хотите выйти?')) {
                window.authManager.logout();
            }
        });
    }
}
