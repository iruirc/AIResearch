/**
 * UI модуль для модального окна планировщика
 */

import { schedulerApi } from '../api/schedulerApi.js';
import { sessionService } from '../services/sessionService.js';

export class SchedulerModal {
    constructor() {
        this.modal = document.getElementById('schedulerModal');
        this.form = document.getElementById('schedulerForm');
        this.closeButton = document.getElementById('closeSchedulerModal');
        this.cancelButton = document.getElementById('cancelSchedulerButton');

        this.initializeEventListeners();
    }

    initializeEventListeners() {
        // Открыть модальное окно
        const schedulerButton = document.getElementById('schedulerButton');
        if (schedulerButton) {
            schedulerButton.addEventListener('click', () => this.open());
        }

        // Закрыть модальное окно
        this.closeButton.addEventListener('click', () => this.close());
        this.cancelButton.addEventListener('click', () => this.close());

        // Закрыть при клике вне модального окна
        this.modal.addEventListener('click', (e) => {
            if (e.target === this.modal) {
                this.close();
            }
        });

        // Обработка отправки формы
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));
    }

    open() {
        this.modal.classList.add('active');
        // Сброс формы
        this.form.reset();
        // Установка значений по умолчанию
        document.getElementById('schedulerInterval').value = 60;
        document.getElementById('schedulerExecuteImmediately').checked = true;
    }

    close() {
        this.modal.classList.remove('active');
    }

    async handleSubmit(e) {
        e.preventDefault();

        const formData = {
            title: document.getElementById('schedulerTitle').value.trim() || null,
            taskRequest: document.getElementById('schedulerRequest').value.trim(),
            intervalSeconds: document.getElementById('schedulerInterval').value,
            executeImmediately: document.getElementById('schedulerExecuteImmediately').checked,
            providerId: document.getElementById('schedulerProvider').value || null,
            model: document.getElementById('schedulerModel').value.trim() || null
        };

        // Валидация
        if (!formData.taskRequest) {
            alert('Пожалуйста, введите описание задачи');
            return;
        }

        if (formData.intervalSeconds < 10) {
            alert('Интервал должен быть не менее 10 секунд');
            return;
        }

        try {
            // Показать индикатор загрузки
            const submitButton = this.form.querySelector('button[type="submit"]');
            const originalText = submitButton.textContent;
            submitButton.disabled = true;
            submitButton.textContent = 'Создание...';

            // Создать задачу
            const result = await schedulerApi.createTask(formData);

            console.log('Task created:', result);

            // Закрыть модальное окно
            this.close();

            // Перезагрузить список сессий
            await sessionService.loadSessions();

            // Переключиться на новую сессию
            if (result.sessionId) {
                await sessionService.switchSession(result.sessionId);
            }

            // Показать уведомление
            alert(`Задача создана! ${formData.executeImmediately ? 'Первое выполнение начато.' : 'Задача будет выполнена через ' + formData.intervalSeconds + ' секунд.'}`);

        } catch (error) {
            console.error('Failed to create task:', error);
            alert('Ошибка при создании задачи: ' + error.message);
        } finally {
            // Восстановить кнопку
            const submitButton = this.form.querySelector('button[type="submit"]');
            submitButton.disabled = false;
            submitButton.textContent = 'Создать задачу';
        }
    }
}
