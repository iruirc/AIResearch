/**
 * @fileoverview Utility helper functions
 * Provides common utilities for fetch operations, time formatting, provider detection,
 * DOM manipulation, and function debouncing
 * @module utils/helpers
 */

/**
 * Executes a fetch request with a timeout
 * @param {string} url - The URL to fetch from
 * @param {RequestInit} options - Fetch options (method, headers, body, etc.)
 * @param {number} timeout - Timeout duration in milliseconds
 * @returns {Promise<Response>} The fetch response
 * @throws {Error} Throws 'AbortError' if the request times out
 * @example
 * try {
 *   const response = await fetchWithTimeout('/api/data', { method: 'GET' }, 5000);
 *   const data = await response.json();
 * } catch (error) {
 *   if (error.message === 'AbortError') {
 *     console.error('Request timed out');
 *   }
 * }
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
 * Formats a timestamp into a human-readable "time ago" string (in Russian)
 * @param {number} timestamp - Unix timestamp in milliseconds
 * @returns {string} Formatted time ago string (e.g., "5 мин. назад", "2 ч. назад")
 * @example
 * const timestamp = Date.now() - 3600000; // 1 hour ago
 * console.log(getTimeAgo(timestamp)); // "1 ч. назад"
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
 * Detects the AI provider based on model ID naming patterns
 * @param {string} modelId - The model identifier
 * @returns {string} Provider identifier ('claude', 'openai', or 'huggingface')
 * @example
 * detectProviderFromModel('claude-3-opus-20240229'); // returns 'claude'
 * detectProviderFromModel('gpt-4-turbo'); // returns 'openai'
 * detectProviderFromModel('deepseek-ai/DeepSeek-R1'); // returns 'huggingface'
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
 * Scrolls an element to its bottom
 * @param {HTMLElement} element - The DOM element to scroll
 * @example
 * const chatContainer = document.getElementById('chat');
 * scrollToBottom(chatContainer);
 */
export function scrollToBottom(element) {
    element.scrollTop = element.scrollHeight;
}

/**
 * Creates a debounced version of a function that delays execution
 * until after wait milliseconds have elapsed since the last call
 * @param {Function} func - The function to debounce
 * @param {number} wait - The number of milliseconds to delay
 * @returns {Function} The debounced function
 * @example
 * const debouncedSearch = debounce((query) => {
 *   console.log('Searching for:', query);
 * }, 300);
 *
 * // Will only execute once after user stops typing for 300ms
 * input.addEventListener('input', (e) => debouncedSearch(e.target.value));
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

/**
 * Generates a URL-friendly slug from a text string
 * Converts text to lowercase, transliterates Cyrillic to Latin, and replaces spaces/special chars with hyphens
 * @param {string} text - The text to convert to a slug
 * @returns {string} URL-friendly slug
 * @example
 * generateSlug('AI Репетитор'); // returns 'ai-repetitor'
 * generateSlug('Code Reviewer!!!'); // returns 'code-reviewer'
 */
export function generateSlug(text) {
    // Transliteration map for Cyrillic characters
    const translitMap = {
        'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'д': 'd', 'е': 'e', 'ё': 'yo',
        'ж': 'zh', 'з': 'z', 'и': 'i', 'й': 'y', 'к': 'k', 'л': 'l', 'м': 'm',
        'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u',
        'ф': 'f', 'х': 'h', 'ц': 'ts', 'ч': 'ch', 'ш': 'sh', 'щ': 'sch',
        'ъ': '', 'ы': 'y', 'ь': '', 'э': 'e', 'ю': 'yu', 'я': 'ya'
    };

    return text
        .toLowerCase()
        .split('')
        .map(char => translitMap[char] || char)
        .join('')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
        .replace(/-+/g, '-');
}
