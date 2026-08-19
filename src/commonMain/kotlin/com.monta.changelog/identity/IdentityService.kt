package com.monta.changelog.identity

import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.client
import com.monta.changelog.util.getBodySafe
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A person as returned by project-tracker's identity resolver.
 */
@Serializable
data class IdentityPerson(
    @SerialName("displayName")
    val displayName: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("isBot")
    val isBot: Boolean = false,
    @SerialName("githubLogin")
    val githubLogin: String? = null,
    @SerialName("slackUserId")
    val slackUserId: String? = null,
)

@Serializable
internal data class IdentityResolveResponse(
    @SerialName("github")
    val github: Map<String, IdentityPerson?> = emptyMap(),
)

/**
 * Client for project-tracker's cross-system identity resolver, which maps a GitHub login to the
 * person's work email and Slack id.
 *
 * We need it because a GitHub login on its own is not enough to reach someone in Slack: public
 * GitHub profile emails are set on only a small minority of our org members, and the commit
 * author email is just as often a `users.noreply.github.com` address or a personal one that Slack
 * has never heard of. The resolver is the only place that knows, say, that `MithrandirDK` is
 * `jake@monta.com`.
 *
 * The endpoint is VPN-only, so it is entirely optional: any failure - unset URL, a runner outside
 * the tailnet, a bad response - is treated as "nobody resolved", and callers fall back to whatever
 * they can derive from GitHub on their own.
 */
class IdentityService(
    baseUrl: String,
) {

    private val resolveUrl = "${baseUrl.trimEnd('/')}/api/identity/resolve"

    /**
     * Resolves GitHub logins to people, keyed by lowercased login. Logins the resolver doesn't
     * know are absent from the result rather than mapped to null.
     */
    suspend fun resolveByGithubLogins(logins: List<String>): Map<String, IdentityPerson> {
        val distinctLogins = logins.filter { it.isNotBlank() }.distinct()
        if (distinctLogins.isEmpty()) {
            return emptyMap()
        }

        return distinctLogins
            .chunked(MAX_HANDLES_PER_REQUEST)
            .flatMap { chunk -> resolveChunk(chunk).entries }
            .associate { entry -> entry.key to entry.value }
    }

    private suspend fun resolveChunk(chunk: List<String>): Map<String, IdentityPerson> {
        val response = try {
            client.get(resolveUrl) {
                url {
                    parameters.append("github", chunk.joinToString(","))
                }
            }
        } catch (throwable: Throwable) {
            // Expected whenever the job runs outside the tailnet - not worth failing the release over.
            DebugLogger.warn("⚠️  Identity resolver unreachable at $resolveUrl: ${throwable.message}")
            DebugLogger.warn("   → Falling back to GitHub-derived emails for Slack lookups")
            return emptyMap()
        }

        val body = response.getBodySafe<IdentityResolveResponse>() ?: return emptyMap()

        // The resolver echoes back the caller's spelling of each handle; normalise so lookups
        // don't depend on how GitHub happened to capitalise the login.
        return body.github.mapNotNull { (handle, person) ->
            person?.let { handle.lowercase() to it }
        }.toMap()
    }

    private companion object {
        /**
         * The resolver caps a request at 100 handles across all lookup types.
         */
        const val MAX_HANDLES_PER_REQUEST = 100
    }
}
