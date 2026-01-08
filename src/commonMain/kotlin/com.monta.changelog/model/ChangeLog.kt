package com.monta.changelog.model

import com.monta.changelog.util.GroupedCommitMap
import kotlinx.serialization.Serializable

@Serializable
data class ChangeLog(
    val serviceName: String,
    val jiraAppName: String?,
    val tagName: String,
    val previousTagName: String?,
    val repoOwner: String,
    val repoName: String,
    val repositoryUrl: String?,
    val groupedCommitMap: GroupedCommitMap,
) {
    val title: String
        get() = "$serviceName release $tagName"

    var githubReleaseUrl: String? = null
}
