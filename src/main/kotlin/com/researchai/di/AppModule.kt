package com.researchai.di

import com.researchai.auth.data.provider.GoogleAuthProvider
import com.researchai.auth.data.provider.GoogleOAuthConfig
import com.researchai.auth.data.repository.UserRepositoryImpl
import com.researchai.auth.domain.models.JWTConfig
import com.researchai.auth.domain.repository.UserRepository
import com.researchai.auth.service.AuthService
import com.researchai.auth.service.JWTService
import com.researchai.auth.service.WhitelistService
import com.researchai.config.ClaudeConfig
import com.researchai.config.OpenAIConfig
import com.researchai.config.HuggingFaceConfig
import com.researchai.config.OllamaConfig
import com.researchai.config.getMCPServers
import com.researchai.data.provider.ollama.OllamaConnectionManager
import com.researchai.data.tokenizer.JTokkitTokenCounter
import com.researchai.data.mcp.MCPServerManager
import com.researchai.domain.mcp.MCPOrchestrationService
import com.researchai.data.provider.AIProviderFactoryImpl
import com.researchai.data.repository.ConfigRepositoryImpl
import com.researchai.data.repository.SessionRepositoryImpl
import com.researchai.domain.provider.AIProviderFactory
import com.researchai.domain.repository.ConfigRepository
import com.researchai.domain.repository.SessionRepository
import com.researchai.domain.usecase.GetModelsUseCase
import com.researchai.domain.usecase.SendMessageUseCase
import com.researchai.persistence.JsonPersistenceStorage
import com.researchai.persistence.PersistenceManager
import com.researchai.persistence.MCPPreferencesStorage
import com.researchai.persistence.ScheduledTaskStorage
import com.researchai.persistence.AssistantStorage
import com.researchai.persistence.JsonAssistantStorage
import com.researchai.persistence.PreferencesStorage
import com.researchai.persistence.JsonPreferencesStorage
import com.researchai.persistence.sql.DatabaseConfig
import com.researchai.persistence.sql.DatabaseFactory
import com.researchai.persistence.sql.FlywayMigrator
import com.researchai.persistence.sql.StorageFactory
import com.researchai.services.AssistantManager
import com.researchai.services.ChatCompressionService
import com.researchai.services.ChatSessionManager
import com.researchai.services.SchedulerManager
import com.researchai.services.PreferencesManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Dependency Injection контейнер для приложения
 */
class AppModule(
    private val claudeConfig: ClaudeConfig,
    private val openAIConfig: OpenAIConfig? = null,
    private val huggingFaceConfig: HuggingFaceConfig? = null,
    private val ollamaConfig: OllamaConfig? = null,
    private val jwtConfig: JWTConfig? = null,
    private val googleOAuthConfig: GoogleOAuthConfig? = null,
    private val allowedEmails: String? = null,
    private val enablePostgres: Boolean = false
) {
    private val logger = LoggerFactory.getLogger(AppModule::class.java)

    // Database initialization
    init {
        if (enablePostgres) {
            try {
                logger.info("Initializing PostgreSQL database...")
                val dbConfig = DatabaseConfig.fromEnv()
                DatabaseFactory.init(dbConfig)

                logger.info("Running Flyway migrations...")
                FlywayMigrator.migrate(DatabaseFactory.dataSource)

                logger.info("PostgreSQL initialized successfully")
            } catch (e: Exception) {
                logger.error("Failed to initialize PostgreSQL, will use JSON storage as fallback", e)
            }
        } else {
            logger.info("PostgreSQL disabled, using JSON storage")
        }
    }
    // HTTP Client
    val httpClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 300_000 // 5 минут
                connectTimeoutMillis = 10_000  // 10 секунд
                socketTimeoutMillis = 300_000  // 5 минут
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
        }
    }

    // Persistence
    val persistenceManager: PersistenceManager by lazy {
        val storage = StorageFactory.createPersistenceStorage(enablePostgres)
        PersistenceManager(
            storage = storage,
            saveDelayMs = 1000,  // 1 секунда задержка перед сохранением
            batchSize = 10       // Максимум 10 сессий в batch
        )
    }

    // Assistant Storage
    val assistantStorage: AssistantStorage by lazy {
        StorageFactory.createAssistantStorage(enablePostgres)
    }

    // Preferences Storage
    val preferencesStorage: PreferencesStorage by lazy {
        JsonPreferencesStorage() // TODO: Migrate to PostgreSQL (requires schema alignment)
    }

    // Legacy services
    val chatSessionManager: ChatSessionManager by lazy {
        ChatSessionManager(persistenceManager)
    }

    val assistantManager: AssistantManager by lazy {
        AssistantManager(assistantStorage)
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(preferencesStorage)
    }

    // PR Review Service
    val prReviewService: com.researchai.services.PRReviewService by lazy {
        com.researchai.services.PRReviewService(
            mcpServerManager = mcpServerManager,
            aiProviderFactory = providerFactory,
            configRepository = configRepository,
            assistantManager = assistantManager,
            ragManager = ragManager,
            preferencesManager = preferencesManager
        )
    }

    // Tech Support Service
    val techSupportConfig: com.researchai.domain.models.techsupport.TechSupportConfig by lazy {
        com.researchai.domain.models.techsupport.TechSupportConfig(
            ragMinScore = 0.5f,
            defaultBoardId = System.getenv("TRELLO_SUPPORT_BOARD_ID"),
            trelloMcpServerId = "trello"
        )
    }

    val techSupportService: com.researchai.services.TechSupportService by lazy {
        com.researchai.services.TechSupportService(
            mcpServerManager = mcpServerManager,
            ragManager = ragManager,
            aiProviderFactory = providerFactory,
            configRepository = configRepository,
            assistantManager = assistantManager,
            preferencesManager = preferencesManager,
            config = techSupportConfig
        )
    }

    // Ollama Connection Manager
    val ollamaConnectionManager: OllamaConnectionManager by lazy {
        val tokenCounter = JTokkitTokenCounter.forModel("llama3.2")
        OllamaConnectionManager(httpClient, preferencesStorage, tokenCounter)
    }

    // Repositories
    val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(chatSessionManager)
    }

    val configRepository: ConfigRepository by lazy {
        ConfigRepositoryImpl(claudeConfig, openAIConfig, huggingFaceConfig, ollamaConnectionManager)
    }

    // Provider Factory
    val providerFactory: AIProviderFactory by lazy {
        AIProviderFactoryImpl(httpClient)
    }

    // MCP Preferences Storage
    val mcpPreferencesStorage: MCPPreferencesStorage by lazy {
        MCPPreferencesStorage()
    }

    // MCP Server Manager
    val mcpServerManager: MCPServerManager by lazy {
        val configs = getMCPServers()
        MCPServerManager(configs, mcpPreferencesStorage)
    }

    // MCP Orchestration Service
    val mcpOrchestrationService: MCPOrchestrationService by lazy {
        MCPOrchestrationService(mcpServerManager)
    }

    // Use Cases
    val sendMessageUseCase: SendMessageUseCase by lazy {
        SendMessageUseCase(
            providerFactory = providerFactory,
            sessionRepository = sessionRepository,
            configRepository = configRepository,
            assistantManager = assistantManager,
            mcpOrchestrationService = mcpOrchestrationService,
            ollamaConnectionManager = ollamaConnectionManager,
            ragManager = ragManager,
            ragPreferencesStorage = ragPreferencesStorage,
            citationExtractor = citationExtractor
        )
    }

    val getModelsUseCase: GetModelsUseCase by lazy {
        GetModelsUseCase(
            providerFactory = providerFactory,
            configRepository = configRepository
        )
    }

    // Compression Service
    val compressionService: ChatCompressionService by lazy {
        ChatCompressionService(
            providerFactory = providerFactory,
            configRepository = configRepository
        )
    }

    // Scheduler
    val scheduledTaskStorage: ScheduledTaskStorage by lazy {
        ScheduledTaskStorage()
    }

    val schedulerManager: SchedulerManager by lazy {
        SchedulerManager(
            sessionManager = chatSessionManager,
            sendMessageUseCase = sendMessageUseCase,
            storage = scheduledTaskStorage
        )
    }

    // Authentication (только если jwtConfig не null)
    val userRepository: UserRepository? by lazy {
        if (jwtConfig != null) UserRepositoryImpl() else null
    }

    val jwtService: JWTService? by lazy {
        jwtConfig?.let { JWTService(it) }
    }

    val whitelistService: WhitelistService? by lazy {
        if (jwtConfig != null) WhitelistService.fromCommaSeparatedString(allowedEmails) else null
    }

    val authService: AuthService? by lazy {
        if (jwtConfig != null && jwtService != null && whitelistService != null && userRepository != null) {
            AuthService(userRepository!!, jwtService!!, whitelistService!!)
        } else null
    }

    val googleAuthProvider: GoogleAuthProvider? by lazy {
        googleOAuthConfig?.let {
            GoogleAuthProvider(it, httpClient)
        }
    }

    // ============================================
    // Pipeline Components
    // ============================================

    /**
     * Storage for pipeline configurations and executions
     */
    val pipelineStorage: com.researchai.persistence.AssistantPipelineStorage by lazy {
        com.researchai.persistence.JsonPipelineStorage(
            pipelinesDir = java.io.File("data/assistant_pipelines"),
            executionsDir = java.io.File("data/pipeline_executions")
        )
    }

    /**
     * Use case for executing assistant pipelines
     */
    val assistantPipelineUseCase: com.researchai.domain.usecase.AssistantPipelineUseCase by lazy {
        com.researchai.domain.usecase.AssistantPipelineUseCase(
            sendMessageUseCase = sendMessageUseCase,
            assistantManager = assistantManager,
            sessionRepository = sessionRepository,
            pipelineStorage = pipelineStorage
        )
    }

    // ============================================
    // RAG Components
    // ============================================

    /**
     * RAG Configuration
     */
    val ragConfig: com.researchai.domain.models.RAGConfig by lazy {
        com.researchai.domain.models.RAGConfig()
    }

    /**
     * RAG Document Storage (JSON-based)
     */
    val ragDocumentStorage: com.researchai.persistence.RAGDocumentStorage by lazy {
        com.researchai.persistence.JsonRAGDocumentStorage()
    }

    /**
     * RAG Search Preferences Storage
     */
    val ragPreferencesStorage: com.researchai.persistence.RAGPreferencesStorage by lazy {
        com.researchai.persistence.JsonRAGPreferencesStorage()
    }

    /**
     * RAG Test Storage (JSON or PostgreSQL based on enablePostgres flag)
     */
    val ragTestStorage: com.researchai.persistence.RAGTestStorage by lazy {
        StorageFactory.createRAGTestStorage(enablePostgres)
    }

    /**
     * Ollama Embedding Service
     */
    val embeddingService: com.researchai.domain.rag.EmbeddingService by lazy {
        com.researchai.data.rag.OllamaEmbeddingService(httpClient, ragConfig)
    }

    /**
     * In-Memory Vector Search
     */
    val vectorSearchService: com.researchai.domain.rag.VectorSearchService by lazy {
        com.researchai.data.rag.InMemoryVectorSearch()
    }

    /**
     * Reranker Service for two-stage search
     */
    val rerankerService: com.researchai.services.RerankerService by lazy {
        com.researchai.services.RerankerService(httpClient, ragConfig.ollamaUrl)
    }

    /**
     * RAG Manager - Main orchestrator for RAG operations
     */
    val ragManager: com.researchai.services.RAGManager by lazy {
        com.researchai.services.RAGManager(
            embeddingService = embeddingService,
            vectorSearch = vectorSearchService,
            storage = ragDocumentStorage,
            config = ragConfig,
            rerankerService = rerankerService
        )
    }

    /**
     * Citation Extractor - Formats RAG responses with source citations
     */
    val citationExtractor: com.researchai.services.CitationExtractor by lazy {
        com.researchai.services.CitationExtractor()
    }

    /**
     * RAG Test Execution Service - Handles batch test execution with progress streaming
     */
    val ragTestExecutionService: com.researchai.services.RAGTestExecutionService by lazy {
        com.researchai.services.RAGTestExecutionService(
            testStorage = ragTestStorage,
            sendMessageUseCase = sendMessageUseCase,
            sessionManager = chatSessionManager,
            preferencesManager = preferencesManager,
            ragManager = ragManager,
            ragPreferencesStorage = ragPreferencesStorage
        )
    }

    /**
     * Initialize RAG system
     */
    suspend fun initializeRAG() {
        ragManager.initialize()
    }

    /**
     * Initialize MCP servers asynchronously
     */
    suspend fun initializeMCP() {
        mcpServerManager.initialize()
    }

    /**
     * Очистка ресурсов
     */
    fun close() {
        runBlocking {
            schedulerManager.shutdown()
            chatSessionManager.shutdown()
            mcpServerManager.shutdown()
            assistantManager.close()
            pipelineStorage.close()
        }
        httpClient.close()
    }
}
