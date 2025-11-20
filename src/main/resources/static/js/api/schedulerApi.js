/**
 * API для работы с планировщиком задач
 */

export const schedulerApi = {
    /**
     * Создать новую задачу
     */
    async createTask(taskData) {
        const response = await fetch('/scheduler/tasks', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                title: taskData.title || null,
                taskRequest: taskData.taskRequest,
                intervalSeconds: parseInt(taskData.intervalSeconds),
                executeImmediately: taskData.executeImmediately,
                providerId: taskData.providerId || null,
                model: taskData.model || null
            })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create task');
        }

        return await response.json();
    },

    /**
     * Получить список всех задач
     */
    async getAllTasks() {
        const response = await fetch('/scheduler/tasks');

        if (!response.ok) {
            throw new Error('Failed to fetch tasks');
        }

        return await response.json();
    },

    /**
     * Получить детали задачи
     */
    async getTask(taskId) {
        const response = await fetch(`/scheduler/tasks/${taskId}`);

        if (!response.ok) {
            throw new Error('Failed to fetch task');
        }

        return await response.json();
    },

    /**
     * Остановить задачу
     */
    async stopTask(taskId) {
        const response = await fetch(`/scheduler/tasks/${taskId}/stop`, {
            method: 'POST'
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to stop task');
        }

        return await response.json();
    },

    /**
     * Запустить задачу
     */
    async startTask(taskId) {
        const response = await fetch(`/scheduler/tasks/${taskId}/start`, {
            method: 'POST'
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to start task');
        }

        return await response.json();
    },

    /**
     * Удалить задачу
     */
    async deleteTask(taskId) {
        const response = await fetch(`/scheduler/tasks/${taskId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to delete task');
        }

        return await response.json();
    }
};
