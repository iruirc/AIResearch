# MCP Tool Orchestration Implementation

## Overview

This document describes the implementation of MCP (Model Context Protocol) tool orchestration in the ResearchAI chat system. The orchestration allows AI models to automatically discover and use tools provided by MCP servers during conversations.

## Architecture

The implementation follows a clean architecture pattern with clear separation of concerns:

```
User Request
    ↓
SendMessageUseCase
    ↓
MCPOrchestrationService ← MCPServerManager
    ↓                          ↓
AIProvider (Claude)        MCP Servers
    ↓
Tool Use Loop
```

## Implementation Details

### 1. MCP Orchestration Service

**File**: `src/main/kotlin/com/researchai/domain/mcp/MCPOrchestrationService.kt`

A service that manages MCP tool interactions with AI models.

**Key Methods**:
- `getAvailableTools()`: Retrieves all tools from connected MCP servers
- `executeToolCall(toolName, arguments)`: Executes a specific tool with given arguments
- `convertToClaudeTools(mcpTools)`: Converts MCP tools to Claude API format
- `hasAvailableTools()`: Checks if any tools are available

**Example**:
```kotlin
val tools = mcpOrchestrationService.getAvailableTools()
// Returns: [MCPTool(name="get_weather", description="Get weather info", ...)]

val result = mcpOrchestrationService.executeToolCall(
    toolName = "get_weather",
    arguments = buildJsonObject { put("location", "London") }
)
// Returns: MCPToolCallResult(success=true, content=[...])
```

### 2. Enhanced Domain Models

#### AIRequest
**File**: `src/main/kotlin/com/researchai/domain/models/AIRequest.kt`

Added `tools` field to pass available tools to AI providers:
```kotlin
data class AIRequest(
    val messages: List<Message>,
    val model: String,
    val parameters: RequestParameters = RequestParameters(),
    val systemPrompt: String? = null,
    val sessionId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val tools: List<ClaudeTool> = emptyList() // NEW: MCP tools
)
```

#### AIResponse
**File**: `src/main/kotlin/com/researchai/domain/models/AIResponse.kt`

Added `toolUses` field to capture tool requests from AI:
```kotlin
data class AIResponse(
    val id: String,
    val content: String,
    val role: MessageRole = MessageRole.ASSISTANT,
    val model: String,
    val usage: TokenUsage,
    val finishReason: FinishReason,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val estimatedInputTokens: Int = 0,
    val estimatedOutputTokens: Int = 0,
    val toolUses: List<ToolUse> = emptyList() // NEW: Tool use requests
)
```

#### ToolUse
```kotlin
data class ToolUse(
    val id: String,
    val name: String,
    val input: JsonElement
)
```

#### FinishReason
Added `TOOL_USE` enum value:
```kotlin
enum class FinishReason {
    STOP, MAX_TOKENS, CONTENT_FILTER, ERROR, CANCELLED, TOOL_USE
}
```

### 3. Claude Provider Updates

#### ClaudeApiModels.kt
**File**: `src/main/kotlin/com/researchai/data/provider/claude/ClaudeApiModels.kt`

**Changes**:
1. Added `tools` parameter to `ClaudeApiRequest`:
```kotlin
data class ClaudeApiRequest(
    val model: String,
    val messages: List<ClaudeApiMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int,
    // ... other fields
    val tools: List<ClaudeApiTool>? = null // NEW
)
```

2. Created `ClaudeApiTool` for tool definitions:
```kotlin
data class ClaudeApiTool(
    val name: String,
    val description: String,
    @SerialName("input_schema")
    val inputSchema: JsonElement
)
```

3. Added `ClaudeApiMessageContent` sealed class for structured/text content:
```kotlin
sealed class ClaudeApiMessageContent {
    data class Text(val value: String) : ClaudeApiMessageContent()
    data class Structured(val blocks: List<ClaudeContentBlock>) : ClaudeApiMessageContent()
}
```

4. Created `ClaudeContentBlock` for tool_use and tool_result:
```kotlin
sealed class ClaudeContentBlock {
    data class Text(val type: String = "text", val text: String) : ClaudeContentBlock()

    data class ToolUse(
        val type: String = "tool_use",
        val id: String,
        val name: String,
        val input: JsonElement
    ) : ClaudeContentBlock()

    data class ToolResult(
        val type: String = "tool_result",
        @SerialName("tool_use_id")
        val toolUseId: String,
        val content: String
    ) : ClaudeContentBlock()
}
```

5. Custom serializer for handling both string and structured content:
```kotlin
class ClaudeApiMessageContentSerializer : KSerializer<ClaudeApiMessageContent> {
    // Handles both simple strings and structured content blocks
}
```

#### ClaudeMapper.kt
**File**: `src/main/kotlin/com/researchai/data/provider/claude/ClaudeMapper.kt`

**Changes**:
1. Updated request mapping to include tools:
```kotlin
fun toClaudeRequest(
    request: AIRequest,
    config: ProviderConfig.ClaudeConfig,
    formatter: ClaudeMessageFormatter
): ClaudeApiRequest {
    // ... existing code ...

    // NEW: Convert MCP tools to Claude API format
    val claudeTools = if (request.tools.isNotEmpty()) {
        request.tools.map { tool ->
            ClaudeApiTool(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.input_schema
            )
        }
    } else null

    return ClaudeApiRequest(
        // ... other fields ...
        tools = claudeTools
    )
}
```

2. Added `extractToolUses()` method:
```kotlin
fun extractToolUses(response: ClaudeApiResponse): List<ToolUse> {
    return response.content
        .filter { it.type == "tool_use" }
        .mapNotNull { content ->
            if (content.id != null && content.name != null && content.input != null) {
                ToolUse(
                    id = content.id,
                    name = content.name,
                    input = content.input
                )
            } else null
        }
}
```

3. Enhanced response mapping to extract tool use requests:
```kotlin
fun fromClaudeResponse(
    response: ClaudeApiResponse,
    format: ResponseFormat,
    formatter: ClaudeMessageFormatter
): AIResponse {
    // ... existing code ...

    // NEW: Extract tool uses
    val toolUses = extractToolUses(response)

    return AIResponse(
        // ... other fields ...
        toolUses = toolUses
    )
}
```

4. Updated `mapStopReason()` to handle tool_use:
```kotlin
private fun mapStopReason(reason: String?): FinishReason {
    return when (reason) {
        "end_turn" -> FinishReason.STOP
        "max_tokens" -> FinishReason.MAX_TOKENS
        "stop_sequence" -> FinishReason.STOP
        "tool_use" -> FinishReason.TOOL_USE // NEW
        else -> FinishReason.STOP
    }
}
```

### 4. SendMessageUseCase Integration

**File**: `src/main/kotlin/com/researchai/domain/usecase/SendMessageUseCase.kt`

#### Constructor Changes
Added `mcpOrchestrationService` parameter:
```kotlin
class SendMessageUseCase(
    private val providerFactory: AIProviderFactory,
    private val sessionRepository: SessionRepository,
    private val configRepository: ConfigRepository,
    private val assistantManager: AssistantManager,
    private val mcpOrchestrationService: MCPOrchestrationService? = null // NEW
)
```

#### Tool Orchestration Flow
The main invoke method now includes tool orchestration:

```kotlin
suspend operator fun invoke(
    message: String,
    sessionId: String? = null,
    providerId: ProviderType = ProviderType.CLAUDE,
    model: String? = null,
    parameters: RequestParameters = RequestParameters()
): Result<MessageResult> {
    // ... existing setup code ...

    // NEW: Get available MCP tools if orchestration is enabled
    val mcpTools = if (mcpOrchestrationService != null && providerId == ProviderType.CLAUDE) {
        val tools = mcpOrchestrationService.getAvailableTools()
        if (tools.isNotEmpty()) {
            logger.info("MCP orchestration enabled: ${tools.size} tools available")
            mcpOrchestrationService.convertToClaudeTools(tools)
        } else {
            emptyList()
        }
    } else {
        emptyList()
    }

    // Create request with tools
    val request = AIRequest(
        messages = messages,
        model = selectedModel,
        parameters = parameters,
        systemPrompt = systemPrompt,
        sessionId = session.id,
        tools = mcpTools // NEW
    )

    // NEW: Handle tool use loop
    val (finalResponse, toolResults) = handleToolUseLoop(provider, request, mcpTools)

    // ... save response and return ...
}
```

#### Tool Use Loop Implementation
The `handleToolUseLoop()` method implements the core orchestration logic:

```kotlin
private suspend fun handleToolUseLoop(
    provider: AIProvider,
    initialRequest: AIRequest,
    mcpTools: List<ClaudeTool>
): Pair<AIResponse, List<ToolExecutionResult>> {
    var currentRequest = initialRequest
    val toolExecutionResults = mutableListOf<ToolExecutionResult>()
    var iteration = 0

    while (iteration < MAX_TOOL_ITERATIONS) {
        iteration++

        // 1. Send request to AI provider
        val response = provider.sendMessage(currentRequest).getOrThrow()

        // 2. Check if response contains tool uses
        if (response.finishReason != FinishReason.TOOL_USE ||
            mcpOrchestrationService == null ||
            response.toolUses.isEmpty()) {
            // No tool use, return final response
            return Pair(response, toolExecutionResults)
        }

        logger.info("Detected ${response.toolUses.size} tool use(s) in AI response")

        // 3. Execute each tool
        val toolResults = mutableListOf<Pair<String, String>>()
        for (toolUse in response.toolUses) {
            logger.info("Executing tool: ${toolUse.name}")

            val result = mcpOrchestrationService.executeToolCall(
                toolName = toolUse.name,
                arguments = toolUse.input
            )

            val resultText = if (result.success) {
                result.content.joinToString("\n") { it.text ?: "" }
            } else {
                "Error: ${result.error}"
            }

            toolResults.add(Pair(toolUse.id, resultText))
            toolExecutionResults.add(
                ToolExecutionResult(
                    toolName = toolUse.name,
                    success = result.success,
                    result = resultText
                )
            )
        }

        // 4. Prepare next request with tool results
        val updatedMessages = currentRequest.messages.toMutableList()

        val toolResultsText = toolResults.joinToString("\n\n") { (id, result) ->
            "Tool result for $id:\n$result"
        }

        updatedMessages.add(
            Message(
                role = MessageRole.USER,
                content = MessageContent.Text(toolResultsText)
            )
        )

        currentRequest = currentRequest.copy(messages = updatedMessages)
    }

    // Max iterations reached
    logger.warn("Max tool use iterations ($MAX_TOOL_ITERATIONS) reached")
    throw AIError.ConfigurationException("Tool use loop exceeded maximum iterations")
}
```

**Constants**:
```kotlin
companion object {
    private const val MAX_TOOL_ITERATIONS = 5 // Prevent infinite loops
}
```

**Data Classes**:
```kotlin
data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val result: String
)
```

### 5. Dependency Injection

**File**: `src/main/kotlin/com/researchai/di/AppModule.kt`

Added MCP orchestration service to the DI container:

```kotlin
// Import
import com.researchai.domain.mcp.MCPOrchestrationService

class AppModule(/* ... */) {
    // ... existing dependencies ...

    // MCP Server Manager (existing)
    val mcpServerManager: MCPServerManager by lazy {
        val configs = getMCPServers()
        MCPServerManager(configs)
    }

    // NEW: MCP Orchestration Service
    val mcpOrchestrationService: MCPOrchestrationService by lazy {
        MCPOrchestrationService(mcpServerManager)
    }

    // Updated: SendMessageUseCase with orchestration
    val sendMessageUseCase: SendMessageUseCase by lazy {
        SendMessageUseCase(
            providerFactory = providerFactory,
            sessionRepository = sessionRepository,
            configRepository = configRepository,
            assistantManager = assistantManager,
            mcpOrchestrationService = mcpOrchestrationService // NEW
        )
    }
}
```

## How It Works

### Complete Flow Diagram

```
1. User sends message: "What's the weather in London?"
   ↓
2. ChatRoutes receives request
   ↓
3. SendMessageUseCase.invoke()
   ├─ Checks if MCP orchestration is available
   ├─ Retrieves available MCP tools
   │  └─ MCPOrchestrationService.getAvailableTools()
   │     └─ MCPServerManager.listAllTools()
   │        └─ Returns: [get_weather, get_forecast, ...]
   ├─ Converts to Claude format
   │  └─ MCPOrchestrationService.convertToClaudeTools()
   └─ Creates AIRequest with tools
   ↓
4. handleToolUseLoop() starts
   ↓
5. First iteration:
   ├─ Sends request to Claude with tools
   │  └─ ClaudeProvider.sendMessage()
   │     └─ HTTP POST to Claude API with tools array
   ├─ Claude responds: "I'll use get_weather tool"
   │  └─ Response includes: ToolUse(name="get_weather", input={location:"London"})
   ├─ System detects FinishReason.TOOL_USE
   └─ Executes tool
      └─ MCPOrchestrationService.executeToolCall()
         └─ MCPServerManager.callTool()
            └─ MCPClientWrapper.callTool()
               └─ MCP Weather Server returns: "15°C, Cloudy"
   ↓
6. Second iteration:
   ├─ Adds tool result to messages
   ├─ Sends updated request to Claude
   │  └─ Claude processes tool result
   └─ Claude responds: "The weather in London is 15°C and cloudy."
      └─ FinishReason.STOP (no more tool uses)
   ↓
7. Returns final response to user
```

### Sequence Example

**Request 1** (User to AI):
```json
{
  "messages": [{"role": "user", "content": "What's the weather in London?"}],
  "tools": [
    {
      "name": "get_weather",
      "description": "Get current weather for a location",
      "input_schema": {
        "type": "object",
        "properties": {
          "location": {"type": "string"}
        }
      }
    }
  ]
}
```

**Response 1** (AI decides to use tool):
```json
{
  "content": [
    {
      "type": "tool_use",
      "id": "tool_abc123",
      "name": "get_weather",
      "input": {"location": "London"}
    }
  ],
  "stop_reason": "tool_use"
}
```

**System executes tool** → MCP Server returns: `"15°C, Cloudy"`

**Request 2** (System to AI with tool result):
```json
{
  "messages": [
    {"role": "user", "content": "What's the weather in London?"},
    {"role": "assistant", "content": [{"type": "tool_use", ...}]},
    {"role": "user", "content": "Tool result for tool_abc123:\n15°C, Cloudy"}
  ],
  "tools": [...]
}
```

**Response 2** (AI provides final answer):
```json
{
  "content": [
    {
      "type": "text",
      "text": "The current weather in London is 15°C and cloudy."
    }
  ],
  "stop_reason": "end_turn"
}
```

## Key Features

### 1. Automatic Tool Discovery
- System automatically retrieves all available tools from connected MCP servers
- No manual configuration required for individual tools
- Tools are dynamically loaded when servers connect

### 2. Intelligent Tool Selection
- AI model (Claude) decides when to use tools based on user query
- Can use multiple tools in sequence
- Can choose not to use tools if not needed

### 3. Multi-Iteration Support
- Supports complex workflows requiring multiple tool calls
- Maximum 5 iterations to prevent infinite loops
- Each iteration can use multiple tools

### 4. Error Handling
```kotlin
// Tool execution errors are captured and sent back to AI
val result = mcpOrchestrationService.executeToolCall(...)
val resultText = if (result.success) {
    result.content.joinToString("\n") { it.text ?: "" }
} else {
    "Error: ${result.error}" // AI can see the error and respond appropriately
}
```

### 5. Provider-Agnostic Design
While currently implemented for Claude, the architecture supports extension to other providers:
```kotlin
// Can be extended to support other providers
val mcpTools = if (mcpOrchestrationService != null && providerId == ProviderType.CLAUDE) {
    // ... Claude-specific implementation
} else if (providerId == ProviderType.OPENAI) {
    // TODO: OpenAI implementation
} else {
    emptyList()
}
```

### 6. Session Tracking
- Tool executions are tracked per session
- Tool results are included in chat history
- Enables audit trail of tool usage

## Configuration

### MCP Server Configuration
Tools are provided by MCP servers configured in the application. Example:

```kotlin
// In Application.kt or config
val mcpServers = listOf(
    MCPServerConfig(
        id = "weather-server",
        name = "Weather Service",
        enabled = true,
        transport = "stdio",
        command = "node",
        args = listOf("path/to/weather-server.js")
    )
)
```

### Enabling/Disabling Orchestration
Orchestration is automatically enabled when:
1. MCP servers are configured and connected
2. `MCPOrchestrationService` is provided to `SendMessageUseCase`
3. Provider is Claude (currently only Claude is supported)

To disable: Simply don't pass `mcpOrchestrationService` to the use case.

## Testing

### Build
```bash
./gradlew build
```
Build completed successfully with no errors.

### Manual Testing Checklist

1. **Ensure MCP servers are configured**:
   ```bash
   # Check config/mcp.json or environment variables
   cat config/mcp.json
   ```

2. **Start the application**:
   ```bash
   ./gradlew run
   ```

3. **Verify MCP server connections**:
   ```bash
   # Check logs for:
   # "✅ Connected to MCP server: ..."
   # "MCP initialization complete: X/Y servers connected"
   ```

4. **Test tool orchestration**:
   ```bash
   curl -X POST http://localhost:8080/chat \
     -H "Content-Type: application/json" \
     -d '{
       "message": "What is the current weather?",
       "model": "claude-sonnet-4-5-20250929"
     }'
   ```

5. **Check logs for orchestration**:
   ```
   MCP orchestration enabled: X tools available
   Detected Y tool use(s) in AI response
   Executing tool: tool_name
   Tool tool_name executed: success=true
   ```

### Example Log Output
```
2025-01-XX INFO  - SendMessageUseCase: Processing message for provider CLAUDE
2025-01-XX INFO  - MCP orchestration enabled: 3 tools available
2025-01-XX INFO  - Sending request to provider: CLAUDE, model: claude-sonnet-4-5-20250929, tools: 3
2025-01-XX INFO  - Detected 1 tool use(s) in AI response
2025-01-XX INFO  - Executing tool: get_weather
2025-01-XX INFO  - Tool get_weather executed: success=true
2025-01-XX INFO  - Response received from provider: 2847 tokens
2025-01-XX INFO  - Message sent successfully
```

## Limitations and Future Enhancements

### Current Limitations
1. **Claude only**: Currently only Claude provider supports tool orchestration
2. **Simple tool result formatting**: Tool results are sent as plain text
3. **No streaming support**: Tool orchestration doesn't work with streaming responses
4. **Limited to 5 iterations**: Hard-coded limit to prevent infinite loops

### Future Enhancements
1. **OpenAI Support**: Implement tool orchestration for OpenAI's function calling
2. **Parallel tool execution**: Execute independent tools in parallel
3. **Tool result caching**: Cache tool results to avoid redundant calls
4. **Streaming with tools**: Support streaming responses with tool use
5. **Tool approval flow**: Optional user approval before executing tools
6. **Tool usage analytics**: Track which tools are most frequently used
7. **Custom tool limits**: Per-tool iteration limits and timeouts

## API Reference

### MCPOrchestrationService

```kotlin
class MCPOrchestrationService(
    private val mcpServerManager: MCPServerManager
)

// Get all available tools
suspend fun getAvailableTools(): List<MCPTool>

// Execute a tool
suspend fun executeToolCall(
    toolName: String,
    arguments: JsonElement
): MCPToolCallResult

// Convert to Claude format
fun convertToClaudeTools(mcpTools: List<MCPTool>): List<ClaudeTool>

// Check availability
suspend fun hasAvailableTools(): Boolean
```

### SendMessageUseCase

```kotlin
class SendMessageUseCase(
    private val providerFactory: AIProviderFactory,
    private val sessionRepository: SessionRepository,
    private val configRepository: ConfigRepository,
    private val assistantManager: AssistantManager,
    private val mcpOrchestrationService: MCPOrchestrationService? = null
)

suspend operator fun invoke(
    message: String,
    sessionId: String? = null,
    providerId: ProviderType = ProviderType.CLAUDE,
    model: String? = null,
    parameters: RequestParameters = RequestParameters()
): Result<MessageResult>
```

## Troubleshooting

### Tools not being used
1. Check MCP server connection logs
2. Verify tools are returned by `GET /mcp/tools`
3. Ensure provider is Claude
4. Check if tool descriptions are clear enough for AI

### Tool execution failures
1. Check MCP server logs
2. Verify tool arguments match schema
3. Check network connectivity to MCP server
4. Review tool timeout settings

### Infinite loop prevention
If hitting max iterations:
1. Review tool descriptions for clarity
2. Check if tools return useful results
3. Consider if query actually requires that many steps
4. Adjust MAX_TOOL_ITERATIONS if needed

## Files Changed

1. `src/main/kotlin/com/researchai/domain/mcp/MCPOrchestrationService.kt` (NEW)
2. `src/main/kotlin/com/researchai/domain/models/AIRequest.kt` (MODIFIED)
3. `src/main/kotlin/com/researchai/domain/models/AIResponse.kt` (MODIFIED)
4. `src/main/kotlin/com/researchai/data/provider/claude/ClaudeApiModels.kt` (MODIFIED)
5. `src/main/kotlin/com/researchai/data/provider/claude/ClaudeMapper.kt` (MODIFIED)
6. `src/main/kotlin/com/researchai/domain/usecase/SendMessageUseCase.kt` (MODIFIED)
7. `src/main/kotlin/com/researchai/di/AppModule.kt` (MODIFIED)

## Conclusion

The MCP tool orchestration implementation provides a robust, extensible foundation for enabling AI models to interact with external tools. The system automatically discovers available tools, allows the AI to intelligently select and use them, and handles the complete orchestration loop transparently.

This implementation enables powerful new use cases such as:
- Real-time data retrieval (weather, stock prices, news)
- Code execution and analysis
- Database queries
- API integrations
- File system operations
- And any other capability exposed via MCP servers

The architecture is designed to be maintainable, testable, and easily extensible to support additional AI providers and advanced features.
