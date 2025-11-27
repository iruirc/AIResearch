/**
 * @fileoverview RAG Test Validator
 * Handles JSON validation for test content
 * @module ui/rag/tests/TestValidator
 */

/**
 * Test Validator class for validating test JSON content
 */
export class TestValidator {
    /**
     * Create a TestValidator
     */
    constructor() {
        // Required fields for each query element
        this.requiredFields = ['id', 'query', 'explanation'];
    }

    /**
     * Validate test content as JSON with required fields
     * @param {string} content - JSON content to validate
     * @returns {{valid: boolean, error?: string, lineNumber?: number}} Validation result
     */
    validateTestJSON(content) {
        // First, try to parse as JSON
        let parsed;
        try {
            parsed = JSON.parse(content);
        } catch (e) {
            // Extract line number from JSON parse error if available
            const lineMatch = e.message.match(/position\s+(\d+)/i) ||
                              e.message.match(/line\s+(\d+)/i) ||
                              e.message.match(/at\s+(\d+)/i);

            let lineNumber = null;
            if (lineMatch) {
                // Try to calculate line number from position
                const position = parseInt(lineMatch[1], 10);
                const lines = content.substring(0, position).split('\n');
                lineNumber = lines.length;
            }

            return {
                valid: false,
                error: `Некорректный JSON: ${e.message}`,
                lineNumber: lineNumber
            };
        }

        // Find queries array (could be top-level array or nested under "queries" key)
        let queries = Array.isArray(parsed) ? parsed : parsed.queries;

        if (!queries) {
            return {
                valid: false,
                error: 'JSON должен содержать массив запросов (либо как массив, либо в поле "queries")'
            };
        }

        if (!Array.isArray(queries)) {
            return {
                valid: false,
                error: 'Поле "queries" должно быть массивом'
            };
        }

        if (queries.length === 0) {
            return {
                valid: false,
                error: 'Массив запросов не может быть пустым'
            };
        }

        // Validate each query element
        for (let i = 0; i < queries.length; i++) {
            const query = queries[i];
            const missingFields = [];

            for (const field of this.requiredFields) {
                if (!(field in query) || query[field] === null || query[field] === undefined) {
                    missingFields.push(field);
                } else if (typeof query[field] === 'string' && query[field].trim() === '') {
                    missingFields.push(`${field} (пустое значение)`);
                }
            }

            if (missingFields.length > 0) {
                // Try to find line number for this query in the JSON string
                let lineNumber = null;
                try {
                    // Search for this query's id in the original content
                    const queryId = query.id || `элемент ${i + 1}`;
                    const idPattern = new RegExp(`"id"\\s*:\\s*"${queryId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}"`, 'g');
                    const match = idPattern.exec(content);
                    if (match) {
                        const lines = content.substring(0, match.index).split('\n');
                        lineNumber = lines.length;
                    }
                } catch (e) {
                    // Ignore regex errors
                }

                return {
                    valid: false,
                    error: `Элемент #${i + 1}${query.id ? ` (id: "${query.id}")` : ''}: отсутствуют обязательные поля: ${missingFields.join(', ')}`,
                    lineNumber: lineNumber
                };
            }
        }

        return { valid: true };
    }

    /**
     * Show validation error in the UI
     * @param {string} error - Error message
     * @param {number|null} lineNumber - Line number where error occurred
     */
    showValidationError(error, lineNumber = null) {
        const errorDiv = document.getElementById('ragTestValidationError');
        if (!errorDiv) return;

        let html = `<div class="validation-error-title">Ошибка валидации JSON</div>`;
        if (lineNumber) {
            html += `<div class="validation-error-line">Строка: ${lineNumber}</div>`;
        }
        html += `<div>${error}</div>`;
        html += `<div class="validation-error-details">Обязательные поля для каждого запроса: id, query, explanation</div>`;

        errorDiv.innerHTML = html;
        errorDiv.classList.remove('hidden');
    }

    /**
     * Hide validation error
     */
    hideValidationError() {
        const errorDiv = document.getElementById('ragTestValidationError');
        if (errorDiv) {
            errorDiv.classList.add('hidden');
            errorDiv.innerHTML = '';
        }
    }
}
