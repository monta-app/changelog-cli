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
    val pullRequests: List<String> = emptyList(),
    val jiraTickets: List<String> = emptyList(),
    val jobUrl: String? = null,
    val triggeredBy: String? = null,
    val triggeredByName: String? = null,
    val dockerImage: String? = null,
    val imageTag: String? = null,
    val previousImageTag: String? = null,
    val stage: String? = null,
    val deploymentStartTime: String? = null,
    val deploymentEndTime: String? = null,
    val deploymentUrl: String? = null,
) {
    val title: String
        get() = "$serviceName release $tagName"

    var githubReleaseUrl: String? = null
}
