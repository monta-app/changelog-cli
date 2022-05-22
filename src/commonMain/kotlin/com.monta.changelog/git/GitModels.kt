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
    val commits: List<Commit>,
)

@Serializable
internal data class LogItem(
    val abbreviated_commit: String,
    val abbreviated_parent: String,
    val abbreviated_tree: String,
    val author: Author,
    val commit: String,
    val commit_notes: String,
    val committer: Author,
    val parent: String,
    val refs: String,
    val subject: String,
    val tree: String,
)

@Serializable
internal data class Author(
    val date: String,
    val email: String,
    val name: String,
)