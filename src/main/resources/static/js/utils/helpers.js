// Utility helper functions

/**
 * Fetch with timeout
 */
export function fetchWithTimeout(url, options, timeout) {
    return Promise.race([
        fetch(url, options),
        new Promise((_, reject) =>
            setTimeout(() => reject(new Error('AbortError')), timeout)
        )
    ]);
}

/**
 * Format time ago string
 */
export function getTimeAgo(timestamp) {
    const now = Date.now();
    const diff = now - timestamp;
    const seconds = Math.floor(diff / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days} дн. назад`;
    if (hours > 0) return `${hours} ч. назад`;
    if (minutes > 0) return `${minutes} мин. назад`;
    return 'только что';
}

/**
 * Detect provider from model ID
 */
export function detectProviderFromModel(modelId) {
    if (modelId.startsWith('claude-')) {
        return 'claude';
    } else if (modelId.startsWith('gpt-')) {
        return 'openai';
    } else if (modelId.includes('/') || modelId.toLowerCase().includes('deepseek')) {
        return 'huggingface';
    }
    return 'claude';
}

/**
 * Scroll to bottom of element
 */
export function scrollToBottom(element) {
    element.scrollTop = element.scrollHeight;
}

/**
 * Debounce function
 */
export function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}
