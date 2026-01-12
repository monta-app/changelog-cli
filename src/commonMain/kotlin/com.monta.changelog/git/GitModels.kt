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
