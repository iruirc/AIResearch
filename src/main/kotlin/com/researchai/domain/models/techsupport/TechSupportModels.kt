package com.researchai.domain.models.techsupport

import com.researchai.domain.models.ProviderType
import kotlinx.serialization.Serializable

/**
 * Тип запроса в техподдержку (определяется AI)
 */
@Serializable
enum class QueryType {
    BUG_REPORT,       // Сообщение об ошибке
    HOW_TO,           // Вопрос "как сделать"
    STATUS_CHECK,     // Проверка статуса тикета
    FEATURE_REQUEST,  // Запрос на новую фичу
    GENERAL           // Общий вопрос
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
    val includeRag: Boolean = true,
    val includeTrello: Boolean = true,
    val maxRagResults: Int = 5,
    val maxTrelloResults: Int = 3,
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
    val ragSources: List<String>,
    val trelloSources: List<String>
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
    val reason: String
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
    val contactSupport: ContactSupportAction? = null
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
    val trelloMcpServerId: String = "trello"
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
    val error: String? = null
)
