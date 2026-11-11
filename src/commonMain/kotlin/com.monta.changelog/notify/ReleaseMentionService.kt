package com.monta.changelog.notify

import com.monta.changelog.github.GitHubService
import com.monta.changelog.model.ChangeLog
import com.monta.changelog.printer.slack.SlackBlock
import com.monta.changelog.printer.slack.SlackMessageRequest
import com.monta.changelog.printer.slack.SlackText
import com.monta.changelog.printer.slack.SlackUserResolver
import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.json
import kotlinx.serialization.Serializable

@Serializable
internal data class ReleaseSubscribers(
    val logins: List<String> = emptyList(),
)

/**
 * The lowercased GitHub logins in a subscribers file; empty for a missing or malformed file.
 */
internal fun parseSubscribers(text: String?): Set<String> {
    if (text.isNullOrBlank()) return emptySet()
    return try {
        json.decodeFromString<ReleaseSubscribers>(text).logins.map { it.lowercase() }.toSet()
    } catch (e: Exception) {
        DebugLogger.warn("⚠️  Could not parse the release subscribers file: ${e.message}")
        emptySet()
    }
}

/**
 * A subscriber whose changes are in the release: the PRs they wrote or co-authored, and whether
 * they triggered the release.
 */
internal data class ReleaseMention(
    val login: String,
    val prNumbers: Set<Int>,
    val triggeredRelease: Boolean,
)

/**
 * Picks the subscribers who authored or co-authored a PR in the release, or triggered it.
 * Approving alone doesn't count: the release isn't theirs.
 */
internal fun selectReleaseMentions(
    contributors: Collection<Contributor>,
    triggeredBy: String?,
    subscribers: Set<String>,
): List<ReleaseMention> {
    val trigger = triggeredBy?.removePrefix("@")?.lowercase()
    val mentions = contributors
        .filter { (it.isAuthor || it.isCoAuthor) && it.login?.lowercase() in subscribers }
        .map { ReleaseMention(login = it.login!!, prNumbers = it.prNumbers, triggeredRelease = it.login.lowercase() == trigger) }

    val triggerOnly = trigger != null &&
        trigger in subscribers &&
        mentions.none { it.login.lowercase() == trigger } &&
        !GitHubService.isBotActor(trigger)

    return (mentions + listOfNotNull(if (triggerOnly) ReleaseMention(triggeredBy!!.removePrefix("@"), emptySet(), true) else null))
        .sortedBy { it.login.lowercase() }
}

internal fun buildReleaseMentionLine(
    repoOwner: String,
    repoName: String,
    mention: ReleaseMention,
    prUrls: Map<Int, String?>,
    slackUserId: String,
): String {
    val parts = listOfNotNull(
        buildPrLinks(repoOwner, repoName, mention.prNumbers, prUrls).ifEmpty { null },
        if (mention.triggeredRelease) "(triggered this release)" else null
    )
    return "• <@$slackUserId> ${parts.joinToString(" ")}"
}

internal fun buildReleaseMentionBlocks(changeLog: ChangeLog, lines: List<String>): List<SlackBlock> = listOf(
    SlackBlock(
        type = "section",
        text = SlackText(type = "mrkdwn", text = ":bell: *Your changes are in ${changeLog.tagName}*\n${lines.joinToString("\n")}")
    )
)

/**
 * Tags the people who opted in to release mentions (listed in the subscribers file) as a reply in
 * the changelog's Slack thread. A reply rather than an edit: Slack only notifies on mentions in new
 * messages. Posts nothing when no subscriber has changes in the release.
 */
class ReleaseMentionService(
    private val slackToken: String,
    private val subscribersUrl: String,
    private val gitHubService: GitHubService,
    private val identityResolver: IdentityResolver? = null,
) {

    suspend fun mention(
        changeLog: ChangeLog,
        pullRequests: List<String>,
        slackChannel: String,
        slackThreadTs: String,
    ) {
        val subscribers = parseSubscribers(gitHubService.getRawFile(subscribersUrl))
        if (subscribers.isEmpty()) {
            DebugLogger.debug("No release subscribers, skipping release mentions")
            return
        }

        val (contributors, prUrls) = buildContributors(
            prNumbers = pullRequests.mapNotNull { it.toIntOrNull() },
            getPullRequestDetails = { prNumber ->
                gitHubService.getPullRequestDetails(changeLog.repoOwner, changeLog.repoName, prNumber)
            },
            getPullRequestCommitMessages = { prNumber ->
                gitHubService.getPullRequestCommitMessages(changeLog.repoOwner, changeLog.repoName, prNumber)
            }
        )

        val mentions = selectReleaseMentions(contributors.values, changeLog.triggeredBy, subscribers)
        if (mentions.isEmpty()) {
            DebugLogger.debug("No release subscribers have changes in this release")
            return
        }

        val identities = identityResolver?.resolveGithubIdentities(mentions.map { it.login }).orEmpty()

        // Only a real Slack mention notifies anyone, so a subscriber we can't resolve is left out.
        val lines = mentions.mapNotNull { mention ->
            val slackUserId = resolveSlackUserId(
                identity = findIdentity(mention.login, identities),
                fallbackEmail = { gitHubService.getUser(mention.login)?.email },
                lookupSlackUserId = { email -> SlackUserResolver.lookupUserIdByEmail(slackToken, email) }
            ) ?: run {
                DebugLogger.warn("⚠️  Could not find a Slack user for release subscriber ${mention.login}")
                return@mapNotNull null
            }
            buildReleaseMentionLine(changeLog.repoOwner, changeLog.repoName, mention, prUrls, slackUserId)
        }
        if (lines.isEmpty()) return

        postSlackMessage(
            slackToken = slackToken,
            request = SlackMessageRequest(
                channel = slackChannel,
                threadTs = slackThreadTs,
                text = "Your changes are in ${changeLog.tagName}",
                blocks = buildReleaseMentionBlocks(changeLog, lines)
            )
        )
    }
}
