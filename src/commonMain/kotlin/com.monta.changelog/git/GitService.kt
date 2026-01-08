package com.monta.changelog.git

import com.monta.changelog.git.sorter.Tag
import com.monta.changelog.git.sorter.TagSorter
import com.monta.changelog.model.Commit
import com.monta.changelog.util.DebugLogger
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
            // Convert git remote URL to HTTPS URL
            remoteUrl
                .removePrefix("ssh@github.com:")
                .removePrefix("git@github.com:")
                .removePrefix("https://github.com/")
                .removeSuffix(".git")
                .let { "https://github.com/$it" }
        } catch (e: Exception) {
            null
        }
    }

    fun getCommits(startSha: String, endSha: String): CommitInfo = CommitInfo(
        tagName = Clock.System.now().toLocalDateTime(TimeZone.UTC).toString(),
        commits = gitCommandUtil.getLogs(startSha, endSha).mapToCommits()
    )

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
                return CommitInfo(
                    tagName = Clock.System.now().toLocalDateTime(TimeZone.UTC).toString(),
                    commits = gitCommandUtil.getLogs().mapToCommits()
                )
            }

            1 -> {
                val latestTag = tags[0].fullTag
                DebugLogger.info("only one tag found $latestTag; returning from latest commit to last tag")
                return CommitInfo(
                    tagName = latestTag.getTagValue(),
                    commits = gitCommandUtil.getLogs(gitCommandUtil.getHeadSha(), latestTag).mapToCommits()
                )
            }

            else -> {
                val latestTag = tags[0].fullTag
                val previousTag = tags[1].fullTag
                DebugLogger.info("returning commits between tag $latestTag and $previousTag")
                return CommitInfo(
                    tagName = latestTag.getTagValue(),
                    commits = gitCommandUtil.getLogs(latestTag, previousTag).mapToCommits()
                )
            }
        }
    }

    private fun List<LogItem>.mapToCommits(): List<Commit> = this.filter { gitLogItem ->
        when (pathExcludePattern) {
            null -> true
            else -> {
                val filesInCommit = gitCommandUtil.getFilesInCommit(gitLogItem.commit)
                filesInCommit.any { !pathExcludePattern.containsMatchIn(it) }
            }
        }
    }.mapNotNull { gitLogItem ->
        commitMapper.fromGitLogItem(gitLogItem)
    }.toSet().toList()

    private fun getGitOwnerAndRepo(): Pair<String, String> = getGitOwnerAndRepo(
        url = requireNotNull(
            gitCommandUtil.getRemoteUrl()
        )
    )

    private fun getGitOwnerAndRepo(url: String): Pair<String, String> {
        val splitUrl = url.removePrefix("ssh@github.com:")
            .removePrefix("git@github.com:")
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .split("/")
            .map { it.trim() }

        return splitUrl[0] to splitUrl[1]
    }
}

fun String.getTagValue(): String = this.split("/").last()
