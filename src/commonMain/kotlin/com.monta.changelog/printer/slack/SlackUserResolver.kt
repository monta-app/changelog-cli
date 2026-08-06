package com.monta.changelog.printer.slack

import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.client
import com.monta.changelog.util.getBodySafe
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resolves Slack user IDs from an email address, used to @mention GitHub
 * contributors who share their public email address with Slack.
 */
internal object SlackUserResolver {
    suspend fun lookupUserIdByEmail(slackToken: String, email: String): String? {
        val response = client.get("https://slack.com/api/users.lookupByEmail") {
            header("Authorization", "Bearer $slackToken")
            url {
                parameters.append("email", email)
            }
        }

        val result = response.getBodySafe<SlackUserLookupResponse>()
        if (result?.ok == true && result.user?.id != null) {
            return result.user.id
        }

        if (result?.error != null && result.error != "users_not_found") {
            DebugLogger.warn("⚠️  Slack user lookup failed for $email: ${result.error}")
        }

        return null
    }
}

@Serializable
internal data class SlackUserLookupResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("error")
    val error: String? = null,
    @SerialName("user")
    val user: SlackUserLookupResult? = null,
)

@Serializable
internal data class SlackUserLookupResult(
    @SerialName("id")
    val id: String? = null,
)
