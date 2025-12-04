package com.researchai.domain.models.techsupport

import com.researchai.domain.models.ProviderType
import kotlinx.serialization.Serializable

/**
 * Тип запроса в техподдержку (определяется AI)
 */
@Serializable
enum class QueryType {
    BUG_REPORT,          // Сообщение об ошибке
    HOW_TO,              // Вопрос "как сделать"
    STATUS_CHECK,        // Проверка статуса тикета
    FEATURE_REQUEST,     // Запрос на новую фичу
    PROJECT_MANAGEMENT,  // Запрос о статусе проекта, приоритизации задач
    GITHUB_INFO,         // Вопрос о GitHub репозитории (ветки, коммиты, issues, PRs)
    TASK_WORKFLOW,       // Workflow задач: начать/завершить работу над задачей
    GENERAL              // Общий вопрос
}

/**
 * Запрос в техподдержку
 */
@Serializable
data class TechSupportRequest(
    val query: String,
    val sessionId: String? = null,
    val customerId: String? = null,
    val trelloBoardId: String? = null,
    val githubOwner: String? = null,
    val githubRepo: String? = null,
    val includeRag: Boolean = true,
    val includeTrello: Boolean = true,
    val includeGithub: Boolean = true,
    val maxRagResults: Int = 5,
    val maxTrelloResults: Int = 3,
    val maxGithubResults: Int = 10,
    val providerId: ProviderType = ProviderType.CLAUDE,
    val model: String? = null,
    val existingAnswer: String? = null  // Skip AI call if answer already provided from main chat
)

/**
 * Информация о тикете Trello
 */
@Serializable
data class TrelloTicketInfo(
    val cardId: String,
    val cardName: String,
    val listName: String,
    val description: String?,
    val labels: List<String>,
    val lastActivity: String?,
    val url: String?
)

// ==================== GitHub Models ====================

/**
 * Информация о ветке GitHub
 */
@Serializable
data class GitHubBranchInfo(
    val name: String,
    val sha: String,
    val isProtected: Boolean = false,
    val isDefault: Boolean = false,
    val url: String? = null
)

/**
 * Информация о коммите GitHub
 */
@Serializable
data class GitHubCommitInfo(
    val sha: String,
    val message: String,
    val author: String,
    val date: String,
    val url: String? = null
)

/**
 * Информация об Issue GitHub
 */
@Serializable
data class GitHubIssueInfo(
    val number: Int,
    val title: String,
    val state: String,  // "open" or "closed"
    val author: String,
    val labels: List<String>,
    val createdAt: String,
    val updatedAt: String?,
    val body: String?,
    val url: String?
)

/**
 * Информация о Pull Request GitHub
 */
@Serializable
data class GitHubPRInfo(
    val number: Int,
    val title: String,
    val state: String,  // "open", "closed", "merged"
    val author: String,
    val sourceBranch: String,
    val targetBranch: String,
    val labels: List<String>,
    val createdAt: String,
    val updatedAt: String?,
    val isDraft: Boolean = false,
    val url: String?
)

/**
 * Результат поиска контекста из GitHub
 */
@Serializable
data class GitHubContextResult(
    val formattedContext: String,
    val branches: List<GitHubBranchInfo> = emptyList(),
    val commits: List<GitHubCommitInfo> = emptyList(),
    val issues: List<GitHubIssueInfo> = emptyList(),
    val pullRequests: List<GitHubPRInfo> = emptyList(),
    val repositoryInfo: GitHubRepoInfo? = null
)

/**
 * Базовая информация о репозитории
 */
@Serializable
data class GitHubRepoInfo(
    val owner: String,
    val name: String,
    val fullName: String,
    val defaultBranch: String,
    val description: String?,
    val url: String
)

/**
 * Результат поиска контекста из RAG
 */
@Serializable
data class RagContextResult(
    val formattedContext: String,
    val sourceCount: Int,
    val sources: List<String>
)

/**
 * Результат поиска тикетов из Trello
 */
@Serializable
data class TicketContextResult(
    val relatedTickets: List<TrelloTicketInfo>,
    val formattedContext: String
)

/**
 * Объединённый контекст
 */
@Serializable
data class TechSupportContext(
    val ragContext: RagContextResult?,
    val ticketContext: TicketContextResult?,
    val githubContext: GitHubContextResult?,
    val queryType: QueryType,
    val processingTimeMs: Long
)

/**
 * Использованные источники
 */
@Serializable
data class SourcesUsed(
    val ragSourceCount: Int,
    val trelloTicketCount: Int,
    val githubItemCount: Int = 0,
    val ragSources: List<String>,
    val trelloSources: List<String>,
    val githubSources: List<String> = emptyList()
)

/**
 * Предложенное действие - создать тикет
 */
@Serializable
data class CreateTicketAction(
    val type: String = "CREATE_TICKET",
    val title: String,
    val description: String,
    val suggestedList: String = "Inbox",
    val suggestedLabels: List<String> = emptyList()
)

/**
 * Предложенное действие - посмотреть тикет
 */
@Serializable
data class ViewTicketAction(
    val type: String = "VIEW_TICKET",
    val cardId: String,
    val cardName: String,
    val reason: String,
    val url: String? = null
)

/**
 * Предложенное действие - эскалация на человека
 */
@Serializable
data class EscalateAction(
    val type: String = "ESCALATE",
    val reason: String,
    val priority: String = "normal", // low, normal, high, urgent
    val suggestedTeam: String? = null
)

/**
 * Предложенное действие - добавить в FAQ
 */
@Serializable
data class AddToFaqAction(
    val type: String = "ADD_TO_FAQ",
    val question: String,
    val suggestedAnswer: String,
    val category: String? = null
)

/**
 * Предложенное действие - связаться с поддержкой
 */
@Serializable
data class ContactSupportAction(
    val type: String = "CONTACT_SUPPORT",
    val reason: String,
    val suggestedChannel: String = "email" // email, chat, phone
)

/**
 * Краткая информация о задаче для списка
 */
@Serializable
data class TaskSummary(
    val cardId: String,
    val cardName: String,
    val listName: String,
    val priority: String?,  // "high", "medium", "low" or null
    val url: String?
)

/**
 * Предложенное действие - показать список задач
 */
@Serializable
data class ListTasksAction(
    val type: String = "LIST_TASKS",
    val filter: String,  // "high", "medium", "low", "all"
    val tasks: List<TaskSummary>,
    val totalCount: Int
)

/**
 * Рекомендация AI по задаче
 */
@Serializable
data class TaskRecommendation(
    val order: Int,
    val cardId: String,
    val cardName: String,
    val reason: String,
    val estimatedEffort: String? = null,  // "small", "medium", "large"
    val dependencies: List<String> = emptyList()
)

/**
 * Предложенное действие - приоритизация задач
 */
@Serializable
data class PrioritizeAction(
    val type: String = "PRIORITIZE",
    val recommendedOrder: List<TaskRecommendation>
)

/**
 * Предложенное действие - показать статус проекта
 */
@Serializable
data class ProjectStatusAction(
    val type: String = "PROJECT_STATUS",
    val totalTasks: Int,
    val byPriority: Map<String, Int>,  // {"high": 5, "medium": 10, "low": 3}
    val byStatus: Map<String, Int>     // {"To Do": 10, "In Progress": 5, "Done": 3}
)

// ==================== GitHub Actions ====================

/**
 * Предложенное действие - посмотреть ветку GitHub
 */
@Serializable
data class ViewBranchAction(
    val type: String = "VIEW_BRANCH",
    val branchName: String,
    val sha: String,
    val isDefault: Boolean = false,
    val url: String? = null
)

/**
 * Предложенное действие - посмотреть коммит GitHub
 */
@Serializable
data class ViewCommitAction(
    val type: String = "VIEW_COMMIT",
    val sha: String,
    val message: String,
    val author: String,
    val url: String? = null
)

/**
 * Предложенное действие - посмотреть Issue GitHub
 */
@Serializable
data class ViewIssueAction(
    val type: String = "VIEW_ISSUE",
    val number: Int,
    val title: String,
    val state: String,
    val url: String? = null
)

/**
 * Предложенное действие - посмотреть PR GitHub
 */
@Serializable
data class ViewPullRequestAction(
    val type: String = "VIEW_PR",
    val number: Int,
    val title: String,
    val state: String,
    val sourceBranch: String,
    val targetBranch: String,
    val url: String? = null
)

/**
 * Предложенное действие - показать список веток
 */
@Serializable
data class ListBranchesAction(
    val type: String = "LIST_BRANCHES",
    val branches: List<GitHubBranchInfo>,
    val defaultBranch: String?,
    val totalCount: Int
)

/**
 * Предложенное действие - показать репозиторий
 */
@Serializable
data class ViewRepoAction(
    val type: String = "VIEW_REPO",
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val description: String?,
    val url: String
)

/**
 * Ответ от техподдержки
 */
@Serializable
data class TechSupportResponse(
    val answer: String,
    val sessionId: String,
    val queryType: QueryType,
    val sourcesUsed: SourcesUsed,
    val suggestedActions: List<SuggestedActionWrapper>,
    val relatedTickets: List<TrelloTicketInfo>,
    val processingTimeMs: Long
)

/**
 * Wrapper для suggested actions (для JSON сериализации)
 */
@Serializable
data class SuggestedActionWrapper(
    val actionType: String,
    val createTicket: CreateTicketAction? = null,
    val viewTicket: ViewTicketAction? = null,
    val escalate: EscalateAction? = null,
    val addToFaq: AddToFaqAction? = null,
    val contactSupport: ContactSupportAction? = null,
    // Project Management actions
    val listTasks: ListTasksAction? = null,
    val prioritize: PrioritizeAction? = null,
    val projectStatus: ProjectStatusAction? = null,
    // GitHub actions
    val viewBranch: ViewBranchAction? = null,
    val viewCommit: ViewCommitAction? = null,
    val viewIssue: ViewIssueAction? = null,
    val viewPullRequest: ViewPullRequestAction? = null,
    val listBranches: ListBranchesAction? = null,
    val viewRepo: ViewRepoAction? = null
)

/**
 * Запрос на создание тикета
 */
@Serializable
data class CreateTicketRequest(
    val title: String,
    val description: String,
    val listName: String = "Inbox",
    val labels: List<String> = emptyList(),
    val boardId: String? = null
)

/**
 * Результат создания тикета
 */
@Serializable
data class CreateTicketResponse(
    val success: Boolean,
    val cardId: String?,
    val cardUrl: String?,
    val error: String? = null
)

/**
 * Запрос на добавление FAQ в RAG
 */
@Serializable
data class AddFaqRequest(
    val question: String,
    val answer: String,
    val category: String? = "general"
)

/**
 * Ответ на добавление FAQ
 */
@Serializable
data class AddFaqResponse(
    val success: Boolean,
    val documentId: String? = null,
    val documentName: String? = null,
    val error: String? = null
)

/**
 * Конфигурация TechSupportService
 */
data class TechSupportConfig(
    val ragMinScore: Float = 0.5f,
    val defaultBoardId: String? = null,
    val trelloMcpServerId: String = "trello",
    // GitHub settings
    val defaultGithubOwner: String? = null,
    val defaultGithubRepo: String? = null,
    val githubMcpServerId: String = "github"
)

/**
 * Ответ на health check запрос
 */
@Serializable
data class TechSupportHealthResponse(
    val status: String,
    val service: String = "tech-support",
    val ragEnabled: Boolean,
    val trelloConnected: Boolean,
    val githubConnected: Boolean = false,
    val error: String? = null
)
