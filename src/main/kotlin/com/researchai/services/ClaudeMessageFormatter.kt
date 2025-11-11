package com.researchai.services

import com.researchai.models.ResponseFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

class ClaudeMessageFormatter {

    /**
     * Enhances user message based on the specified format
     */
    fun enhanceMessage(userMessage: String, format: ResponseFormat): String {
        return when (format) {
            ResponseFormat.PLAIN_TEXT -> enhanceMessageForPlainText(userMessage)
            ResponseFormat.JSON -> enhanceMessageForJson(userMessage, createJsonTemplate())
            ResponseFormat.XML -> enhanceMessageForXml(userMessage)
        }
    }

    /**
     * Processes response based on the specified format
     */
    fun processResponseByFormat(responseText: String, format: ResponseFormat): String {
        return when (format) {
            ResponseFormat.JSON -> processJsonResponse(responseText)
            ResponseFormat.PLAIN_TEXT -> processPlainTextResponse(responseText)
            ResponseFormat.XML -> processXmlResponse(responseText)
        }
    }

    /**
     * Enhances user message with plain text formatting instructions
     */
    private fun enhanceMessageForPlainText(userMessage: String): String {
        return userMessage
    }

    /**
     * Enhances user message with JSON formatting instructions
     */
    private fun enhanceMessageForJson(userMessage: String, format_template: String): String {
        return """Запрос пользователя: $userMessage

CRITICAL: Respond ONLY with raw JSON. Your response must start with { and end with }

Required JSON format:
$format_template

STRICT RULES:
- NO markdown code blocks (NO ```json or ```)
- NO explanatory text before or after JSON
- NO additional formatting
- Start immediately with {
- End immediately with }
- Use only the specified keys

CORRECT example (your response should look EXACTLY like this):
{
  "title": "Расположение Древнего Рима",
  "source_request": "Где находится Древний Рим",
  "answer": "Древний Рим находился на территории современной Италии, в центральной части Апеннинского полуострова"
}

WRONG examples (DO NOT do this):
```json
{...}
```

or

Here is the JSON:
{...}

Your response must be pure JSON only."""
    }

    /**
     * Enhances user message with XML formatting instructions
     */
    private fun enhanceMessageForXml(userMessage: String): String {
        val template = createXmlTemplate()
        return """Запрос пользователя: $userMessage

CRITICAL: Respond ONLY with valid XML. Your response must start with <?xml and end with </response>

Required XML format:
$template

STRICT RULES:
- NO markdown code blocks (NO ```xml or ```)
- NO explanatory text before or after XML
- NO additional formatting
- Must include XML declaration: <?xml version="1.0" encoding="UTF-8"?>
- Must be well-formed XML with proper opening and closing tags
- Use only the specified tags: <response>, <title>, <source_request>, <answer>

CORRECT example (your response should look EXACTLY like this):
<?xml version="1.0" encoding="UTF-8"?>
<response>
  <title>Расположение Древнего Рима</title>
  <source_request>Где находится Древний Рим</source_request>
  <answer>Древний Рим находился на территории современной Италии, в центральной части Апеннинского полуострова</answer>
</response>

WRONG examples (DO NOT do this):
```xml
<response>...</response>
```

or

Here is the XML:
<response>...</response>

Your response must be pure XML only."""
    }

    /**
     * Creates JSON template for response formatting
     */
    private fun createJsonTemplate(): String {
        return """{
  "title": "здесь краткое описание запроса",
  "source_request": "здесь исходный запрос"
  "answer": "здесь ответ за запрос"
}"""
    }

    /**
     * Creates XML template for response formatting
     */
    private fun createXmlTemplate(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<response>
  <title>здесь краткое описание запроса</title>
  <source_request>здесь исходный запрос</source_request>
  <answer>здесь ответ за запрос</answer>
</response>"""
    }

    /**
     * Processes plain text response
     */
    private fun processPlainTextResponse(responseText: String): String {
        return responseText
    }

    /**
     * Processes JSON response
     */
    private fun processJsonResponse(responseText: String): String {
        val cleaned = cleanJsonResponse(responseText)
        return try {
            val jsonElement = Json.parseToJsonElement(cleaned)
            Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), jsonElement)
        } catch (e: Exception) {
            "Некорректный JSON"
        }
    }

    /**
     * Processes XML response
     */
    private fun processXmlResponse(responseText: String): String {
        val cleaned = cleanXmlResponse(responseText)
        return try {
            // Parse XML to validate it
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()
            val documentBuilder = documentBuilderFactory.newDocumentBuilder()
            val inputSource = InputSource(StringReader(cleaned))
            val document = documentBuilder.parse(inputSource)

            // Remove all whitespace-only text nodes
            removeWhitespaceNodes(document.documentElement)

            // Format XML with pretty print
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")

            val domSource = DOMSource(document)
            val stringWriter = StringWriter()
            val streamResult = StreamResult(stringWriter)
            transformer.transform(domSource, streamResult)

            stringWriter.toString()
        } catch (e: Exception) {
            "Некорректный XML: ${e.message}"
        }
    }

    /**
     * Cleans JSON response by removing markdown code block markers
     */
    private fun cleanJsonResponse(response: String): String {
        return response
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Cleans XML response by removing markdown code block markers
     */
    private fun cleanXmlResponse(response: String): String {
        return response
            .trim()
            .removePrefix("```xml")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Recursively removes whitespace-only text nodes from XML
     */
    private fun removeWhitespaceNodes(node: org.w3c.dom.Node) {
        val nodesToRemove = mutableListOf<org.w3c.dom.Node>()
        val nodeList = node.childNodes

        for (i in 0 until nodeList.length) {
            val child = nodeList.item(i)
            if (child.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                if (child.textContent.isBlank()) {
                    nodesToRemove.add(child)
                }
            } else if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                removeWhitespaceNodes(child)
            }
        }

        nodesToRemove.forEach { node.removeChild(it) }
    }
}
