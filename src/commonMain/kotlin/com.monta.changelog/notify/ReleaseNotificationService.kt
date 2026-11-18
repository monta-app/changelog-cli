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
 * The roles a contributor had in one specific pull request.
 */
internal data class PrRoles(
    var isAuthor: Boolean = false,
    var isApprover: Boolean = false,
    var isCoAuthor: Boolean = false,
)

/**
 * A single person contributing to the release, tracked by the most specific identity
 * we have for them (GitHub login, otherwise email, otherwise their commit trailer name).
 */
internal data class Contributor(
    val login: String?,
    val email: String?,
    val displayName: String,
    val prNumbers: MutableSet<Int> = mutableSetOf(),
    val prRoles: MutableMap<Int, PrRoles> = mutableMapOf(),
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

    // Apply the same role to a throwaway contributor to learn which role this call grants, so it
    // can be recorded against just this PR.
    val granted = Contributor(login = login, email = email, displayName = resolvedDisplayName).also(markRole)
    val prRoles = existing.prRoles.getOrPut(prNumber) { PrRoles() }
    prRoles.isAuthor = prRoles.isAuthor || granted.isAuthor
    prRoles.isApprover = prRoles.isApprover || granted.isApprover
    prRoles.isCoAuthor = prRoles.isCoAuthor || granted.isCoAuthor
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
 * Builds the "(co-author, approver)" style suffix for the secondary roles someone had in a
 * single pull request. Authors get no suffix - being the author is the default, unqualified role.
 */
internal fun buildRoleSuffix(roles: PrRoles): String {
    val secondary = buildList {
        if (roles.isCoAuthor) add("co-author")
        if (roles.isApprover) add("approver")
    }
    return if (!roles.isAuthor && secondary.isNotEmpty()) " (${secondary.joinToString(", ")})" else ""
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
 * Builds the "• title (#123)" line for a pull request, linking the number to the PR.
 */
internal fun buildPullRequestLine(
    repoOwner: String,
    repoName: String,
    prNumber: Int,
    title: String?,
    prUrls: Map<Int, String?>,
): String {
    val link = buildPrLinks(repoOwner, repoName, setOf(prNumber), prUrls)
    return if (title.isNullOrBlank()) "• $link" else "• ${escapeSlackText(title.trim())} ($link)"
}

/**
 * Builds the indented "◦ <mention> (role)" sub-bullet for a contributor of a pull request, with the role they had in that PR.
 */
internal fun buildContributorLine(roles: PrRoles, mention: String): String = "    ◦ $mention${buildRoleSuffix(roles)}"

/**
 * Escapes the characters Slack treats as control characters in mrkdwn text.
 */
internal fun escapeSlackText(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/**
 * Builds one entry per pull request - the PR line followed by its contributors as sub-bullets -
 * in PR number order. [mentions] maps a contributor key to its resolved Slack mention.
 */
internal fun buildPullRequestLines(
    repoOwner: String,
    repoName: String,
    prNumbers: List<Int>,
    prTitles: Map<Int, String?>,
    prUrls: Map<Int, String?>,
    contributors: Collection<Contributor>,
    mentions: Map<String, String>,
): List<String> = prNumbers.distinct().sorted().map { prNumber ->
    val contributorLines = contributors
        .mapNotNull { contributor -> contributor.prRoles[prNumber]?.let { contributor to it } }
        .sortedWith(
            compareBy(
                { (_, roles) -> !roles.isAuthor && !roles.isCoAuthor && roles.isApprover },
                { (contributor, _) -> contributor.displayName.lowercase() }
            )
        )
        .map { (contributor, roles) -> buildContributorLine(roles, mentions[contributor.key] ?: contributor.displayName) }
    (listOf(buildPullRequestLine(repoOwner, repoName, prNumber, prTitles[prNumber], prUrls)) + contributorLines)
        .joinToString("\n")
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
    prTitles: MutableMap<Int, String?> = mutableMapOf(),
): Pair<Map<String, Contributor>, Map<Int, String?>> {
    val contributors = mutableMapOf<String, Contributor>()
    val prUrls = mutableMapOf<Int, String?>()

    prNumbers.forEach { prNumber ->
        val details = getPullRequestDetails(prNumber)
        prUrls[prNumber] = details.htmlUrl
        prTitles[prNumber] = details.title

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
    pullRequestLines: List<String>,
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

    if (pullRequestLines.isNotEmpty()) {
        add(sectionBlock("*Pull requests:*\n${pullRequestLines.joinToString("\n")}"))
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
    private val identityResolver: IdentityResolver? = null,
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

        val prTitles = mutableMapOf<Int, String?>()
        val (contributors, prUrls) = buildContributors(
            prNumbers = prNumbers,
            prTitles = prTitles,
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

        // One batched lookup for every contributor's GitHub login, ahead of the
        // per-contributor email resolution/lookup in resolveMention. Skipped entirely
        // when no identity-resolve URL was configured.
        val identities = identityResolver?.resolveGithubIdentities(contributors.values.mapNotNull { it.login }).orEmpty()

        val mentions = contributors.mapValues { (_, contributor) -> resolveMention(contributor, identities) }

        val pullRequestLines = buildPullRequestLines(
            repoOwner = changeLog.repoOwner,
            repoName = changeLog.repoName,
            prNumbers = prNumbers,
            prTitles = prTitles,
            prUrls = prUrls,
            contributors = contributors.values,
            mentions = mentions
        )

        postSlackMessage(
            slackToken = slackToken,
            request = SlackMessageRequest(
                channel = slackChannel,
                threadTs = null,
                text = changeLog.title,
                blocks = buildReleaseNotificationBlocks(changeLog, monitoringUrls, pullRequestLines)
            )
        )
    }

    /**
     * Resolves a contributor to a real Slack mention. Uses the identity service's Slack
     * user ID directly when it has one - skipping the email lookup entirely. Otherwise
     * falls back to resolving an email (identity service, commit trailer, or public GitHub
     * profile) and looking that up in Slack.
     */
    private suspend fun resolveMention(contributor: Contributor, identities: Map<String, ResolvedIdentity>): String {
        val identity = findIdentity(contributor.login, identities)

        val slackUserId = resolveSlackUserId(
            identity = identity,
            fallbackEmail = { contributor.email ?: contributor.login?.let { gitHubService.getUser(it)?.email } },
            lookupSlackUserId = { email -> SlackUserResolver.lookupUserIdByEmail(slackToken, email) }
        )

        return formatMention(slackUserId = slackUserId, login = contributor.login, displayName = contributor.displayName)
    }
}

internal suspend fun postSlackMessage(slackToken: String, request: SlackMessageRequest) {
    val response = client.post("https://slack.com/api/chat.postMessage") {
        header("Authorization", "Bearer $slackToken")
        contentType(ContentType.Application.Json.withParameter("charset", Charsets.UTF_8.name))
        setBody(request)
    }

    val result = response.getBodySafe<SlackMessageResponse>()
    if (result?.ok != true) {
        DebugLogger.error("Could not post release notification to slack channel '${request.channel}'")
    }
}
