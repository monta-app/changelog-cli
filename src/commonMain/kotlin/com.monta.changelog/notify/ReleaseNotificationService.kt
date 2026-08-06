package com.monta.changelog.notify

import com.monta.changelog.github.GitHubService
import com.monta.changelog.model.ChangeLog
import com.monta.changelog.printer.slack.SlackBlock
import com.monta.changelog.printer.slack.SlackMessageRequest
import com.monta.changelog.printer.slack.SlackMessageResponse
import com.monta.changelog.printer.slack.SlackText
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
 * A single person contributing to the release, tracked by the most specific identity
 * we have for them (GitHub login, otherwise email, otherwise their commit trailer name).
 */
internal data class Contributor(
    val login: String?,
    val email: String?,
    val displayName: String,
    val prNumbers: MutableSet<Int> = mutableSetOf(),
    var isAuthor: Boolean = false,
    var isApprover: Boolean = false,
    var isCoAuthor: Boolean = false,
) {
    val key: String get() = (login ?: email ?: displayName).lowercase()
}

/**
 * Adds or updates a contributor for a given PR, skipping bot accounts (matched on GitHub
 * login or the commit trailer display name, since bot co-authors rarely have a resolvable login).
 * Returns silently if there's no usable identity at all.
 */
internal fun addContributor(
    contributors: MutableMap<String, Contributor>,
    login: String? = null,
    email: String? = null,
    displayName: String? = null,
    prNumber: Int,
    markRole: (Contributor) -> Unit,
) {
    val resolvedDisplayName = displayName ?: login ?: email ?: return
    if (GitHubService.isBotActor(login) || GitHubService.isBotActor(resolvedDisplayName)) {
        return
    }

    val contributor = Contributor(login = login, email = email, displayName = resolvedDisplayName)
    val existing = contributors.getOrPut(contributor.key) { contributor }
    existing.prNumbers.add(prNumber)
    markRole(existing)
}

/**
 * Orders contributors so that people who wrote code (authors/co-authors) come before
 * people who only approved, and alphabetically within each group.
 */
internal fun sortContributors(contributors: Collection<Contributor>): List<Contributor> = contributors
    .sortedWith(
        compareBy(
            { contributor -> !contributor.isAuthor && !contributor.isCoAuthor && contributor.isApprover },
            { contributor -> contributor.displayName.lowercase() }
        )
    )

/**
 * Builds the "(co-author, approver)" style suffix for a contributor's secondary roles.
 * Authors get no suffix - being the author is the default, unqualified role.
 */
internal fun buildRoleSuffix(contributor: Contributor): String {
    val roles = buildList {
        if (contributor.isCoAuthor) add("co-author")
        if (contributor.isApprover) add("approver")
    }
    return if (!contributor.isAuthor && roles.isNotEmpty()) " (${roles.joinToString(", ")})" else ""
}

/**
 * Builds the space-separated list of linked PR numbers a contributor participated in.
 */
internal fun buildPrLinks(
    repoOwner: String,
    repoName: String,
    prNumbers: Set<Int>,
    prUrls: Map<Int, String?>,
): String = prNumbers.sorted().joinToString(" ") { prNumber ->
    val url = prUrls[prNumber] ?: "https://github.com/$repoOwner/$repoName/pull/$prNumber"
    "<$url|#$prNumber>"
}

/**
 * Formats a resolved Slack mention, falling back to a GitHub profile link when no Slack
 * account was found, and to a plain display name when there's no GitHub login either.
 */
internal fun formatMention(slackUserId: String?, login: String?, displayName: String): String = when {
    slackUserId != null -> "<@$slackUserId>"
    login != null -> "<https://github.com/$login|@$login>"
    else -> displayName
}

/**
 * Builds the "• <mention> (role) <pr links>" line for a single contributor.
 */
internal fun buildMentionLine(
    repoOwner: String,
    repoName: String,
    contributor: Contributor,
    prUrls: Map<Int, String?>,
    mention: String,
): String {
    val roleSuffix = buildRoleSuffix(contributor)
    val links = buildPrLinks(repoOwner, repoName, contributor.prNumbers, prUrls)
    return "• $mention$roleSuffix $links"
}

/**
 * Walks every pull request in the release, collecting authors, approvers and
 * `Co-authored-by:` trailers into a single contributor map (bots excluded). The fetchers
 * are injected so this orchestration can be unit tested without hitting the GitHub API.
 */
internal suspend fun buildContributors(
    prNumbers: List<Int>,
    getPullRequestDetails: suspend (Int) -> GitHubService.PullRequestDetails,
    getPullRequestCommitMessages: suspend (Int) -> List<String>,
): Pair<Map<String, Contributor>, Map<Int, String?>> {
    val contributors = mutableMapOf<String, Contributor>()
    val prUrls = mutableMapOf<Int, String?>()

    prNumbers.forEach { prNumber ->
        val details = getPullRequestDetails(prNumber)
        prUrls[prNumber] = details.htmlUrl

        details.author?.let { author ->
            addContributor(contributors, login = author, prNumber = prNumber) { it.isAuthor = true }
        }
        details.approvers.forEach { approver ->
            addContributor(contributors, login = approver, prNumber = prNumber) { it.isApprover = true }
        }

        val commitMessages = getPullRequestCommitMessages(prNumber)
        CoAuthorExtractor.extract(commitMessages).forEach { coAuthor ->
            addContributor(
                contributors,
                login = coAuthor.login,
                email = coAuthor.email,
                displayName = coAuthor.name,
                prNumber = prNumber
            ) { it.isCoAuthor = true }
        }
    }

    return contributors to prUrls
}

/**
 * True when there's nothing worth posting a release notification for.
 */
internal fun shouldSkipNotification(
    prNumbers: List<Int>,
    monitoringUrls: List<MonitoringUrl>,
): Boolean = prNumbers.isEmpty() && monitoringUrls.isEmpty()

/**
 * Builds the Slack blocks for the release notification: release link, dashboards to
 * monitor and the contributor list, each section omitted when there's nothing to show.
 */
internal fun buildReleaseNotificationBlocks(
    changeLog: ChangeLog,
    monitoringUrls: List<MonitoringUrl>,
    mentionLines: List<String>,
): List<SlackBlock> = buildList {
    val releaseUrl = changeLog.githubReleaseUrl
        ?: changeLog.repositoryUrl?.let { "$it/releases/tag/${changeLog.tagName}" }
    val releaseText = if (releaseUrl != null) {
        "*Release <$releaseUrl|${changeLog.tagName}>* is out :rocket:"
    } else {
        "*Release ${changeLog.tagName}* is out :rocket:"
    }
    add(sectionBlock(releaseText))

    if (monitoringUrls.isNotEmpty()) {
        val dashboardLines = monitoringUrls.joinToString("\n") { "• <${it.url}|${it.label}>" }
        add(sectionBlock("*Dashboards to monitor:*\n$dashboardLines"))
    }

    if (mentionLines.isNotEmpty()) {
        add(sectionBlock("*Contributors:*\n${mentionLines.joinToString("\n")}"))
    }
}

private fun sectionBlock(text: String) = SlackBlock(
    type = "section",
    text = SlackText(type = "mrkdwn", text = text)
)

/**
 * Posts a standalone Slack message announcing a release, listing dashboards to monitor
 * and tagging the people who authored, co-authored or approved the pull requests included in it.
 */
class ReleaseNotificationService(
    private val slackToken: String,
    private val slackChannel: String,
    private val monitoringUrls: List<MonitoringUrl>,
    private val gitHubService: GitHubService,
) {

    suspend fun notify(
        changeLog: ChangeLog,
        pullRequests: List<String>,
    ) {
        val prNumbers = pullRequests.mapNotNull { it.toIntOrNull() }

        if (shouldSkipNotification(prNumbers, monitoringUrls)) {
            DebugLogger.debug("Skipping release notification - no pull requests or monitoring URLs to report")
            return
        }

        val (contributors, prUrls) = buildContributors(
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

        val mentionLines = sortContributors(contributors.values).map { contributor ->
            buildMentionLine(
                repoOwner = changeLog.repoOwner,
                repoName = changeLog.repoName,
                contributor = contributor,
                prUrls = prUrls,
                mention = resolveMention(contributor)
            )
        }

        postMessage(
            blocks = buildReleaseNotificationBlocks(changeLog, monitoringUrls, mentionLines),
            fallbackText = changeLog.title
        )
    }

    /**
     * Resolves a contributor to a real Slack mention by looking up their public GitHub
     * email (or commit trailer email) in Slack.
     */
    private suspend fun resolveMention(contributor: Contributor): String {
        val email = contributor.email ?: contributor.login?.let { gitHubService.getUser(it)?.email }
        val slackUserId = email?.let { SlackUserResolver.lookupUserIdByEmail(slackToken, it) }
        return formatMention(slackUserId = slackUserId, login = contributor.login, displayName = contributor.displayName)
    }

    private suspend fun postMessage(blocks: List<SlackBlock>, fallbackText: String) {
        val response = client.post("https://slack.com/api/chat.postMessage") {
            header("Authorization", "Bearer $slackToken")
            contentType(ContentType.Application.Json.withParameter("charset", Charsets.UTF_8.name))
            setBody(
                SlackMessageRequest(
                    channel = slackChannel,
                    threadTs = null,
                    text = fallbackText,
                    blocks = blocks
                )
            )
        }

        val result = response.getBodySafe<SlackMessageResponse>()
        if (result?.ok != true) {
            DebugLogger.error("Could not post release notification to slack channel '$slackChannel'")
        }
    }
}
