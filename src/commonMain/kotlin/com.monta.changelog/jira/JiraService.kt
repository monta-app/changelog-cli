package com.monta.changelog.jira

import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.client
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.encodeBase64
import kotlinx.serialization.Serializable

class JiraService(
    private val jiraAppName: String,
    private val jiraEmail: String,
    private val jiraToken: String,
) {

    /**
     * Validates if a JIRA ticket exists by making an API call to JIRA.
     * Returns true if the ticket exists, false otherwise.
     */
    suspend fun ticketExists(ticketKey: String): Boolean = try {
        val url = "https://$jiraAppName.atlassian.net/rest/api/3/issue/$ticketKey"
        val credentials = "$jiraEmail:$jiraToken"
        val encodedCredentials = credentials.encodeBase64()

        val response = client.get(url) {
            header("Authorization", "Basic $encodedCredentials")
            header("Accept", "application/json")
        }

        val exists = response.status == HttpStatusCode.OK
        if (!exists) {
            DebugLogger.warn("⚠️  JIRA ticket $ticketKey validation failed: HTTP ${response.status.value}")
            try {
                val errorBody = response.bodyAsText()
                if (errorBody.isNotEmpty()) {
                    DebugLogger.warn("   → Response: ${errorBody.take(200)}")
                }
            } catch (e: Exception) {
                DebugLogger.debug("Could not read error response body: ${e.message}")
            }
        }
        exists
    } catch (e: Exception) {
        DebugLogger.warn("⚠️  Exception validating JIRA ticket $ticketKey: ${e.message}")
        false
    }

    /**
     * Validates multiple JIRA tickets and returns only those that exist.
     * This method processes tickets in batches to avoid overwhelming the API.
     */
    suspend fun filterValidTickets(ticketKeys: List<String>): List<String> {
        if (ticketKeys.isEmpty()) {
            return emptyList()
        }

        val totalCount = ticketKeys.size
        DebugLogger.info("Validating $totalCount JIRA ticket(s)...")

        val validTickets = mutableListOf<String>()
        var processedCount = 0

        ticketKeys.forEach { ticketKey ->
            if (ticketExists(ticketKey)) {
                validTickets.add(ticketKey)
            }

            processedCount++

            // Log progress every 10 tickets or at the end
            if (processedCount % 10 == 0 || processedCount == totalCount) {
                DebugLogger.info("Validated $processedCount/$totalCount JIRA tickets...")
            }
        }

        val invalidCount = totalCount - validTickets.size
        if (invalidCount > 0) {
            DebugLogger.info("Filtered out $invalidCount invalid JIRA ticket(s)")
        } else {
            DebugLogger.info("All JIRA tickets are valid")
        }

        return validTickets
    }

    /**
     * Adds a comment to a JIRA ticket.
     * Returns true if the comment was successfully added, false otherwise.
     */
    suspend fun commentOnTicket(
        ticketKey: String,
        commentBody: String,
    ): Boolean = try {
        val url = "https://$jiraAppName.atlassian.net/rest/api/3/issue/$ticketKey/comment"
        val credentials = "$jiraEmail:$jiraToken"
        val encodedCredentials = credentials.encodeBase64()

        val adfDocument = commentBody.toAdfDocument()
        val request = JiraCommentRequest(body = adfDocument)

        val response = client.post(url) {
            header("Authorization", "Basic $encodedCredentials")
            header("Accept", "application/json")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val success = response.status == HttpStatusCode.Created
        if (success) {
            DebugLogger.info("✓ Successfully commented on JIRA ticket $ticketKey")
        } else {
            DebugLogger.warn("⚠️  Failed to comment on JIRA ticket $ticketKey: HTTP ${response.status.value}")
            try {
                val errorBody = response.bodyAsText()
                if (errorBody.isNotEmpty()) {
                    DebugLogger.warn("   → Response: ${errorBody.take(200)}")
                }
            } catch (e: Exception) {
                DebugLogger.debug("Could not read error response body: ${e.message}")
            }
        }
        success
    } catch (e: Exception) {
        DebugLogger.warn("⚠️  Exception commenting on JIRA ticket $ticketKey: ${e.message}")
        false
    }

    /**
     * Converts plain text with markdown-style links to JIRA's Atlassian Document Format (ADF).
     * Each non-empty line becomes a separate paragraph.
     * Markdown links [text](url) are converted to proper ADF links.
     * Markdown headings (##, ###) are converted to ADF headings.
     * Lines with "---" are converted to horizontal rules.
     * Empty lines are skipped as ADF doesn't support empty paragraphs.
     */
    private fun String.toAdfDocument(): AdfDocument {
        val lines = this.split("\n")
        val nodes = lines
            .filter { it.isNotEmpty() }
            .map { line ->
                when {
                    line.trim() == "---" -> AdfNode(type = "rule")
                    line.startsWith("### ") -> AdfNode(
                        type = "heading",
                        attrs = AdfNodeAttrs(level = 3),
                        content = parseLineWithLinks(line.substring(4))
                    )
                    line.startsWith("## ") -> AdfNode(
                        type = "heading",
                        attrs = AdfNodeAttrs(level = 2),
                        content = parseLineWithLinks(line.substring(3))
                    )
                    line.startsWith("# ") -> AdfNode(
                        type = "heading",
                        attrs = AdfNodeAttrs(level = 1),
                        content = parseLineWithLinks(line.substring(2))
                    )
                    else -> AdfNode(
                        type = "paragraph",
                        content = parseLineWithLinks(line)
                    )
                }
            }

        return AdfDocument(
            type = "doc",
            version = 1,
            content = nodes
        )
    }

    /**
     * Parses a line of text and converts markdown-style links [text](url) to ADF text with link marks.
     */
    private fun parseLineWithLinks(line: String): List<AdfText> {
        val linkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
        val result = mutableListOf<AdfText>()
        var lastIndex = 0

        linkRegex.findAll(line).forEach { match ->
            // Add text before the link
            if (match.range.first > lastIndex) {
                val beforeText = line.substring(lastIndex, match.range.first)
                result.add(AdfText(type = "text", text = beforeText))
            }

            // Add the link
            val linkText = match.groupValues[1]
            val linkUrl = match.groupValues[2]
            result.add(
                AdfText(
                    type = "text",
                    text = linkText,
                    marks = listOf(
                        AdfMark(
                            type = "link",
                            attrs = AdfMarkAttrs(href = linkUrl)
                        )
                    )
                )
            )

            lastIndex = match.range.last + 1
        }

        // Add remaining text after the last link
        if (lastIndex < line.length) {
            result.add(AdfText(type = "text", text = line.substring(lastIndex)))
        }

        // If no links were found, return the whole line as text
        if (result.isEmpty()) {
            result.add(AdfText(type = "text", text = line))
        }

        return result
    }
}

@Serializable
internal data class JiraIssueResponse(
    val key: String,
)

/**
 * JIRA API comment request using Atlassian Document Format (ADF).
 */
@Serializable
internal data class JiraCommentRequest(
    val body: AdfDocument,
)

/**
 * Atlassian Document Format (ADF) root document.
 */
@Serializable
internal data class AdfDocument(
    val type: String = "doc",
    val version: Int = 1,
    val content: List<AdfNode>,
)

/**
 * ADF node representing a structural element (paragraph, heading, etc.).
 */
@Serializable
internal data class AdfNode(
    val type: String,
    val content: List<AdfText>? = null,
    val attrs: AdfNodeAttrs? = null,
)

/**
 * Attributes for ADF nodes (e.g., level for headings).
 */
@Serializable
internal data class AdfNodeAttrs(
    val level: Int? = null,
)

/**
 * ADF text content within a node.
 */
@Serializable
internal data class AdfText(
    val type: String = "text",
    val text: String,
    val marks: List<AdfMark>? = null,
)

/**
 * ADF mark for text formatting (links, bold, italic, etc.).
 */
@Serializable
internal data class AdfMark(
    val type: String,
    val attrs: AdfMarkAttrs? = null,
)

/**
 * Attributes for ADF marks (e.g., href for links).
 */
@Serializable
internal data class AdfMarkAttrs(
    val href: String? = null,
)
