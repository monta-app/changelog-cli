package com.monta.changelog.git

import com.monta.changelog.git.sorter.Tag
import com.monta.changelog.git.sorter.TagSorter
import com.monta.changelog.model.Commit
import com.monta.changelog.util.DebugLogger

class GitService(
    private val tagSorter: TagSorter,
    tagPattern: String?,
    pathExcludePattern: String?,
) {

    private val gitCommandUtil = GitCommandUtil()
    private val commitMapper = CommitMapper()
    private val tagPattern = tagPattern?.let(::Regex)
    private val pathExcludePattern = pathExcludePattern?.let(::Regex)

    fun getRepoInfo(): RepoInfo {
        val (repoOwner, repoName) = getGitOwnerAndRepo()
        return RepoInfo(repoOwner, repoName)
    }

    fun getRepositoryUrl(): String? {
        return try {
            val remoteUrl = gitCommandUtil.getRemoteUrl() ?: return null
            normalizeGitHubUrl(remoteUrl)
        } catch (e: Exception) {
            DebugLogger.debug("Could not determine repository URL: ${e.message}")
            null
        }
    }

    fun getCommits(startSha: String, endSha: String): CommitInfo {
        val logs = gitCommandUtil.getLogs(startSha, endSha)
        val (commits, nonConventional) = logs.mapToCommitsAndNonConventional()
        return CommitInfo(
            tagName = tagSorter.generateInitialTag(),
            previousTagName = null,
            commits = commits,
            allCommitShas = logs.map { it.commit },
            nonConventionalCommits = nonConventional
        )
    }

    fun getCommitsBetweenTags(fromTag: String, toTag: String): CommitInfo {
        DebugLogger.info("generating changelog between tags $fromTag and $toTag")
        val logs = gitCommandUtil.getLogs(toTag, fromTag)
        val (commits, nonConventional) = logs.mapToCommitsAndNonConventional()
        return CommitInfo(
            tagName = toTag.getTagValue(),
            previousTagName = fromTag.getTagValue(),
            commits = commits,
            allCommitShas = logs.map { it.commit },
            nonConventionalCommits = nonConventional
        )
    }

    fun getCommits(): CommitInfo {
        val tags = tagSorter.sort(
            tags = gitCommandUtil.getTags()
                .mapNotNull { tag ->
                    when (tagPattern) {
                        null -> Tag(tag)
                        else -> {
                            // there is a tag pattern, extract group 1
                            val match = tagPattern.matchEntire(tag)
                            if (match != null) {
                                match.groups[1]?.value?.let { Tag(it, tag) }
                            } else {
                                null
                            }
                        }
                    }
                }
        )

        DebugLogger.debug("found tags: $tags")

        when (tags.size) {
            0 -> {
                DebugLogger.info("no tags found; returning from latest commit to last tag")
                val logs = gitCommandUtil.getLogs()
                val (commits, nonConventional) = logs.mapToCommitsAndNonConventional()
                return CommitInfo(
                    tagName = tagSorter.generateInitialTag(),
                    previousTagName = null,
                    commits = commits,
                    allCommitShas = logs.map { it.commit },
                    nonConventionalCommits = nonConventional
                )
            }

            1 -> {
                val latestTag = tags[0].fullTag
                DebugLogger.info("only one tag found $latestTag; returning from latest commit to last tag")
                val logs = gitCommandUtil.getLogs(gitCommandUtil.getHeadSha(), latestTag)
                val (commits, nonConventional) = logs.mapToCommitsAndNonConventional()
                return CommitInfo(
                    tagName = latestTag.getTagValue(),
                    previousTagName = null,
                    commits = commits,
                    allCommitShas = logs.map { it.commit },
                    nonConventionalCommits = nonConventional
                )
            }

            else -> {
                val latestTag = tags[0].fullTag
                val previousTag = tags[1].fullTag
                DebugLogger.info("returning commits between tag $latestTag and $previousTag")
                val logs = gitCommandUtil.getLogs(latestTag, previousTag)
                val (commits, nonConventional) = logs.mapToCommitsAndNonConventional()
                return CommitInfo(
                    tagName = latestTag.getTagValue(),
                    previousTagName = previousTag.getTagValue(),
                    commits = commits,
                    allCommitShas = logs.map { it.commit },
                    nonConventionalCommits = nonConventional
                )
            }
        }
    }

    private fun List<LogItem>.mapToCommitsAndNonConventional(): Pair<List<Commit>, List<NonConventionalCommit>> {
        val filteredLogs = this.filter { gitLogItem ->
            when (pathExcludePattern) {
                null -> true
                else -> {
                    val filesInCommit = gitCommandUtil.getFilesInCommit(gitLogItem.commit)
                    filesInCommit.any { !pathExcludePattern.containsMatchIn(it) }
                }
            }
        }

        val commits = mutableListOf<Commit>()
        val nonConventional = mutableListOf<NonConventionalCommit>()

        filteredLogs.forEach { gitLogItem ->
            val commit = commitMapper.fromGitLogItem(gitLogItem)
            if (commit != null) {
                commits.add(commit)
            } else {
                // Skip merge commits - they're already excluded in CommitMapper
                if (!gitLogItem.isMergeCommit()) {
                    nonConventional.add(
                        NonConventionalCommit(
                            sha = gitLogItem.commit,
                            subject = gitLogItem.subject,
                            author = gitLogItem.author.name
                        )
                    )
                }
            }
        }

        return commits.toSet().toList() to nonConventional
    }

    private fun getGitOwnerAndRepo(): Pair<String, String> = getGitOwnerAndRepo(
        url = requireNotNull(
            gitCommandUtil.getRemoteUrl()
        )
    )

    private fun getGitOwnerAndRepo(url: String): Pair<String, String> {
        val normalizedPath = normalizeGitHubPath(url)
        val splitUrl = normalizedPath.split("/").map { it.trim() }
        return splitUrl[0] to splitUrl[1]
    }

    /**
     * Normalizes a GitHub remote URL to the owner/repo path format.
     * Handles SSH, HTTPS, and git@ URL formats.
     */
    private fun normalizeGitHubPath(url: String): String = url
        .removePrefix("ssh@github.com:")
        .removePrefix("git@github.com:")
        .removePrefix("https://github.com/")
        .removeSuffix(".git")

    /**
     * Normalizes a GitHub remote URL to a standard HTTPS URL.
     */
    private fun normalizeGitHubUrl(url: String): String = "https://github.com/${normalizeGitHubPath(url)}"
}

fun String.getTagValue(): String = this.split("/").last()
