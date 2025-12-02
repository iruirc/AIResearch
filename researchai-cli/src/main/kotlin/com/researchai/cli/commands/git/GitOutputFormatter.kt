package com.researchai.cli.commands.git

import kotlinx.serialization.json.*

/**
 * Interface for formatting GitHub MCP tool output
 */
interface GitOutputFormatter {
    fun format(content: String): String
}

/**
 * Utility to extract actual text from MCP content
 * Handles format: TextContent(text=..., annotations=null)
 */
object ContentExtractor {
    private val textContentPattern = """TextContent\(text=(.*), annotations=.*\)""".toRegex(RegexOption.DOT_MATCHES_ALL)

    fun extractText(content: String): String {
        val match = textContentPattern.find(content)
        return match?.groupValues?.get(1) ?: content
    }
}

/**
 * Default formatter - pretty prints JSON or returns text as-is
 */
object DefaultFormatter : GitOutputFormatter {
    private val prettyJson = Json { prettyPrint = true }

    override fun format(content: String): String {
        val text = ContentExtractor.extractText(content)
        return try {
            val element = Json.parseToJsonElement(text)
            prettyJson.encodeToString(JsonElement.serializer(), element)
        } catch (e: Exception) {
            text
        }
    }
}

/**
 * Formatter for list_branches - shows only branch names, one per line
 */
object ListBranchesFormatter : GitOutputFormatter {
    override fun format(content: String): String {
        val text = ContentExtractor.extractText(content)
        return try {
            val json = Json.parseToJsonElement(text)
            val branches = json.jsonArray

            branches.mapNotNull { branch ->
                branch.jsonObject["name"]?.jsonPrimitive?.content
            }.joinToString("\n")
        } catch (e: Exception) {
            DefaultFormatter.format(content)
        }
    }
}

/**
 * Registry of formatters for specific tools
 */
object GitOutputFormatterRegistry {
    private val formatters: Map<String, GitOutputFormatter> = mapOf(
        "list_branches" to ListBranchesFormatter
    )

    fun getFormatter(toolName: String): GitOutputFormatter {
        return formatters[toolName] ?: DefaultFormatter
    }
}
