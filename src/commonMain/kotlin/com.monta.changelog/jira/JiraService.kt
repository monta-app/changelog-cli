package com.monta.changelog.jira

import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.client
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
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
            DebugLogger.debug("JIRA ticket $ticketKey does not exist or is not accessible (status: ${response.status})")
        }
        exists
    } catch (e: Exception) {
        DebugLogger.debug("Failed to validate JIRA ticket $ticketKey: ${e.message}")
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
}

@Serializable
internal data class JiraIssueResponse(
    val key: String,
)
