package com.monta.changelog.notify

import com.monta.changelog.github.GitHubService
import com.monta.changelog.identity.IdentityPerson
import com.monta.changelog.identity.IdentityService
import com.monta.changelog.model.ChangeLog
import com.monta.changelog.printer.slack.SlackBlock
import com.monta.changelog.printer.slack.SlackMessageRequest
import com.monta.changelog.printer.slack.SlackMessageResponse
import com.monta.changelog.printer.slack.SlackUserResolver
import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.client
import com.monta.changelog.util.getBodySafe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.name

/**
 * Finds the Slack account for one contributor, trying the identities most likely to be right
 * first. In order:
 *
 *  1. The Slack id the identity resolver holds for them, which needs no guessing at all.
 *  2. The work email the identity resolver holds for them - this is the address Slack knows, and
 *     it is frequently *not* the one on their GitHub profile (`cr@monta.app` on GitHub versus
 *     `cr@monta.com` in Slack, for instance).
 *  3. Their `Co-authored-by:` trailer email, when they came in as a co-author.
 *  4. Their public GitHub profile email, which most people don't set - tried last because it costs
 *     an extra API call to read.
 *
 * Returns null when none of those reach a Slack account, which the caller reports rather than
 * silently dropping - an author who is never told to monitor is the whole problem we're solving.
 */
internal suspend fun resolveSlackUserId(
    contributor: Contributor,
    identity: IdentityPerson?,
    githubProfileEmail: suspend () -> String?,
    lookupByEmail: suspend (String) -> String?,
): String? {
    identity?.slackUserId?.let { return it }

    // Addresses we already hold, so we spend no GitHub call unless we have to.
    listOfNotNull(identity?.email, contributor.email).distinct().forEach { email ->
        lookupByEmail(email)?.let { return it }
    }

    val profileEmail = githubProfileEmail() ?: return null
    return lookupByEmail(profileEmail)
}

/**
 * Sends each author of a release a direct message telling them their change is deployed and
 * pointing them at the dashboards to watch.
 *
 * This is deliberately separate from [ReleaseNotificationService]: that one announces the release
 * to a channel, where a monitoring ask is easy for everyone to scroll past. A DM lands as a
 * personal to-do, which is the point - it makes post-release monitoring the default rather than
 * something you have to remember to go and do.
 *
 * Only the people who wrote code (authors and co-authors) are messaged. Reviewers are not put on
 * release watch, since the person who wrote the change is the one who can tell whether it is
 * misbehaving.
 */
class AuthorDmService(
    private val slackToken: String,
    private val monitoringUrls: List<MonitoringUrl>,
    private val gitHubService: GitHubService,
    private val identityService: IdentityService?,
) {

    suspend fun notifyAuthors(
        changeLog: ChangeLog,
        pullRequests: List<String>,
    ) {
        if (monitoringUrls.isEmpty()) {
            DebugLogger.warn("⚠️  Author DMs are enabled but no monitoring URLs were given - nothing to ask authors to watch")
            DebugLogger.warn("   → Set CHANGELOG_MONITORING_URLS to enable the author DM")
            return
        }

        val prNumbers = pullRequests.mapNotNull { it.toIntOrNull() }
        if (prNumbers.isEmpty()) {
            DebugLogger.debug("Skipping author DMs - no pull requests in this release")
            return
        }

        val (contributors, prUrls) = collectContributors(changeLog, prNumbers)

        val authors = contributors.values.filter { it.isAuthor || it.isCoAuthor }
        if (authors.isEmpty()) {
            DebugLogger.debug("Skipping author DMs - no human authors found in this release")
            return
        }

        // One batched call for every author, rather than one lookup per person.
        val identities = identityService
            ?.resolveByGithubLogins(authors.mapNotNull { it.login })
            ?: emptyMap()

        val unresolved = mutableListOf<String>()

        authors.forEach { author ->
            val slackUserId = resolveSlackUserId(
                contributor = author,
                identity = author.login?.let { identities[it.lowercase()] },
                githubProfileEmail = { author.login?.let { gitHubService.getUser(it)?.email } },
                lookupByEmail = { email -> SlackUserResolver.lookupUserIdByEmail(slackToken, email) }
            )

            if (slackUserId == null) {
                unresolved.add(author.displayName)
                return@forEach
            }

            sendDm(
                slackUserId = slackUserId,
                changeLog = changeLog,
                prLinks = buildPrLinks(
                    repoOwner = changeLog.repoOwner,
                    repoName = changeLog.repoName,
                    prNumbers = author.prNumbers,
                    prUrls = prUrls
                )
            )
        }

        if (unresolved.isNotEmpty()) {
            DebugLogger.warn("⚠️  Could not reach ${unresolved.size} author(s) in Slack: ${unresolved.joinToString(", ")}")
            DebugLogger.warn("   → Set CHANGELOG_IDENTITY_API_URL so logins can be resolved without a public GitHub email")
        }
    }

    private suspend fun collectContributors(
        changeLog: ChangeLog,
        prNumbers: List<Int>,
    ) = buildContributors(
        prNumbers = prNumbers,
        getPullRequestDetails = { prNumber ->
            gitHubService.getPullRequestDetails(
                repoOwner = changeLog.repoOwner,
                repoName = changeLog.repoName,
                prNumber = prNumber
            )
        },
        getPullRequestCommitMessages = { prNumber ->
            gitHubService.getPullRequestCommitMessages(
                repoOwner = changeLog.repoOwner,
                repoName = changeLog.repoName,
                prNumber = prNumber
            )
        }
    )

    private suspend fun sendDm(
        slackUserId: String,
        changeLog: ChangeLog,
        prLinks: String,
    ) {
        // Posting to a user id opens (or reuses) the DM conversation with them.
        val posted = postMessage(
            channel = slackUserId,
            blocks = buildAuthorDmBlocks(
                changeLog = changeLog,
                monitoringUrls = monitoringUrls,
                prLinks = prLinks
            ),
            fallbackText = buildAuthorDmFallbackText(changeLog)
        )

        if (posted) {
            DebugLogger.debug("Sent release monitoring DM to $slackUserId")
        }
    }

    private suspend fun postMessage(
        channel: String,
        blocks: List<SlackBlock>,
        fallbackText: String,
    ): Boolean {
        val response = client.post("https://slack.com/api/chat.postMessage") {
            header("Authorization", "Bearer $slackToken")
            contentType(ContentType.Application.Json.withParameter("charset", Charsets.UTF_8.name))
            setBody(
                SlackMessageRequest(
                    channel = channel,
                    threadTs = null,
                    text = fallbackText,
                    blocks = blocks
                )
            )
        }

        val result = response.getBodySafe<SlackMessageResponse>()
        if (result?.ok != true) {
            DebugLogger.error("Could not send release monitoring DM to '$channel'")
            return false
        }

        return true
    }
}
