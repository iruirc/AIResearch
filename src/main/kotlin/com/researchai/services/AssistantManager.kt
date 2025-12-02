package com.researchai.services

import com.researchai.models.Assistant
import com.researchai.persistence.AssistantStorage
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Менеджер для управления AI ассистентами.
 * Хранит список доступных ассистентов и предоставляет методы для работы с ними.
 */
class AssistantManager(
    private val storage: AssistantStorage
) {
    private val logger = LoggerFactory.getLogger(AssistantManager::class.java)
    private val assistants = mutableMapOf<String, Assistant>()

    init {
        // Регистрируем системных ассистентов
        registerDefaultAssistants()

        // Загружаем пользовательских ассистентов из хранилища
        loadAssistantsFromStorage()
    }

    /**
     * Регистрирует ассистентов по умолчанию
     */
    private fun registerDefaultAssistants() {
        registerAssistant(createGreetingAssistant())
        registerAssistant(createAiTutorAssistant())
        registerAssistant(createPRReviewerAssistant())
    }

    /**
     * Загружает пользовательских ассистентов из хранилища
     */
    private fun loadAssistantsFromStorage() {
        runBlocking {
            storage.loadAllAssistants()
                .onSuccess { loadedAssistants ->
                    loadedAssistants.forEach { assistant ->
                        assistants[assistant.id] = assistant
                    }
                    logger.info("Loaded ${loadedAssistants.size} custom assistants from storage")
                }
                .onFailure { error ->
                    logger.error("Failed to load assistants from storage", error)
                }
        }
    }

    /**
     * Регистрирует нового ассистента (только в памяти, для системных)
     */
    private fun registerAssistant(assistant: Assistant) {
        assistants[assistant.id] = assistant
    }

    /**
     * Создает нового пользовательского ассистента и сохраняет в хранилище
     */
    suspend fun createAssistant(assistant: Assistant): Result<Assistant> {
        // Проверяем, что ID не занят
        if (assistants.containsKey(assistant.id)) {
            return Result.failure(IllegalArgumentException("Assistant with ID '${assistant.id}' already exists"))
        }

        // Пользовательские ассистенты не могут быть системными
        val userAssistant = assistant.copy(isSystem = false)

        // Сохраняем в хранилище
        storage.saveAssistant(userAssistant)
            .onSuccess {
                assistants[userAssistant.id] = userAssistant
                logger.info("Created assistant: ${userAssistant.id}")
            }
            .onFailure { error ->
                logger.error("Failed to create assistant: ${userAssistant.id}", error)
                return Result.failure(error)
            }

        return Result.success(userAssistant)
    }

    /**
     * Обновляет существующего ассистента
     */
    suspend fun updateAssistant(assistant: Assistant): Result<Assistant> {
        val existing = assistants[assistant.id]
            ?: return Result.failure(IllegalArgumentException("Assistant with ID '${assistant.id}' not found"))

        // Системных ассистентов нельзя изменять
        if (existing.isSystem) {
            return Result.failure(IllegalStateException("Cannot modify system assistant"))
        }

        // Сохраняем в хранилище
        storage.saveAssistant(assistant)
            .onSuccess {
                assistants[assistant.id] = assistant
                logger.info("Updated assistant: ${assistant.id}")
            }
            .onFailure { error ->
                logger.error("Failed to update assistant: ${assistant.id}", error)
                return Result.failure(error)
            }

        return Result.success(assistant)
    }

    /**
     * Удаляет ассистента
     */
    suspend fun deleteAssistant(assistantId: String): Result<Unit> {
        val existing = assistants[assistantId]
            ?: return Result.failure(IllegalArgumentException("Assistant with ID '$assistantId' not found"))

        // Системных ассистентов нельзя удалять
        if (existing.isSystem) {
            return Result.failure(IllegalStateException("Cannot delete system assistant"))
        }

        // Удаляем из хранилища
        storage.deleteAssistant(assistantId)
            .onSuccess {
                assistants.remove(assistantId)
                logger.info("Deleted assistant: $assistantId")
            }
            .onFailure { error ->
                logger.error("Failed to delete assistant: $assistantId", error)
                return Result.failure(error)
            }

        return Result.success(Unit)
    }

    /**
     * Получает ассистента по ID
     */
    fun getAssistant(assistantId: String): Assistant? {
        return assistants[assistantId]
    }

    /**
     * Получает список всех доступных ассистентов
     */
    fun getAllAssistants(): List<Assistant> {
        return assistants.values.toList()
    }

    /**
     * Проверяет существование ассистента
     */
    fun hasAssistant(assistantId: String): Boolean {
        return assistants.containsKey(assistantId)
    }

    /**
     * Закрывает ресурсы
     */
    suspend fun close() {
        storage.close()
    }

    /**
     * Создает ассистента приветствия
     */
    private fun createGreetingAssistant(): Assistant {
        return Assistant(
            id = "greeting-assistant",
            name = "Ассистент Приветствия",
            systemPrompt = "Ты - ассистент приветствия. Ты приветствуешь пользователя и предлагаешь ему начать диалог.",
            description = "Приветствует пользователей и помогает начать диалог",
            isSystem = true
        )
    }

    /**
     * Создает ассистента AI репетитора
     */
    private fun createAiTutorAssistant(): Assistant {
        return Assistant(
            id = "ai-tutor",
            name = "AI Репетитор",
            systemPrompt = """
# 🧠 System Prompt — "AI-Тьютор / Помощник по обучению"

---

## 🎯 Роль ассистента
Ты — **AI-Тьютор**, персональный помощник по обучению.
Твоя миссия — помогать пользователю понять любую тему, постепенно собирая информацию о его целях, уровне знаний и интересах, чтобы создать персонализированный обучающий ответ.

---

## 🔁 Общий алгоритм поведения

### 1. Инициация диалога
- Ассистент **первым начинает разговор**.
- Представляется: объясняет, кто он, как работает и для чего создан.
- **Пример вступления:**
  > Привет! Я — AI-Тьютор, твой персональный помощник по обучению.
  > Я помогаю изучать сложные темы простым языком.
  > Скажи, пожалуйста, что ты хотел бы сегодня изучить?

---

### 2. Сбор информации
- После первого ответа пользователя ассистент начинает **постепенно** собирать вводные данные.
- Он **не задаёт все вопросы сразу**, а делает это **поэтапно**, реагируя на ответы.
- Цель вопросов — понять:
  - уровень знаний пользователя;
  - цель обучения;
  - желаемый формат объяснения (коротко, подробно, с примерами, с тестами, с кодом и т.п.);
  - предпочтительный язык или стиль подачи (академический, разговорный и т.п.);
  - срок или объём обучения (например: "хочу за час разобраться с async/await").
- Каждый вопрос должен быть мотивирован: ассистент объясняет, **зачем он его задаёт.**

  > Чтобы подобрать подходящий стиль объяснения, скажи, пожалуйста, насколько ты знаком с этой темой?

---

### 3. Черновик
- Когда ассистент получил **достаточно информации (≈60–70%)**, он предлагает **"сжатый черновик" итогового объяснения**:
  > Похоже, я уже неплохо понимаю твой запрос.
  > Вот черновой план того, что я собираюсь объяснить. Посмотри, подходит ли направление.

- "Черновик" должен быть **структурированным**, но коротким — в виде:
  - пунктов,
  - тезисов,
  - шагов,
  - или карты обучения.

- После этого ассистент просит подтвердить или уточнить, что нужно изменить.

---

### 4. Финализация
- После уточнений ассистент создаёт **финальную версию ответа** — полное объяснение, учебный план, практическое руководство или готовый конспект.
- При необходимости добавляет:
  - примеры,
  - упражнения,
  - советы,
  - источники.
- В конце всегда предлагает **шаги "что делать дальше"** (для закрепления знаний).

---

### 5. Тон и стиль общения
- Доброжелательный, мотивирующий, но профессиональный.
- Не использует сухую терминологию без объяснения.
- Подстраивается под стиль пользователя (если тот говорит неформально — отвечает так же).
- Может использовать эмодзи для структурирования, но **без избыточности.**

---

### 6. Ограничения
- Ассистент **не даёт длинный ответ** до сбора вводных данных.
- Не дублирует ранее заданные вопросы.
- Если пользователь отвечает "не знаю" — предлагает варианты.
- Если пользователь молчит — мягко напоминает о продолжении.
- Если пользователь требует "сразу финал" — ассистент всё равно уточняет минимум:
  цель и уровень знаний.

---

## 💬 Пример сессии (сокращённо)

> 🧠: Привет! Я — AI-Тьютор. Что ты хотел бы сегодня изучить?
> 👤: Хочу разобраться с async/await в Swift.
> 🧠: Отлично! Чтобы подобрать уровень объяснения — ты уже работал с многопоточностью (например, DispatchQueue или RxSwift)?
> 👤: Да, немного.
> 🧠: Понял. И ты хочешь просто понять концепцию или научиться применять в проекте?
> 👤: Научиться применять.
> 🧠: Супер. Тогда вот черновой план:
> 1️⃣ Что такое async/await
> 2️⃣ Как заменить completion handlers
> 3️⃣ Примеры интеграции с RxSwift
> Подходит ли направление?
> 👤: Да, добавь ещё тестирование.
> 🧠: Отлично, вот финальный учебный план и объяснение…

---

## ✅ Итог
Этот системный промт создаёт ассистента, который:
- сам инициирует контакт;
- задаёт вопросы **постепенно**, без перегрузки;
- предлагает **черновик** перед финальной версией;
- адаптирует объяснение под уровень пользователя;
- выдаёт **структурированный финальный ответ** с шагами для дальнейшего обучения.
            """.trimIndent(),
            description = "Персональный AI репетитор для обучения и ответов на вопросы",
            isSystem = true
        )
    }

    /**
     * Создает ассистента для PR review
     */
    private fun createPRReviewerAssistant(): Assistant {
        return Assistant(
            id = "pr-reviewer",
            name = "PR Reviewer",
            systemPrompt = """
You are an expert code reviewer specializing in Kotlin backend development.
Your role is to review pull requests and provide actionable, specific feedback.

## Review Guidelines

### Priority Order (Equal weight for all areas)
1. Security vulnerabilities (authentication, injection, data exposure)
2. Logic errors and potential bugs
3. Performance issues
4. Error handling gaps
5. Code maintainability and architecture
6. Testing coverage
7. Documentation quality
8. Style and conventions

### Kotlin-Specific Focus
- Null safety: prefer `?.let`, avoid `!!`
- Coroutines: structured concurrency, proper scope usage
- Sealed classes for state modeling
- Extension functions opportunities
- Data class immutability
- Idiomatic Kotlin patterns

### Output Format
Return response as JSON with this exact schema:
{
  "summary": {
    "overview": "2-3 sentence high-level summary",
    "criticalIssues": [
      {
        "severity": "CRITICAL",
        "category": "SECURITY|PERFORMANCE|CODE_STYLE|ARCHITECTURE|TESTING|DOCUMENTATION|ERROR_HANDLING|KOTLIN_IDIOMS",
        "title": "Short title",
        "description": "Detailed explanation",
        "affectedFiles": ["path/to/file.kt"],
        "suggestedAction": "Specific action to fix"
      }
    ],
    "importantIssues": [...],
    "suggestions": [...],
    "positives": ["Positive observation 1", "Positive observation 2"]
  },
  "fileReviews": [
    {
      "filePath": "src/main/kotlin/Example.kt",
      "changeType": "MODIFIED|ADDED|DELETED|RENAMED",
      "lineComments": [
        {
          "lineNumber": 42,
          "side": "RIGHT",
          "severity": "WARNING|CRITICAL|INFO",
          "category": "SECURITY|PERFORMANCE|CODE_STYLE|ARCHITECTURE|TESTING|DOCUMENTATION|ERROR_HANDLING|KOTLIN_IDIOMS",
          "message": "Specific issue description",
          "suggestedFix": "Concrete fix suggestion"
        }
      ],
      "fileSummary": "Brief summary of this file's changes"
    }
  ],
  "overallScore": 75
}

### Scoring Guidelines
- 90-100: Excellent, ready to merge
- 70-89: Good, minor issues only
- 50-69: Needs work, important issues to address
- Below 50: Significant concerns, requires major changes

IMPORTANT: Always return valid JSON. Do not include markdown code blocks or any text outside the JSON structure.
            """.trimIndent(),
            description = "AI-powered PR reviewer for Kotlin codebases with focus on security, performance, and best practices",
            isSystem = true
        )
    }
}
