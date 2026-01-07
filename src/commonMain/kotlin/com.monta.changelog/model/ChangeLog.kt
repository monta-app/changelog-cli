package com.monta.changelog.model

import com.monta.changelog.util.GroupedCommitMap
import kotlinx.serialization.Serializable

@Serializable
data class ChangeLog(
    val serviceName: String,
    val jiraAppName: String?,
    val tagName: String,
    val repoOwner: String,
    val repoName: String,
    val repositoryUrl: String?,
    val groupedCommitMap: GroupedCommitMap,
) {
    val title: String
        get() = "$serviceName release $tagName"

    /**
     * Returns a Slack-formatted title with the service name as a clickable link (if repository URL is available)
     * Format: "<url|Service Name> Release V1.2.3" or "Service Name release v1.2.3"
     */
    fun getSlackTitle(): String {
        return if (repositoryUrl != null) {
            "<$repositoryUrl|$serviceName> Release $tagName"
        } else {
            title.split(" ").joinToString(" ") {
                it.replaceFirstChar { char -> char.uppercaseChar() }
            }
        }
    }

    var githubReleaseUrl: String? = null
}
