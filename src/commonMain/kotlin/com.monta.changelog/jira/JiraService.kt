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
    internal suspend fun commentOnTicket(
        ticketKey: String,
        body: AdfDocument,
    ): Boolean = try {
        val url = "https://$jiraAppName.atlassian.net/rest/api/3/issue/$ticketKey/comment"
        val credentials = "$jiraEmail:$jiraToken"
        val encodedCredentials = credentials.encodeBase64()

        val request = JiraCommentRequest(body = body)

        val response = client.post(url) {
            header("Authorization", "Basic $encodedCredentials")
            header("Accept", "application/json")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        handleCommentResponse(response, ticketKey)
    } catch (e: Exception) {
        DebugLogger.warn("⚠️  Exception commenting on JIRA ticket $ticketKey: ${e.message}")
        false
    }

    /**
     * Handles the response from JIRA comment API and logs appropriate messages.
     */
    private suspend fun handleCommentResponse(response: io.ktor.client.statement.HttpResponse, ticketKey: String): Boolean {
        val success = response.status == HttpStatusCode.Created
        if (success) {
            DebugLogger.info("✓ Successfully commented on JIRA ticket $ticketKey")
            return true
        }

        DebugLogger.warn("⚠️  Failed to comment on JIRA ticket $ticketKey: HTTP ${response.status.value}")
        logErrorResponseBody(response)
        return false
    }

    /**
     * Attempts to log the error response body from JIRA API.
     */
    private suspend fun logErrorResponseBody(response: io.ktor.client.statement.HttpResponse) {
        try {
            val errorBody = response.bodyAsText()
            if (errorBody.isNotEmpty()) {
                DebugLogger.warn("   → Response: ${errorBody.take(200)}")
            }
        } catch (e: Exception) {
            DebugLogger.debug("Could not read error response body: ${e.message}")
        }
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
