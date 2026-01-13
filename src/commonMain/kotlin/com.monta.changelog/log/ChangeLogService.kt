package com.monta.changelog.log

import com.monta.changelog.git.CommitInfo
import com.monta.changelog.git.GitService
import com.monta.changelog.git.sorter.TagSorter
import com.monta.changelog.github.GitHubService
import com.monta.changelog.model.ChangeLog
import com.monta.changelog.printer.ChangeLogPrinter
import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.GroupedCommitMap
import com.monta.changelog.util.LinkResolver

class ChangeLogService(
    debug: Boolean,
    private val serviceName: String,
    private val jiraAppName: String?,
    private val jiraEmail: String?,
    private val jiraToken: String?,
    tagSorter: TagSorter,
    private val githubRelease: Boolean,
    private val githubToken: String?,
    tagPattern: String?,
    pathExcludePattern: String?,
    private val jobUrl: String?,
    private val triggeredBy: String?,
    private val dockerImage: String?,
    private val imageTag: String?,
    private val previousImageTag: String?,
    private val stage: String?,
    private val deploymentStartTime: String?,
    private val deploymentEndTime: String?,
    private val deploymentUrl: String?,
) {

    private val gitService = GitService(tagSorter, tagPattern, pathExcludePattern)
    private val gitHubService = GitHubService(githubToken)
    private val jiraService = if (jiraAppName != null && jiraEmail != null && jiraToken != null) {
        com.monta.changelog.jira.JiraService(jiraAppName, jiraEmail, jiraToken)
    } else {
        null
    }
    private val repoInfo = gitService.getRepoInfo()
    private val repositoryUrl = gitService.getRepositoryUrl()

    private fun linkResolvers(
        validatedTickets: List<String>? = null,
        validatedPrs: List<String>? = null,
    ) = listOf(
        LinkResolver.Jira(
            jiraAppName = jiraAppName,
            validTickets = validatedTickets?.toSet()
        ),
        LinkResolver.Github(
            repoOwner = repoInfo.repoOwner,
            repoName = repoInfo.repoName,
            validPullRequests = validatedPrs?.toSet()
        )
    )

    init {
        if (debug) {
            DebugLogger.setLoggingLevel(DebugLogger.Level.Verbose)
        } else {
            DebugLogger.setLoggingLevel(DebugLogger.Level.Info)
        }

        DebugLogger.info("serviceName   $serviceName")
        DebugLogger.info("jiraAppName   $jiraAppName")
        if (jiraService != null) {
            DebugLogger.info("jiraValidation enabled")
        }
        DebugLogger.info("githubRelease $githubRelease")
        DebugLogger.info("repoOwner     ${repoInfo.repoOwner}")
        DebugLogger.info("repoName      ${repoInfo.repoName}")
        if (tagPattern != null) {
            DebugLogger.info("tagPattern    $tagPattern")
        }
        if (pathExcludePattern != null) {
            DebugLogger.info("pathExclude   $pathExcludePattern")
        }
    }

    suspend fun generateFromShas(
        changeLogPrinter: ChangeLogPrinter,
        startSha: String,
        endSha: String,
    ) = generateChangeLog(
        changeLogPrinter = changeLogPrinter,
        commitInfo = gitService.getCommits(
            startSha = startSha,
            endSha = endSha
        )
    )

    suspend fun generateFromTags(
        changeLogPrinter: ChangeLogPrinter,
        fromTag: String,
        toTag: String,
    ) = generateChangeLog(
        changeLogPrinter = changeLogPrinter,
        commitInfo = gitService.getCommitsBetweenTags(
            fromTag = fromTag,
            toTag = toTag
        )
    )

    suspend fun generate(
        changeLogPrinter: ChangeLogPrinter,
    ) = generateChangeLog(
        changeLogPrinter = changeLogPrinter,
        commitInfo = gitService.getCommits()
    )

    private suspend fun generateChangeLog(
        changeLogPrinter: ChangeLogPrinter,
        commitInfo: CommitInfo,
    ) {
        // Extract PRs and their bodies from ALL commits (including merge commits)
        val prInfos = extractPullRequestsFromCommitShas(commitInfo.commits, commitInfo.allCommitShas)

        // Fetch the user's real name from GitHub if triggeredBy is provided
        val triggeredByName = if (triggeredBy != null) {
            gitHubService.getUserName(triggeredBy)
        } else {
            null
        }

        // Extract and validate pull requests
        val extractedPrs = prInfos.map { it.number.toString() }.distinct().sortedBy { it.toIntOrNull() ?: 0 }

        // Warn if no PRs were detected but we have commits
        if (extractedPrs.isEmpty() && commitInfo.commits.isNotEmpty()) {
            val totalCommits = commitInfo.allCommitShas.size
            val nonMergeCommits = commitInfo.commits.size
            val mergeCommits = totalCommits - nonMergeCommits

            DebugLogger.warn("⚠️  No pull requests detected in changelog")
            DebugLogger.warn("   → Found $totalCommits total commit(s): $nonMergeCommits non-merge + $mergeCommits merge commit(s)")
            DebugLogger.warn("   → No associated PRs detected from any of these commits")

            if (githubToken.isNullOrBlank()) {
                DebugLogger.warn("   → Root cause: Missing GitHub token")
                DebugLogger.warn("     • Set CHANGELOG_GITHUB_TOKEN or --github-token to enable PR lookup via API")
                DebugLogger.warn("     • Without a token, we cannot fetch PR information from GitHub's API")
                DebugLogger.warn("     • We can only detect PRs if commit messages contain references (e.g., #123)")
            } else {
                DebugLogger.warn("   → Root cause: PRs not found (token is present)")
                DebugLogger.warn("     • Commits may have been pushed directly without PRs (not recommended)")
                DebugLogger.warn("     • PR references may be missing from commit messages (e.g., #123)")
                DebugLogger.warn("     • Or the GitHub API lookup failed to find associated PRs")
            }
        }

        val validatedPrs = if (extractedPrs.isNotEmpty()) {
            gitHubService.filterValidPullRequests(
                repoOwner = repoInfo.repoOwner,
                repoName = repoInfo.repoName,
                prNumbers = extractedPrs
            )
        } else {
            extractedPrs
        }

        // Extract and validate JIRA tickets
        val extractedTickets = extractJiraTickets(commitInfo.commits, prInfos)
        val validatedTickets = if (jiraService != null && extractedTickets.isNotEmpty()) {
            jiraService.filterValidTickets(extractedTickets)
        } else {
            if (jiraService == null && extractedTickets.isNotEmpty()) {
                DebugLogger.warn("⚠️  Skipping JIRA ticket validation - credentials not provided")
                DebugLogger.warn("   → Set CHANGELOG_JIRA_EMAIL and CHANGELOG_JIRA_TOKEN to enable JIRA validation")
                DebugLogger.warn("   → Without validation, invalid JIRA ticket references may appear in changelogs")
                DebugLogger.warn("   → Found ${extractedTickets.size} JIRA ticket(s) that will not be validated: ${extractedTickets.joinToString()}")
            }
            extractedTickets
        }

        val changeLog = ChangeLog(
            serviceName = serviceName,
            jiraAppName = jiraAppName,
            tagName = commitInfo.tagName,
            previousTagName = commitInfo.previousTagName,
            repoOwner = repoInfo.repoOwner,
            repoName = repoInfo.repoName,
            repositoryUrl = repositoryUrl,
            groupedCommitMap = commitInfo.toGroupedCommitMap(),
            pullRequests = validatedPrs,
            jiraTickets = validatedTickets,
            jobUrl = jobUrl,
            triggeredBy = triggeredBy,
            triggeredByName = triggeredByName,
            dockerImage = dockerImage,
            imageTag = imageTag,
            previousImageTag = previousImageTag,
            stage = stage,
            deploymentStartTime = deploymentStartTime,
            deploymentEndTime = deploymentEndTime,
            deploymentUrl = deploymentUrl
        )

        if (githubRelease) {
            changeLog.githubReleaseUrl = gitHubService.createOrUpdateRelease(
                linkResolvers = linkResolvers(
                    validatedTickets = validatedTickets,
                    validatedPrs = validatedPrs
                ),
                changeLog = changeLog
            )
        }

        changeLogPrinter.print(
            linkResolvers(
                validatedTickets = validatedTickets,
                validatedPrs = validatedPrs
            ),
            changeLog
        )
    }

    private suspend fun extractPullRequestsFromCommitShas(
        commits: List<com.monta.changelog.model.Commit>,
        commitShas: List<String>,
    ): List<com.monta.changelog.github.GitHubService.PullRequestInfo> {
        val prRegex = Regex("#(\\d+)")
        val allPrInfos = mutableListOf<com.monta.changelog.github.GitHubService.PullRequestInfo>()

        for (commitSha in commitShas) {
            // Extract PRs from commit message using regex (works without GitHub token)
            val commit = commits.find { it.sha == commitSha }
            if (commit != null) {
                val prsFromMessage = prRegex.findAll(commit.message).map { it.groupValues[1].toInt() }.toList()
                if (prsFromMessage.isNotEmpty()) {
                    DebugLogger.debug("Found PR(s) in message for ${commitSha.take(7)}: ${prsFromMessage.joinToString()}")
                    allPrInfos.addAll(prsFromMessage.map { com.monta.changelog.github.GitHubService.PullRequestInfo(it, "") })
                }
            }

            // Query GitHub API to find associated PRs (especially important for merge commits)
            val prsFromApi = gitHubService.getPullRequestsForCommit(
                repoOwner = repoInfo.repoOwner,
                repoName = repoInfo.repoName,
                commitSha = commitSha
            )

            if (prsFromApi.isNotEmpty()) {
                DebugLogger.debug("Found PR(s) from API for ${commitSha.take(7)}: ${prsFromApi.map { it.number }.joinToString()}")
            }

            allPrInfos.addAll(prsFromApi)
        }

        // Deduplicate by PR number, preferring entries with bodies
        val uniquePrs = allPrInfos.groupBy { it.number }.map { (_, infos) ->
            infos.firstOrNull { it.body.isNotEmpty() } ?: infos.first()
        }

        DebugLogger.debug("Total PRs extracted from ${commitShas.size} commits (including merge commits): ${uniquePrs.map { it.number }.sorted()}")

        return uniquePrs
    }

    private fun extractJiraTickets(
        commits: List<com.monta.changelog.model.Commit>,
        prInfos: List<com.monta.changelog.github.GitHubService.PullRequestInfo>,
    ): List<String> {
        val jiraIdRegex = Regex("[A-Z]{2,}-\\d+")
        val jiraUrlRegex = Regex("https://[^/]+\\.atlassian\\.net/browse/([A-Z]{2,}-\\d+)")

        // Extract from commits
        val ticketsFromCommits = commits.flatMap { commit ->
            val fullText = "${commit.message}\n${commit.body}"
            val ticketsFromIds = jiraIdRegex.findAll(fullText).map { it.value }
            val ticketsFromUrls = jiraUrlRegex.findAll(fullText).map { it.groupValues[1] }
            (ticketsFromIds + ticketsFromUrls).toList()
        }

        // Extract from PR bodies
        val ticketsFromPRs = prInfos.flatMap { prInfo ->
            val ticketsFromIds = jiraIdRegex.findAll(prInfo.body).map { it.value }
            val ticketsFromUrls = jiraUrlRegex.findAll(prInfo.body).map { it.groupValues[1] }
            val tickets = (ticketsFromIds + ticketsFromUrls).toList()
            if (tickets.isNotEmpty()) {
                DebugLogger.debug("Found JIRA ticket(s) in PR #${prInfo.number}: ${tickets.joinToString()}")
            }
            tickets
        }

        return (ticketsFromCommits + ticketsFromPRs)
            .distinct()
            .sorted()
    }

    private fun CommitInfo.toGroupedCommitMap(): GroupedCommitMap = this.commits
        // Group them up by the scope
        .groupBy { commit ->
            commit.scope.toReadableScope()
        }
        // Then the real work begins
        .map { (scope, commits) ->
            // Then after we've grouped our commits by scope we need to further sort them by
            // Type as there is a sorting order there
            scope to commits
                // So first we start off by grouping by type
                .groupBy { commit ->
                    commit.type
                }
                // Then we turn that into a list (as only these are sortable)
                .toList()
                .sortedBy { (type, _) ->
                    type.sortOrder
                }
                .toMap()
        }
        // Sort by the scope (in this instance null will be first)
        .sortedBy { (scope, _) ->
            scope
        }
        // Then back to a map (hopefully in the correct order)
        .toMap()

    private fun String?.toReadableScope(): String? = this?.split("-")
        ?.joinToString(" ") { joinString ->
            joinString.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        ?.replace(" Api", " API", true)
}
