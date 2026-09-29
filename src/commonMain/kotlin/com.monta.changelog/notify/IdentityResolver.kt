package com.monta.changelog.notify

import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.client
import com.monta.changelog.util.getBodySafe
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A person's identity as known to the identity service for a given GitHub login: a Slack
 * user ID when one has been linked (skips the `users.lookupByEmail` hop entirely), and/or
 * an email to use as a fallback lookup key when it hasn't.
 */
data class ResolvedIdentity(
    val slackUserId: String?,
    val email: String?,
)

/**
 * Resolves GitHub logins to organisation identities via an internal cross-system identity
 * API. Far more reliable than a GitHub account's public profile email, which only a small
 * fraction of people expose (and even fewer as their work address).
 *
 * [baseUrl] has no default deliberately - this is a public repository, and the URL is an
 * internal, VPN-only hostname that shouldn't be baked into the source. It's supplied via
 * `CHANGELOG_IDENTITY_RESOLVE_URL` instead (see GenerateChangeLogCommand).
 *
 * The endpoint is unauthenticated but VPN-only; CI reaches it over a Tailscale connection.
 * If that connectivity isn't set up, calls fail (typically via DNS resolution) and this
 * degrades to an empty result rather than throwing, so callers should always have another
 * resolution path to fall back to.
 */
class IdentityResolver(
    private val baseUrl: String,
) {

    /**
     * Resolves as many of the given GitHub logins as the identity service knows about.
     * Batches at 100 logins per call (the API's documented limit). Result keys are
     * lowercased logins; unresolved logins are simply absent, never null-valued.
     */
    suspend fun resolveGithubIdentities(logins: List<String>): Map<String, ResolvedIdentity> {
        val distinctLogins = logins.filter { it.isNotBlank() }.distinct()
        if (distinctLogins.isEmpty()) {
            return emptyMap()
        }

        return distinctLogins
            .chunked(100)
            .fold(emptyMap()) { resolved, batch -> resolved + resolveBatch(batch) }
    }

    // Best-effort lookup that runs before the primary changelog output, so it must never be
    // able to hang the job (e.g. a host that resolves but silently drops packets).
    private suspend fun resolveBatch(logins: List<String>): Map<String, ResolvedIdentity> = try {
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) { fetchBatch(logins) } ?: run {
            DebugLogger.warn("⚠️  Identity resolve API did not respond within ${REQUEST_TIMEOUT_MS / 1000}s")
            emptyMap()
        }
    } catch (e: Exception) {
        DebugLogger.warn("⚠️  Could not reach the identity resolve API (likely no VPN/Tailscale connectivity): ${e.message}")
        emptyMap()
    }

    private suspend fun fetchBatch(logins: List<String>): Map<String, ResolvedIdentity> {
        val response = client.get(baseUrl) {
            url {
                parameters.append("github", logins.joinToString(","))
            }
        }

        return if (response.status.isSuccess()) {
            extractIdentities(response.getBodySafe<IdentityResolveResponse>())
        } else {
            DebugLogger.warn("⚠️  Identity resolve API returned HTTP ${response.status.value}")
            emptyMap()
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
}

// Slack user IDs are `U`/`W` followed by uppercase alphanumerics. Anything else from the API
// is rejected rather than interpolated into a `<@...>` mention.
private val slackUserIdPattern = Regex("^[UW][A-Z0-9]+$")

/**
 * Pulls `login -> ResolvedIdentity` pairs out of a resolve response. Values are validated
 * at this boundary: a Slack user ID must look like one, and blank values count as absent.
 * Unresolved (null) entries and entries left with neither a valid Slack ID nor an email
 * are dropped. Pure so the parsing/shape logic is testable without a live API call.
 */
internal fun extractIdentities(response: IdentityResolveResponse?): Map<String, ResolvedIdentity> = response
    ?.github
    .orEmpty()
    .mapNotNull { (login, identity) ->
        val slackUserId = identity?.slackUserId?.trim()?.takeIf { slackUserIdPattern.matches(it) }
        val email = identity?.email?.trim()?.takeIf { it.isNotEmpty() }

        if (slackUserId == null && email == null) {
            null
        } else {
            login.lowercase() to ResolvedIdentity(slackUserId = slackUserId, email = email)
        }
    }
    .toMap()

@Serializable
internal data class IdentityResolveResponse(
    @SerialName("github")
    val github: Map<String, GithubIdentity?>? = null,
)

@Serializable
internal data class GithubIdentity(
    @SerialName("displayName")
    val displayName: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("slackUserId")
    val slackUserId: String? = null,
)
