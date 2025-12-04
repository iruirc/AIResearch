package com.researchai.services

import com.researchai.data.mcp.MCPServerManager
import com.researchai.domain.models.techsupport.TechSupportConfig
import com.researchai.domain.models.workflow.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Service for handling task workflow operations:
 * - Start task: move Trello card to InProgress + create Git branch
 * - Complete task: move Trello card to Review + create PR
 */
class TaskWorkflowService(
    private val mcpServerManager: MCPServerManager,
    private val config: TechSupportConfig
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

        // Ключевые слова для определения действия COMPLETE
        private val COMPLETE_KEYWORDS = listOf(
            "завершил", "завершила", "закончил", "закончила", "готов", "готова",
            "сделал", "сделала", "выполнил", "выполнила", "готова", "сделана", "сделано",
            "finished", "completed", "done", "ready", "complete"
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
            TaskAction.COMPLETE -> completeTask(taskId, request)
            TaskAction.CANCEL -> cancelTask(taskId, request)
        }
    }

    /**
     * Начать работу над задачей:
     * 1. Найти карточку в Trello
     * 2. Проверить, что карточка левее InProgress
     * 3. Создать ветку в GitHub
     * 4. Переместить карточку в InProgress
     * 5. Добавить комментарий
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

        // 3. Создать ветку в GitHub
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

        // 4. Переместить карточку в InProgress
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
                message = "Частичный успех: ветка ${if (githubResult.branchAlreadyExists) "уже существует" else "создана"}, но карточка не перемещена: ${trelloResult.error}",
                errors = listOf(trelloResult.error ?: "Ошибка Trello"),
                wasRolledBack = false
            )
        }

        // 5. Добавить комментарий в Trello
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
        }

        logger.info("Task $taskId started successfully")

        return TaskWorkflowResult(
            success = true,
            taskId = taskId,
            action = TaskAction.START,
            trelloResult = trelloResult.copy(commentAdded = commentAdded),
            githubResult = githubResult,
            message = message
        )
    }

    /**
     * Завершить работу над задачей:
     * 1. Найти карточку в Trello
     * 2. Проверить, что карточка левее Review
     * 3. Создать PR в GitHub
     * 4. Переместить карточку в Review
     * 5. Добавить комментарий с ссылкой на PR
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

        // 4. Переместить карточку в Review
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
                message = "Частичный успех: PR #${githubResult.prNumber} создан, но карточка не перемещена: ${trelloResult.error}",
                errors = listOf(trelloResult.error ?: "Ошибка Trello"),
                wasRolledBack = false
            )
        }

        // 5. Добавить комментарий в Trello с ссылкой на PR
        val prLink = githubResult.prUrl ?: "PR #${githubResult.prNumber}"
        val comment = "Задача завершена. Создан PR: $prLink"
        val commentAdded = addCommentToCard(card.cardId, comment, request.trelloBoardId)

        val message = "Задача $taskId завершена. PR #${githubResult.prNumber} создан, карточка перемещена в Review."

        logger.info("Task $taskId completed successfully")

        return TaskWorkflowResult(
            success = true,
            taskId = taskId,
            action = TaskAction.COMPLETE,
            trelloResult = trelloResult.copy(commentAdded = commentAdded),
            githubResult = githubResult,
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
            COMPLETE_KEYWORDS.any { lowerQuery.contains(it) } -> TaskAction.COMPLETE
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
        val hasActionKeyword = (START_KEYWORDS + COMPLETE_KEYWORDS + CANCEL_KEYWORDS)
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
                error = "GitHub owner/repo not configured (set GITHUB_DEFAULT_OWNER and GITHUB_DEFAULT_REPO)"
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
