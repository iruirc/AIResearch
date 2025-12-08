package com.researchai.domain.models.workflow

import kotlinx.serialization.Serializable

/**
 * Действие над задачей в workflow
 */
@Serializable
enum class TaskAction {
    START,      // Начать работу над задачей
    SYNC,       // Синхронизировать ветку с main
    COMPLETE,   // Завершить задачу
    APPROVE,    // Принять PR и смержить
    CANCEL      // Отменить работу над задачей
}

/**
 * Запрос на выполнение workflow
 */
@Serializable
data class TaskWorkflowRequest(
    val query: String,                    // Исходный запрос пользователя
    val action: TaskAction? = null,       // Если определено заранее
    val taskId: String? = null,           // Если известен ID задачи (Task_N)
    val githubOwner: String? = null,      // Override для GitHub owner
    val githubRepo: String? = null,       // Override для GitHub repo
    val trelloBoardId: String? = null     // Override для Trello board
)

/**
 * Информация о карточке Trello (для внутреннего использования)
 */
@Serializable
data class TrelloCardInfo(
    val cardId: String,
    val cardName: String,
    val listId: String,
    val listName: String,
    val url: String? = null
)

/**
 * Результат операции с Trello
 */
@Serializable
data class TrelloActionResult(
    val success: Boolean,
    val cardId: String,
    val cardName: String,
    val fromList: String,
    val toList: String,
    val cardUrl: String? = null,
    val commentAdded: Boolean = false,
    val error: String? = null
)

/**
 * Результат операции с GitHub
 */
@Serializable
data class GithubActionResult(
    val success: Boolean,
    val branchName: String? = null,
    val branchCreated: Boolean = false,
    val branchAlreadyExists: Boolean = false,
    val branchDeleted: Boolean = false,
    val prNumber: Int? = null,
    val prUrl: String? = null,
    val prCreated: Boolean = false,
    val prMerged: Boolean = false,
    val syncResult: BranchSyncResult? = null,
    val error: String? = null
)

/**
 * Результат синхронизации веток
 */
@Serializable
data class BranchSyncResult(
    val success: Boolean,
    val hasConflicts: Boolean = false,
    val conflictingFiles: List<String> = emptyList(),
    val commitsAhead: Int = 0,
    val commitsBehind: Int = 0,
    val mergeCommitSha: String? = null
)

/**
 * Результат workflow операции
 */
@Serializable
data class TaskWorkflowResult(
    val success: Boolean,
    val taskId: String,
    val action: TaskAction,
    val trelloResult: TrelloActionResult? = null,
    val githubResult: GithubActionResult? = null,
    val reviewResult: ReviewSummaryInfo? = null,
    val contextInfo: TaskContextInfo? = null,
    val message: String,
    val errors: List<String> = emptyList(),
    val wasRolledBack: Boolean = false
)

/**
 * Краткая информация о код-ревью
 */
@Serializable
data class ReviewSummaryInfo(
    val overallScore: Int,
    val criticalIssuesCount: Int,
    val importantIssuesCount: Int,
    val suggestionsCount: Int,
    val reviewUrl: String? = null
)

/**
 * Контекст задачи из RAG
 */
@Serializable
data class TaskContextInfo(
    val relatedFiles: List<String> = emptyList(),
    val relatedTasks: List<String> = emptyList(),
    val suggestedApproach: String? = null
)

/**
 * Информация о списке Trello (для кэширования)
 */
@Serializable
data class TrelloListInfo(
    val id: String,
    val name: String,
    val position: Int  // Порядок: 0=Inbox, 1=Backlog, 2=ToDo, 3=InProgress, 4=Review, 5=Done
)
