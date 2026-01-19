package com.monta.changelog.git

import com.monta.changelog.model.Commit
import kotlinx.serialization.Serializable

@Serializable
data class RepoInfo(
    val repoOwner: String,
    val repoName: String,
)

@Serializable
data class CommitInfo(
    val tagName: String,
    val previousTagName: String?,
    val commits: List<Commit>,
    val allCommitShas: List<String> = emptyList(), // All commit SHAs including filtered ones (merge commits, etc.)
    val nonConventionalCommits: List<NonConventionalCommit> = emptyList(), // Commits that didn't match conventional commit syntax
)

@Serializable
data class NonConventionalCommit(
    val sha: String,
    val subject: String,
    val author: String,
)

@Serializable
internal data class LogItem(
    val author: Author,
    val committer: Author,
    val commit: String,
    val subject: String,
    val body: String = "",
    val parents: String = "",
) {
    /**
     * Returns true if this is a merge commit (has 2 or more parents).
     */
    fun isMergeCommit(): Boolean = parents.trim().contains(" ")
}

@Serializable
internal data class Author(
    val date: String,
    val email: String,
    val name: String,
)
