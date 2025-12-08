package com.researchai.services

import com.researchai.data.mcp.MCPServerManager
import com.researchai.domain.models.SearchResult
import com.researchai.domain.models.techsupport.TechSupportConfig
import com.researchai.domain.models.workflow.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Service for handling task workflow operations:
 * - Start task: move Trello card to InProgress + create Git branch + show RAG context
 * - Sync task: synchronize feature branch with main
 * - Complete task: move Trello card to Review + create PR + auto code review
 * - Approve task: merge PR + delete branch + move card to Done
 * - Cancel task: move card back to ToDo
 */
class TaskWorkflowService(
    private val mcpServerManager: MCPServerManager,
    private val config: TechSupportConfig,
    private val prReviewService: PRReviewService? = null,
    private val ragManager: RAGManager? = null
) {
    private val logger = LoggerFactory.getLogger(TaskWorkflowService::class.java)

    // Порядок списков в Trello (слева направо)
    private val listOrder = listOf("Inbox", "Backlog", "ToDo", "InProgress", "Review", "Done")

    // Кэш списков Trello
    private var listsCache: List<TrelloListInfo> = emptyList()
    private var listsCacheTime: Long = 0
    private val cacheValidityMs = 5 * 60 * 1000L  // 5 минут

    companion object {
        // Ключевые слова для определения действия START
        private val START_KEYWORDS = listOf(
            "выполняю", "начинаю", "приступаю", "беру в работу", "беру",
            "взял", "взяла", "начал", "начала", "приступил", "приступила",
            "start", "starting", "taking", "begin", "working on", "took"
        )

        // Ключевые слова для определения действия SYNC
        private val SYNC_KEYWORDS = listOf(
            "синхронизируй", "синхронизация", "синхронизировать",
            "обнови ветку", "обновить ветку", "обнови бранч",
            "подтяни main", "подтянуть main", "подтяни мейн",
            "sync", "synchronize", "update branch", "pull main", "merge main", "rebase"
        )

        // Ключевые слова для определения действия COMPLETE
        private val COMPLETE_KEYWORDS = listOf(
            "завершил", "завершила", "закончил", "закончила", "готов", "готова",
            "сделал", "сделала", "выполнил", "выполнила", "готова", "сделана", "сделано",
            "finished", "completed", "done", "ready", "complete"
        )

        // Ключевые слова для определения действия APPROVE
        private val APPROVE_KEYWORDS = listOf(
            "одобрено", "апрув", "мержи", "сливай", "слить", "принято", "принять",
            "смержи", "смержить", "замержи", "замержить",
            "approved", "approve", "merge", "accept", "lgtm"
        )

        // Ключевые слова для определения действия CANCEL
        private val CANCEL_KEYWORDS = listOf(
            "отменяю", "отказываюсь", "бросаю", "отмена", "отменил", "отменила",
            "cancel", "abort", "drop", "cancelled"
        )
    }

    /**
     * Основной метод обработки workflow запроса
     */
    suspend fun processWorkflow(request: TaskWorkflowRequest): TaskWorkflowResult {
        logger.info("Processing workflow request: ${request.query.take(50)}...")

        // 1. Извлечь Task ID из запроса
        val taskId = request.taskId ?: extractTaskId(request.query)
        if (taskId == null) {
            logger.warn("Could not extract task ID from query: ${request.query}")
            return errorResult("Не удалось определить ID задачи из запроса. Укажите задачу в формате Task_N, #N или 'задача N'")
        }

        // 2. Определить действие
        val action = request.action ?: detectAction(request.query)
        if (action == null) {
            logger.warn("Could not detect action from query: ${request.query}")
            return errorResult("Не удалось определить действие. Используйте слова: 'начинаю/выполняю' для старта или 'завершил/готово' для завершения", taskId = taskId)
        }

        logger.info("Detected task: $taskId, action: $action")

        // 3. Выполнить соответствующий workflow
        return when (action) {
            TaskAction.START -> startTask(taskId, request)
            TaskAction.SYNC -> syncTask(taskId, request)
            TaskAction.COMPLETE -> completeTask(taskId, request)
            TaskAction.APPROVE -> approveTask(taskId, request)
            TaskAction.CANCEL -> cancelTask(taskId, request)
        }
    }

    /**
     * Начать работу над задачей:
     * 1. Найти карточку в Trello
     * 2. Проверить, что карточка левее InProgress
     * 3. Получить RAG контекст (связанные файлы/задачи)
     * 4. Создать ветку в GitHub
     * 5. Переместить карточку в InProgress
     * 6. Добавить комментарий
     */
    suspend fun startTask(taskId: String, request: TaskWorkflowRequest): TaskWorkflowResult {
        logger.info("Starting task: $taskId")

        // 1. Найти карточку в Trello
        val card = findCardByTaskId(taskId, request.trelloBoardId)
        if (card == null) {
            logger.warn("Task $taskId not found in Trello")
            return errorResult("Задача $taskId не найдена в Trello", taskId = taskId, action = TaskAction.START)
        }

        logger.info("Found card: ${card.cardName} in list: ${card.listName}")

        // 2. Проверить позицию карточки
        val cardPosition = getListPosition(card.listName)
        val inProgressPosition = getListPosition("InProgress")

        if (cardPosition >= inProgressPosition) {
            return errorResult(
                "Задача $taskId уже в статусе '${card.listName}' или правее InProgress. Нельзя взять в работу.",
                taskId = taskId,
                action = TaskAction.START
            )
        }

        // 3. Получить RAG контекст (если доступен RAGManager)
        var contextInfo: TaskContextInfo? = null

        if (ragManager != null) {
            logger.info("Fetching RAG context for task: $taskId")
            contextInfo = fetchTaskContext(taskId, card.cardName)
        }

        // 4. Создать ветку в GitHub
        val branchName = "feature/$taskId"
        val githubResult = createBranchSafe(branchName, request)

        if (!githubResult.success && !githubResult.branchAlreadyExists) {
            return errorResult(
                "Ошибка создания ветки $branchName: ${githubResult.error}",
                taskId = taskId,
                action = TaskAction.START,
                githubResult = githubResult
            )
        }

        // 5. Переместить карточку в InProgress
        val trelloResult = moveCardToList(card, "InProgress", request.trelloBoardId)

        if (!trelloResult.success) {
            // Ветка создана, но Trello не обновился
            logger.error("Failed to move card to InProgress: ${trelloResult.error}")
            return TaskWorkflowResult(
                success = false,
                taskId = taskId,
                action = TaskAction.START,
                trelloResult = trelloResult,
                githubResult = githubResult,
                contextInfo = contextInfo,
                message = "Частичный успех: ветка ${if (githubResult.branchAlreadyExists) "уже существует" else "создана"}, но карточка не перемещена: ${trelloResult.error}",
                errors = listOf(trelloResult.error ?: "Ошибка Trello"),
                wasRolledBack = false
            )
        }

        // 6. Добавить комментарий в Trello
        val branchStatus = if (githubResult.branchAlreadyExists) "использована существующая ветка" else "создана ветка"
        val comment = "Задача взята в работу. ${branchStatus.replaceFirstChar { it.uppercase() }} $branchName"
        val commentAdded = addCommentToCard(card.cardId, comment, request.trelloBoardId)

        val message = buildString {
            append("Задача $taskId взята в работу. ")
            if (githubResult.branchAlreadyExists) {
                append("Ветка $branchName уже существует. ")
            } else {
                append("Ветка $branchName создана. ")
            }
            append("Карточка перемещена в InProgress.")

            // Добавить информацию о контексте из RAG
            contextInfo?.let { ctx ->
                if (ctx.relatedFiles.isNotEmpty()) {
                    append("\n\n📁 Связанные файлы:\n")
                    ctx.relatedFiles.take(5).forEach { file ->
                        append("  • $file\n")
                    }
                }
                if (ctx.relatedTasks.isNotEmpty()) {
                    append("\n🔗 Похожие задачи: ${ctx.relatedTasks.joinToString(", ")}")
                }
                ctx.suggestedApproach?.let { approach ->
                    append("\n\n💡 Рекомендуемый подход:\n$approach")
                }
            }
        }

        logger.info("Task $taskId started successfully")

        return TaskWorkflowResult(
            success = true,
            taskId = taskId,
            action = TaskAction.START,
            trelloResult = trelloResult.copy(commentAdded = commentAdded),
            githubResult = githubResult,
            contextInfo = contextInfo,
            message = message
        )
    }

    /**
     * Завершить работу над задачей:
     * 1. Найти карточку в Trello
     * 2. Проверить, что карточка левее Review
     * 3. Создать PR в GitHub
     * 4. Автоматический AI код-ревью
     * 5. Переместить карточку в Review
     * 6. Добавить комментарий с ссылкой на PR и результатом ревью
     */
    suspend fun completeTask(taskId: String, request: TaskWorkflowRequest): TaskWorkflowResult {
        logger.info("Completing task: $taskId")

        // 1. Найти карточку в Trello
        val card = findCardByTaskId(taskId, request.trelloBoardId)
        if (card == null) {
            logger.warn("Task $taskId not found in Trello")
            return errorResult("Задача $taskId не найдена в Trello", taskId = taskId, action = TaskAction.COMPLETE)
        }

        logger.info("Found card: ${card.cardName} in list: ${card.listName}")

        // 2. Проверить позицию карточки
        val cardPosition = getListPosition(card.listName)
        val reviewPosition = getListPosition("Review")

        if (cardPosition >= reviewPosition) {
            return errorResult(
                "Задача $taskId уже в статусе '${card.listName}' или правее Review. Нельзя завершить повторно.",
                taskId = taskId,
                action = TaskAction.COMPLETE
            )
        }

        // 3. Создать PR в GitHub
        val branchName = "feature/$taskId"
        val prTitle = "${card.cardName} ($taskId)"
        val githubResult = createPullRequestSafe(branchName, "main", prTitle, card.cardName, request)

        if (!githubResult.success) {
            return errorResult(
                "Ошибка создания PR: ${githubResult.error}",
                taskId = taskId,
                action = TaskAction.COMPLETE,
                githubResult = githubResult
            )
        }

        // 4. Автоматический AI код-ревью (если доступен PRReviewService)
        var reviewSummary: ReviewSummaryInfo? = null

        if (prReviewService != null && githubResult.prCreated && githubResult.prNumber != null) {
            logger.info("Starting automatic code review for PR #${githubResult.prNumber}")

            try {
                val owner = request.githubOwner ?: config.defaultGithubOwner ?: ""
                val repo = request.githubRepo ?: config.defaultGithubRepo ?: ""

                val reviewRequest = com.researchai.domain.models.pr.PRReviewRequest(
                    repositoryOwner = owner,
                    repositoryName = repo,
                    pullRequestNumber = githubResult.prNumber,
                    reviewMode = com.researchai.domain.models.pr.ReviewMode.STANDARD,
                    focusAreas = listOf(
                        com.researchai.domain.models.pr.FocusArea.SECURITY,
                        com.researchai.domain.models.pr.FocusArea.CODE_STYLE,
                        com.researchai.domain.models.pr.FocusArea.KOTLIN_IDIOMS
                    ),
                    useRAG = true
                )

                val reviewResult = prReviewService.reviewPullRequest(reviewRequest)

                reviewResult.onSuccess { result ->
                    reviewSummary = ReviewSummaryInfo(
                        overallScore = result.overallScore,
                        criticalIssuesCount = result.summary.criticalIssues.size,
                        importantIssuesCount = result.summary.importantIssues.size,
                        suggestionsCount = result.summary.suggestions.size,
                        reviewUrl = result.pullRequestUrl
                    )

                    // Постить ревью как комментарий к PR
                    prReviewService.postReviewAsComment(result)
                    logger.info("Code review completed: score=${result.overallScore}")
                }.onFailure { error ->
                    logger.warn("Code review failed, continuing without it: ${error.message}")
                }
            } catch (e: Exception) {
                logger.warn("Code review error, continuing without it: ${e.message}")
            }
        }

        // 5. Переместить карточку в Review
        val trelloResult = moveCardToList(card, "Review", request.trelloBoardId)

        if (!trelloResult.success) {
            // PR создан, но Trello не обновился
            logger.error("Failed to move card to Review: ${trelloResult.error}")
            return TaskWorkflowResult(
                success = false,
                taskId = taskId,
                action = TaskAction.COMPLETE,
                trelloResult = trelloResult,
                githubResult = githubResult,
                reviewResult = reviewSummary,
                message = "Частичный успех: PR #${githubResult.prNumber} создан, но карточка не перемещена: ${trelloResult.error}",
                errors = listOf(trelloResult.error ?: "Ошибка Trello"),
                wasRolledBack = false
            )
        }

        // 6. Добавить комментарий в Trello с ссылкой на PR и результатом ревью
        val prLink = githubResult.prUrl ?: "PR #${githubResult.prNumber}"
        val reviewInfo = reviewSummary?.let { review ->
            "\n\n🔍 AI Code Review: ${review.overallScore}/100" +
            (if (review.criticalIssuesCount > 0) " (⚠️ ${review.criticalIssuesCount} critical)" else "")
        } ?: ""
        val comment = "Задача завершена. Создан PR: $prLink$reviewInfo"
        val commentAdded = addCommentToCard(card.cardId, comment, request.trelloBoardId)

        val message = buildString {
            append("Задача $taskId завершена. PR #${githubResult.prNumber} создан")
            reviewSummary?.let { review ->
                append(", AI Review: ${review.overallScore}/100")
                if (review.criticalIssuesCount > 0) {
                    append(" (⚠️ ${review.criticalIssuesCount} critical)")
                }
            }
            append(", карточка перемещена в Review.")
        }

        logger.info("Task $taskId completed successfully")

        return TaskWorkflowResult(
            success = true,
            taskId = taskId,
            action = TaskAction.COMPLETE,
            trelloResult = trelloResult.copy(commentAdded = commentAdded),
            githubResult = githubResult,
            reviewResult = reviewSummary,
            message = message
        )
    }

    /**
     * Отменить работу над задачей (перемещает обратно в ToDo)
     */
    suspend fun cancelTask(taskId: String, request: TaskWorkflowRequest): TaskWorkflowResult {
        logger.info("Cancelling task: $taskId")

        val card = findCardByTaskId(taskId, request.trelloBoardId)
        if (card == null) {
            return errorResult("Задача $taskId не найдена в Trello", taskId = taskId, action = TaskAction.CANCEL)
        }

        // Переместить карточку обратно в ToDo
        val trelloResult = moveCardToList(card, "ToDo", request.trelloBoardId)

        if (!trelloResult.success) {
            return errorResult(
                "Ошибка отмены задачи: ${trelloResult.error}",
                taskId = taskId,
                action = TaskAction.CANCEL,
                trelloResult = trelloResult
            )
        }

        val comment = "Работа над задачей отменена. Карточка возвращена в ToDo."
        addCommentToCard(card.cardId, comment, request.trelloBoardId)

        return TaskWorkflowResult(
            success = true,
            taskId = taskId,
            action = TaskAction.CANCEL,
            trelloResult = trelloResult,
            githubResult = null,
            message = "Работа над задачей $taskId отменена. Карточка перемещена в ToDo."
        )
    }

    /**
     * Синхронизировать ветку задачи с main:
     * 1. Проверить наличие ветки
     * 2. Выполнить merge main -> feature branch
     * 3. Вернуть информацию о конфликтах
     */
    suspend fun syncTask(taskId: String, request: TaskWorkflowRequest): TaskWorkflowResult {
        logger.info("Syncing task: $taskId")

        val branchName = "feature/$taskId"

        // 1. Проверить существование ветки
        val branchExists = checkBranchExists(branchName, request)
        if (!branchExists) {
            return errorResult(
                "Ветка $branchName не найдена. Сначала выполните START для задачи.",
                taskId = taskId,
                action = TaskAction.SYNC
            )
        }

        // 2. Выполнить синхронизацию через GitHub MCP
        val syncResult = mergeBranches("main", branchName, request)

        val message = if (syncResult.hasConflicts) {
            "⚠️ Конфликты при синхронизации $branchName с main:\n" +
            syncResult.conflictingFiles.joinToString("\n") { "  - $it" } +
            "\nТребуется ручное разрешение конфликтов."
        } else if (syncResult.success) {
            "✅ Ветка $branchName синхронизирована с main."
        } else {
            "❌ Ошибка синхронизации: ветки уже синхронизированы или произошла ошибка."
        }

        return TaskWorkflowResult(
            success = syncResult.success && !syncResult.hasConflicts,
            taskId = taskId,
            action = TaskAction.SYNC,
            githubResult = GithubActionResult(
                success = syncResult.success && !syncResult.hasConflicts,
                branchName = branchName,
                syncResult = syncResult
            ),
            message = message
        )
    }

    /**
     * Принять задачу (после ревью):
     * 1. Найти PR для ветки
     * 2. Merge PR
     * 3. Удалить feature branch
     * 4. Переместить карточку в Done
     */
    suspend fun approveTask(taskId: String, request: TaskWorkflowRequest): TaskWorkflowResult {
        logger.info("Approving task: $taskId")

        // 1. Найти карточку в Trello
        val card = findCardByTaskId(taskId, request.trelloBoardId)
        if (card == null) {
            return errorResult("Задача $taskId не найдена в Trello", taskId = taskId, action = TaskAction.APPROVE)
        }

        // 2. Проверить позицию карточки (должна быть в Review или правее, но не в Done)
        val cardPosition = getListPosition(card.listName)
        val reviewPosition = getListPosition("Review")
        val donePosition = getListPosition("Done")

        if (cardPosition < reviewPosition) {
            return errorResult(
                "Задача $taskId ещё не в Review (текущий статус: ${card.listName}). Сначала выполните COMPLETE.",
                taskId = taskId,
                action = TaskAction.APPROVE
            )
        }

        if (cardPosition >= donePosition) {
            return errorResult(
                "Задача $taskId уже завершена (статус: ${card.listName}).",
                taskId = taskId,
                action = TaskAction.APPROVE
            )
        }

        // 3. Найти и смержить PR
        val branchName = "feature/$taskId"
        val prInfo = findPullRequestByBranch(branchName, request)

        if (prInfo == null || prInfo.number == null) {
            return errorResult(
                "PR для ветки $branchName не найден. Возможно, задача не была завершена через COMPLETE.",
                taskId = taskId,
                action = TaskAction.APPROVE
            )
        }

        val mergeResult = mergePullRequest(prInfo.number!!, request)

        if (!mergeResult.success) {
            return errorResult(
                "Ошибка при merge PR #${prInfo.number}: ${mergeResult.error}",
                taskId = taskId,
                action = TaskAction.APPROVE,
                githubResult = mergeResult
            )
        }

        // 4. Удалить feature branch
        val branchDeleted = deleteBranch(branchName, request)

        // 5. Переместить карточку в Done
        val trelloResult = moveCardToList(card, "Done", request.trelloBoardId)

        // 6. Добавить комментарий
        val comment = "✅ Задача завершена! PR #${prInfo.number} смержен" +
                (if (branchDeleted) ", ветка удалена." else ".")
        addCommentToCard(card.cardId, comment, request.trelloBoardId)

        return TaskWorkflowResult(
            success = true,
            taskId = taskId,
            action = TaskAction.APPROVE,
            trelloResult = trelloResult,
            githubResult = mergeResult.copy(branchDeleted = branchDeleted),
            message = "🎉 Задача $taskId завершена! PR #${prInfo.number} смержен, карточка перемещена в Done."
        )
    }

    // ==================== Вспомогательные методы ====================

    /**
     * Извлечение Task ID из текста запроса
     */
    fun extractTaskId(query: String): String? {
        val patterns = listOf(
            // Task_123, task-123, Task 123
            Regex("""[Tt]ask[_\-\s]?(\d+)"""),
            // #123
            Regex("""#(\d+)"""),
            // задача 123, задачу №123
            Regex("""задач[аеуи]\s*[#№]?\s*(\d+)""", RegexOption.IGNORE_CASE),
            // тикет 123
            Regex("""тикет\s*[#№]?\s*(\d+)""", RegexOption.IGNORE_CASE),
            // просто номер в контексте (123)
            Regex("""\b(\d{1,5})\b""")
        )

        for (pattern in patterns) {
            val match = pattern.find(query)
            if (match != null) {
                val number = match.groupValues[1]
                return "Task_$number"
            }
        }
        return null
    }

    /**
     * Определение действия из текста запроса
     */
    fun detectAction(query: String): TaskAction? {
        val lowerQuery = query.lowercase()

        return when {
            START_KEYWORDS.any { lowerQuery.contains(it) } -> TaskAction.START
            SYNC_KEYWORDS.any { lowerQuery.contains(it) } -> TaskAction.SYNC
            COMPLETE_KEYWORDS.any { lowerQuery.contains(it) } -> TaskAction.COMPLETE
            APPROVE_KEYWORDS.any { lowerQuery.contains(it) } -> TaskAction.APPROVE
            CANCEL_KEYWORDS.any { lowerQuery.contains(it) } -> TaskAction.CANCEL
            else -> null
        }
    }

    /**
     * Проверка, является ли запрос workflow запросом
     */
    fun isWorkflowQuery(query: String): Boolean {
        val lowerQuery = query.lowercase()

        // Должен содержать упоминание задачи
        val hasTaskMention = lowerQuery.contains("task") ||
                lowerQuery.contains("задач") ||
                lowerQuery.contains("тикет") ||
                lowerQuery.contains("#")

        // И одно из ключевых слов действия
        val hasActionKeyword = (START_KEYWORDS + SYNC_KEYWORDS + COMPLETE_KEYWORDS + APPROVE_KEYWORDS + CANCEL_KEYWORDS)
            .any { lowerQuery.contains(it) }

        return hasTaskMention && hasActionKeyword
    }

    /**
     * Получить позицию списка (0 = Inbox, 5 = Done)
     */
    private fun getListPosition(listName: String): Int {
        return listOrder.indexOfFirst { it.equals(listName, ignoreCase = true) }
            .takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    // ==================== Trello MCP операции ====================

    /**
     * Поиск карточки по Task ID
     */
    private suspend fun findCardByTaskId(taskId: String, boardId: String?): TrelloCardInfo? {
        val effectiveBoardId = boardId ?: config.defaultBoardId
        if (effectiveBoardId == null) {
            logger.error("Trello board ID not configured")
            return null
        }

        val trelloClient = mcpServerManager.getClient(config.trelloMcpServerId)
        if (trelloClient == null) {
            logger.error("Trello MCP client not available")
            return null
        }

        try {
            // Установить активную доску
            trelloClient.callTool("set_active_board", buildJsonObject {
                put("boardId", effectiveBoardId)
            })

            // Получить все списки
            val listsResult = trelloClient.callTool("get_lists", buildJsonObject {
                put("boardId", effectiveBoardId)
            })

            if (!listsResult.success) {
                logger.error("Failed to get lists: ${listsResult.error}")
                return null
            }

            val lists = parseListsResponse(listsResult.content.firstOrNull()?.text)

            // Для каждого списка получить карточки и найти нужную
            for (list in lists) {
                val cardsResult = trelloClient.callTool("get_cards_by_list_id", buildJsonObject {
                    put("listId", list.id)
                })

                if (!cardsResult.success) continue

                val cards = parseCardsResponse(cardsResult.content.firstOrNull()?.text, list.id, list.name)
                val card = cards.find { it.cardName.contains(taskId, ignoreCase = true) }
                if (card != null) {
                    return card
                }
            }

            return null
        } catch (e: Exception) {
            logger.error("Error finding card by task ID: ${e.message}", e)
            return null
        }
    }

    /**
     * Получить списки Trello (с кэшированием)
     */
    private suspend fun getTrelloLists(boardId: String): List<TrelloListInfo> {
        // Проверить кэш
        if (System.currentTimeMillis() - listsCacheTime < cacheValidityMs && listsCache.isNotEmpty()) {
            return listsCache
        }

        val trelloClient = mcpServerManager.getClient(config.trelloMcpServerId) ?: return emptyList()

        try {
            trelloClient.callTool("set_active_board", buildJsonObject {
                put("boardId", boardId)
            })

            val result = trelloClient.callTool("get_lists", buildJsonObject {
                put("boardId", boardId)
            })

            if (!result.success) return emptyList()

            val lists = parseListsResponse(result.content.firstOrNull()?.text)
            listsCache = lists.mapIndexed { index, list ->
                TrelloListInfo(
                    id = list.id,
                    name = list.name,
                    position = listOrder.indexOf(list.name).takeIf { it >= 0 } ?: index
                )
            }
            listsCacheTime = System.currentTimeMillis()

            return listsCache
        } catch (e: Exception) {
            logger.error("Error getting Trello lists: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Переместить карточку в указанный список
     */
    private suspend fun moveCardToList(
        card: TrelloCardInfo,
        targetListName: String,
        boardId: String?
    ): TrelloActionResult {
        val effectiveBoardId = boardId ?: config.defaultBoardId
        if (effectiveBoardId == null) {
            return TrelloActionResult(
                success = false,
                cardId = card.cardId,
                cardName = card.cardName,
                fromList = card.listName,
                toList = targetListName,
                error = "Board ID not configured"
            )
        }

        val lists = getTrelloLists(effectiveBoardId)
        val targetList = lists.find { it.name.equals(targetListName, ignoreCase = true) }
        if (targetList == null) {
            return TrelloActionResult(
                success = false,
                cardId = card.cardId,
                cardName = card.cardName,
                fromList = card.listName,
                toList = targetListName,
                error = "List '$targetListName' not found"
            )
        }

        val trelloClient = mcpServerManager.getClient(config.trelloMcpServerId)
        if (trelloClient == null) {
            return TrelloActionResult(
                success = false,
                cardId = card.cardId,
                cardName = card.cardName,
                fromList = card.listName,
                toList = targetListName,
                error = "Trello not connected"
            )
        }

        try {
            val result = trelloClient.callTool("move_card", buildJsonObject {
                put("cardId", card.cardId)
                put("listId", targetList.id)
            })

            return if (result.success) {
                TrelloActionResult(
                    success = true,
                    cardId = card.cardId,
                    cardName = card.cardName,
                    fromList = card.listName,
                    toList = targetListName,
                    cardUrl = card.url
                )
            } else {
                TrelloActionResult(
                    success = false,
                    cardId = card.cardId,
                    cardName = card.cardName,
                    fromList = card.listName,
                    toList = targetListName,
                    error = result.error ?: "Move failed"
                )
            }
        } catch (e: Exception) {
            logger.error("Error moving card: ${e.message}", e)
            return TrelloActionResult(
                success = false,
                cardId = card.cardId,
                cardName = card.cardName,
                fromList = card.listName,
                toList = targetListName,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Добавить комментарий к карточке
     */
    private suspend fun addCommentToCard(cardId: String, comment: String, boardId: String?): Boolean {
        val trelloClient = mcpServerManager.getClient(config.trelloMcpServerId) ?: return false

        return try {
            val result = trelloClient.callTool("add_comment", buildJsonObject {
                put("cardId", cardId)
                put("text", comment)
            })
            result.success
        } catch (e: Exception) {
            logger.warn("Failed to add comment to card $cardId: ${e.message}")
            false
        }
    }

    // ==================== GitHub MCP операции ====================

    /**
     * Создать ветку в GitHub (безопасно - с проверкой существования)
     */
    private suspend fun createBranchSafe(
        branchName: String,
        request: TaskWorkflowRequest
    ): GithubActionResult {
        val owner = request.githubOwner ?: config.defaultGithubOwner
        val repo = request.githubRepo ?: config.defaultGithubRepo

        if (owner == null || repo == null) {
            return GithubActionResult(
                success = false,
                branchName = branchName,
                error = "GitHub owner/repo not configured (set GH_DEFAULT_OWNER and GH_DEFAULT_REPO)"
            )
        }

        val githubClient = mcpServerManager.getClient(config.githubMcpServerId)
        if (githubClient == null) {
            return GithubActionResult(
                success = false,
                branchName = branchName,
                error = "GitHub MCP not connected"
            )
        }

        try {
            // Проверить, существует ли ветка
            val branchesResult = githubClient.callTool("list_branches", buildJsonObject {
                put("owner", owner)
                put("repo", repo)
            })

            if (branchesResult.success) {
                val branches = parseBranchesResponse(branchesResult.content.firstOrNull()?.text)
                if (branches.any { it.equals(branchName, ignoreCase = true) }) {
                    logger.info("Branch $branchName already exists")
                    return GithubActionResult(
                        success = true,
                        branchName = branchName,
                        branchCreated = false,
                        branchAlreadyExists = true
                    )
                }
            }

            // Создать ветку
            val result = githubClient.callTool("create_branch", buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("branch", branchName)
                put("from_branch", "main")
            })

            return if (result.success) {
                logger.info("Branch $branchName created successfully")
                GithubActionResult(
                    success = true,
                    branchName = branchName,
                    branchCreated = true,
                    branchAlreadyExists = false
                )
            } else {
                GithubActionResult(
                    success = false,
                    branchName = branchName,
                    error = result.error ?: "Branch creation failed"
                )
            }
        } catch (e: Exception) {
            logger.error("Error creating branch: ${e.message}", e)
            return GithubActionResult(
                success = false,
                branchName = branchName,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Создать Pull Request в GitHub
     */
    private suspend fun createPullRequestSafe(
        headBranch: String,
        baseBranch: String,
        title: String,
        cardDescription: String,
        request: TaskWorkflowRequest
    ): GithubActionResult {
        val owner = request.githubOwner ?: config.defaultGithubOwner
        val repo = request.githubRepo ?: config.defaultGithubRepo

        if (owner == null || repo == null) {
            return GithubActionResult(
                success = false,
                error = "GitHub owner/repo not configured"
            )
        }

        val githubClient = mcpServerManager.getClient(config.githubMcpServerId)
        if (githubClient == null) {
            return GithubActionResult(
                success = false,
                error = "GitHub MCP not connected"
            )
        }

        try {
            val prBody = """
                |## Описание
                |$cardDescription
                |
                |## Изменения
                |Автоматически созданный PR для ветки $headBranch
                |
                |---
                |*Создано через ResearchAI TaskWorkflow*
            """.trimMargin()

            val result = githubClient.callTool("create_pull_request", buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("title", title)
                put("head", headBranch)
                put("base", baseBranch)
                put("body", prBody)
            })

            return if (result.success) {
                val prInfo = parsePRResponse(result.content.firstOrNull()?.text)
                logger.info("PR #${prInfo.number} created successfully")
                GithubActionResult(
                    success = true,
                    branchName = headBranch,
                    prNumber = prInfo.number,
                    prUrl = prInfo.url,
                    prCreated = true
                )
            } else {
                GithubActionResult(
                    success = false,
                    branchName = headBranch,
                    error = result.error ?: "PR creation failed"
                )
            }
        } catch (e: Exception) {
            logger.error("Error creating PR: ${e.message}", e)
            return GithubActionResult(
                success = false,
                branchName = headBranch,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Проверить существование ветки
     */
    private suspend fun checkBranchExists(branchName: String, request: TaskWorkflowRequest): Boolean {
        val owner = request.githubOwner ?: config.defaultGithubOwner ?: return false
        val repo = request.githubRepo ?: config.defaultGithubRepo ?: return false

        val githubClient = mcpServerManager.getClient(config.githubMcpServerId) ?: return false

        return try {
            val result = githubClient.callTool("list_branches", buildJsonObject {
                put("owner", owner)
                put("repo", repo)
            })

            if (!result.success) return false

            val branches = parseBranchesResponse(result.content.firstOrNull()?.text)
            branches.any { it.equals(branchName, ignoreCase = true) }
        } catch (e: Exception) {
            logger.error("Error checking branch existence: ${e.message}", e)
            false
        }
    }

    /**
     * Синхронизация веток (merge source -> target)
     * Использует GitHub API для создания merge commit
     */
    private suspend fun mergeBranches(
        sourceBranch: String,
        targetBranch: String,
        request: TaskWorkflowRequest
    ): BranchSyncResult {
        val owner = request.githubOwner ?: config.defaultGithubOwner
        val repo = request.githubRepo ?: config.defaultGithubRepo

        if (owner == null || repo == null) {
            return BranchSyncResult(success = false)
        }

        val githubClient = mcpServerManager.getClient(config.githubMcpServerId)
        if (githubClient == null) {
            return BranchSyncResult(success = false)
        }

        return try {
            // Попытка merge через GitHub MCP
            // Используем merge_upstream или аналогичный инструмент если доступен
            val result = githubClient.callTool("merge_upstream", buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("branch", targetBranch)
            })

            if (result.success) {
                val content = result.content.firstOrNull()?.text
                val mergeCommitSha = parseMergeResponse(content)
                BranchSyncResult(
                    success = true,
                    hasConflicts = false,
                    mergeCommitSha = mergeCommitSha
                )
            } else {
                // Проверить на конфликты
                val errorText = result.error?.lowercase() ?: ""
                if (errorText.contains("conflict") || errorText.contains("merge")) {
                    BranchSyncResult(
                        success = false,
                        hasConflicts = true,
                        conflictingFiles = listOf("Конфликты обнаружены - требуется ручное разрешение")
                    )
                } else if (errorText.contains("already up to date") || errorText.contains("nothing to merge")) {
                    // Ветки уже синхронизированы
                    BranchSyncResult(success = true, hasConflicts = false)
                } else {
                    BranchSyncResult(success = false)
                }
            }
        } catch (e: Exception) {
            logger.error("Error merging branches: ${e.message}", e)
            BranchSyncResult(success = false)
        }
    }

    /**
     * Найти PR по имени ветки
     */
    private suspend fun findPullRequestByBranch(
        branchName: String,
        request: TaskWorkflowRequest
    ): PRInfo? {
        val owner = request.githubOwner ?: config.defaultGithubOwner ?: return null
        val repo = request.githubRepo ?: config.defaultGithubRepo ?: return null

        val githubClient = mcpServerManager.getClient(config.githubMcpServerId) ?: return null

        return try {
            val result = githubClient.callTool("list_pull_requests", buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("state", "open")
                put("head", "$owner:$branchName")
            })

            if (!result.success) return null

            val prs = parsePRListResponse(result.content.firstOrNull()?.text)
            prs.firstOrNull()
        } catch (e: Exception) {
            logger.error("Error finding PR by branch: ${e.message}", e)
            null
        }
    }

    /**
     * Merge Pull Request
     */
    private suspend fun mergePullRequest(
        prNumber: Int,
        request: TaskWorkflowRequest
    ): GithubActionResult {
        val owner = request.githubOwner ?: config.defaultGithubOwner
        val repo = request.githubRepo ?: config.defaultGithubRepo

        if (owner == null || repo == null) {
            return GithubActionResult(
                success = false,
                error = "GitHub owner/repo not configured"
            )
        }

        val githubClient = mcpServerManager.getClient(config.githubMcpServerId)
        if (githubClient == null) {
            return GithubActionResult(
                success = false,
                error = "GitHub MCP not connected"
            )
        }

        return try {
            val result = githubClient.callTool("merge_pull_request", buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("pull_number", prNumber)
                put("merge_method", "squash")
            })

            if (result.success) {
                GithubActionResult(
                    success = true,
                    prNumber = prNumber,
                    prMerged = true
                )
            } else {
                GithubActionResult(
                    success = false,
                    prNumber = prNumber,
                    error = result.error ?: "Merge failed"
                )
            }
        } catch (e: Exception) {
            logger.error("Error merging PR: ${e.message}", e)
            GithubActionResult(
                success = false,
                prNumber = prNumber,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Удалить ветку
     */
    private suspend fun deleteBranch(
        branchName: String,
        request: TaskWorkflowRequest
    ): Boolean {
        val owner = request.githubOwner ?: config.defaultGithubOwner ?: return false
        val repo = request.githubRepo ?: config.defaultGithubRepo ?: return false

        val githubClient = mcpServerManager.getClient(config.githubMcpServerId) ?: return false

        return try {
            val result = githubClient.callTool("delete_branch", buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("branch", branchName)
            })
            result.success
        } catch (e: Exception) {
            logger.warn("Failed to delete branch $branchName: ${e.message}")
            false
        }
    }

    // ==================== Парсинг ответов MCP ====================

    private data class TrelloList(val id: String, val name: String)
    private data class PRInfo(val number: Int?, val url: String?)

    private fun parseListsResponse(content: String?): List<TrelloList> {
        if (content.isNullOrBlank()) return emptyList()

        return try {
            val json = Json.parseToJsonElement(content)

            val listsArray = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("lists") -> json["lists"]?.jsonArray
                else -> null
            } ?: return emptyList()

            listsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                TrelloList(id, name)
            }
        } catch (e: Exception) {
            logger.error("Error parsing lists response: ${e.message}")
            emptyList()
        }
    }

    private fun parseCardsResponse(content: String?, listId: String, listName: String): List<TrelloCardInfo> {
        if (content.isNullOrBlank()) return emptyList()

        return try {
            val json = Json.parseToJsonElement(content)

            val cardsArray = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("cards") -> json["cards"]?.jsonArray
                else -> null
            } ?: return emptyList()

            cardsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val url = obj["url"]?.jsonPrimitive?.contentOrNull
                    ?: obj["shortUrl"]?.jsonPrimitive?.contentOrNull
                TrelloCardInfo(id, name, listId, listName, url)
            }
        } catch (e: Exception) {
            logger.error("Error parsing cards response: ${e.message}")
            emptyList()
        }
    }

    private fun parseBranchesResponse(content: String?): List<String> {
        if (content.isNullOrBlank()) return emptyList()

        return try {
            val json = Json.parseToJsonElement(content)

            val branchesArray = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("branches") -> json["branches"]?.jsonArray
                else -> null
            } ?: return emptyList()

            branchesArray.mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.content
                    is JsonObject -> element["name"]?.jsonPrimitive?.content
                    else -> null
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing branches response: ${e.message}")
            emptyList()
        }
    }

    private fun parsePRResponse(content: String?): PRInfo {
        if (content.isNullOrBlank()) return PRInfo(null, null)

        return try {
            val json = Json.parseToJsonElement(content).jsonObject
            val number = json["number"]?.jsonPrimitive?.intOrNull
            val url = json["html_url"]?.jsonPrimitive?.contentOrNull
                ?: json["url"]?.jsonPrimitive?.contentOrNull
            PRInfo(number, url)
        } catch (e: Exception) {
            logger.error("Error parsing PR response: ${e.message}")
            PRInfo(null, null)
        }
    }

    private fun parsePRListResponse(content: String?): List<PRInfo> {
        if (content.isNullOrBlank()) return emptyList()

        return try {
            val json = Json.parseToJsonElement(content)

            val prsArray = when {
                json is JsonArray -> json
                json is JsonObject && json.containsKey("pull_requests") -> json["pull_requests"]?.jsonArray
                json is JsonObject && json.containsKey("items") -> json["items"]?.jsonArray
                else -> null
            } ?: return emptyList()

            prsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val number = obj["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val url = obj["html_url"]?.jsonPrimitive?.contentOrNull
                    ?: obj["url"]?.jsonPrimitive?.contentOrNull
                PRInfo(number, url)
            }
        } catch (e: Exception) {
            logger.error("Error parsing PR list response: ${e.message}")
            emptyList()
        }
    }

    private fun parseMergeResponse(content: String?): String? {
        if (content.isNullOrBlank()) return null

        return try {
            val json = Json.parseToJsonElement(content).jsonObject
            json["sha"]?.jsonPrimitive?.contentOrNull
                ?: json["merge_commit_sha"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            logger.error("Error parsing merge response: ${e.message}")
            null
        }
    }

    // ==================== RAG Context ====================

    /**
     * Получить контекст задачи из RAG (связанные файлы и задачи)
     */
    private suspend fun fetchTaskContext(taskId: String, cardName: String): TaskContextInfo? {
        if (ragManager == null) return null

        return try {
            // Сформировать запросы для поиска
            val queries = listOf(
                cardName,
                "$taskId implementation",
                cardName.split(" ").filter { it.length > 3 }.joinToString(" ")
            ).filter { it.isNotBlank() }.distinct()

            val allResults = mutableSetOf<SearchResult>()

            for (query in queries) {
                val results = ragManager.searchRelevantContext(
                    query = query,
                    topK = 5,
                    minScore = 0.5f
                )
                allResults.addAll(results)
            }

            if (allResults.isEmpty()) {
                logger.info("No RAG context found for task: $taskId")
                return null
            }

            // Извлечь файлы из результатов
            val relatedFiles = allResults
                .mapNotNull { it.sourceFileName }
                .distinct()
                .take(10)

            logger.info("Found ${relatedFiles.size} related files for task: $taskId")

            TaskContextInfo(
                relatedFiles = relatedFiles,
                relatedTasks = emptyList() // TODO: можно добавить поиск похожих задач в Trello
            )
        } catch (e: Exception) {
            logger.warn("Failed to fetch RAG context for task $taskId: ${e.message}")
            null
        }
    }

    // ==================== Утилиты ====================

    private fun errorResult(
        message: String,
        taskId: String = "unknown",
        action: TaskAction = TaskAction.START,
        trelloResult: TrelloActionResult? = null,
        githubResult: GithubActionResult? = null
    ): TaskWorkflowResult {
        return TaskWorkflowResult(
            success = false,
            taskId = taskId,
            action = action,
            trelloResult = trelloResult,
            githubResult = githubResult,
            message = message,
            errors = listOf(message)
        )
    }
}
