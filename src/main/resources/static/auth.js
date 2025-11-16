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

    // Добавляем информацию о пользователе в футер боковой панели
    const sidebarFooter = document.getElementById('sidebarFooter');
    if (sidebarFooter) {
        // Получаем инициалы пользователя для аватара
        const initials = user.name
            .split(' ')
            .map(part => part.charAt(0))
            .join('')
            .toUpperCase()
            .substring(0, 2);

        const userInfo = document.createElement('div');
        userInfo.className = 'user-info';
        userInfo.innerHTML = `
            <div class="user-info-header" id="userInfoHeader">
                <div class="user-info-avatar">${initials}</div>
                <div class="user-info-details">
                    <div class="user-info-name">${user.name}</div>
                    <div class="user-info-email">${user.email || ''}</div>
                </div>
                <button id="userMenuToggle" class="user-menu-toggle">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M18 15l-6-6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                </button>
            </div>
            <div id="userMenu" class="user-menu">
                <button id="clearChatMenuButton" class="user-menu-item clear-menu-item">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M3 6h18M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2m3 0v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6h14z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    Очистить
                </button>
                <button id="logoutButton" class="user-menu-item logout-menu-item">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                        <polyline points="16 17 21 12 16 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                        <line x1="21" y1="12" x2="9" y2="12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    Выйти
                </button>
            </div>
        `;
        sidebarFooter.appendChild(userInfo);

        // Добавляем обработчик на кнопку переключения меню
        document.getElementById('userMenuToggle').addEventListener('click', (e) => {
            e.stopPropagation();
            const menu = document.getElementById('userMenu');
            menu.classList.toggle('active');
        });

        // Добавляем обработчик на кнопку "Очистить"
        document.getElementById('clearChatMenuButton').addEventListener('click', () => {
            const menu = document.getElementById('userMenu');
            menu.classList.remove('active');
            // Вызываем функцию handleClearChat из app.js
            if (typeof handleClearChat === 'function') {
                handleClearChat();
            }
        });

        // Добавляем обработчик на кнопку выхода
        document.getElementById('logoutButton').addEventListener('click', () => {
            if (confirm('Вы уверены, что хотите выйти?')) {
                window.authManager.logout();
            }
        });

        // Закрываем меню при клике вне его
        document.addEventListener('click', (e) => {
            const menu = document.getElementById('userMenu');
            const toggle = document.getElementById('userMenuToggle');
            if (menu && !menu.contains(e.target) && !toggle.contains(e.target)) {
                menu.classList.remove('active');
            }
        });
    }
}
