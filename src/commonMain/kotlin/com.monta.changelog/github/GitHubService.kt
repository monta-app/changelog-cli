package com.monta.changelog.github

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.GroupedCommitMap
import com.monta.changelog.util.LinkResolver
import com.monta.changelog.util.MarkdownFormatter
import com.monta.changelog.util.client
import com.monta.changelog.util.getBodySafe
import com.monta.changelog.util.resolve
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import platform.posix.exit

class GitHubService(
    private val githubToken: String?
) {

    suspend fun createRelease(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog
    ): String? {
        val url = baseUrl(
            path = "${changeLog.repoOwner}/${changeLog.repoName}/releases"
        )

        val response = client.post(url) {
            withGithubDefaults()
            setBody(
                ReleaseRequest(
                    body = buildBody(
                        markdownFormatter = MarkdownFormatter.GitHub,
                        linkResolvers = linkResolvers,
                        groupedCommitMap = changeLog.groupedCommitMap
                    ),
                    draft = false,
                    generate_release_notes = false,
                    name = changeLog.tagName,
                    prerelease = false,
                    tag_name = changeLog.tagName
                )
            )
        }

        val releaseResponse = response.getBodySafe<ReleaseResponse>()

        if (releaseResponse == null) {
            DebugLogger.error("failed to create release ${response.bodyAsText()}")
            DebugLogger.error("returning with code 1")
            exit(1)
        }

        DebugLogger.info("successfully created release")

        return releaseResponse?.htmlUrl
    }

    suspend fun updateRelease(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog
    ): String? {
        val releaseId = getReleaseId(changeLog)

        val url = baseUrl(
            path = "${changeLog.repoOwner}/${changeLog.repoName}/releases/$releaseId"
        )

        val response = client.patch(url) {
            withGithubDefaults()
            setBody(
                UpdateReleaseRequest(
                    body = buildBody(
                        markdownFormatter = MarkdownFormatter.GitHub,
                        linkResolvers = linkResolvers,
                        groupedCommitMap = changeLog.groupedCommitMap
                    )
                )
            )
        }

        val releaseResponse = response.getBodySafe<ReleaseResponse>()

        if (releaseResponse == null) {
            DebugLogger.error("failed to update release ${response.bodyAsText()}")
            DebugLogger.error("returning with code 1")
            exit(1)
        }

        DebugLogger.info("successfully updated release")

        return releaseResponse?.htmlUrl
    }

    private suspend fun getReleaseId(changeLog: ChangeLog): Int? {
        val url = baseUrl(
            path = "${changeLog.repoOwner}/${changeLog.repoName}/releases/tags/${changeLog.tagName}"
        )

        val response = client.get(url) {
            withGithubDefaults(false)
        }

        return if (response.status.value == 200) {
            DebugLogger.info("found release ${response.bodyAsText()}")
            val responseBody = response.body<ReleaseResponse>()
            responseBody.id
        } else {
            DebugLogger.error("could not find release ${response.bodyAsText()}")
            DebugLogger.error("returning with code 1")
            exit(1)
            null
        }
    }

    private fun baseUrl(path: String): String {
        return "https://api.github.com/repos/$path"
    }

    private fun HttpRequestBuilder.withGithubDefaults(
        isJson: Boolean = true
    ) {
        header("Authorization", "token $githubToken")
        if (isJson) {
            contentType(ContentType.Application.Json)
        }
        accept(ContentType.parse("application/vnd.github.v3+json"))
    }

    private fun buildBody(
        markdownFormatter: MarkdownFormatter,
        linkResolvers: List<LinkResolver>,
        groupedCommitMap: GroupedCommitMap
    ): String {
        return buildString {
            groupedCommitMap.forEach { (scope, commitsGroupedByType) ->
                if (scope != null) {
                    appendLine()
                    append(
                        markdownFormatter.header(
                            (scope).replaceFirstChar { char ->
                                char.uppercaseChar()
                            }
                        )
                    )
                    appendLine()
                }
                commitsGroupedByType.forEach { (type, commits) ->
                    append(
                        markdownFormatter.title("${type.emoji} ${type.title}")
                    )
                    commits.forEach { commit ->
                        append(
                            markdownFormatter.listItem(
                                linkResolvers.resolve(
                                    markdownFormatter = markdownFormatter,
                                    message = commit.message
                                )
                            )
                        )
                    }
                }
                appendLine()
            }
        }
    }

    @Serializable
    data class ReleaseResponse(
        @SerialName("id")
        val id: Int?,
        @SerialName("name")
        val name: String?,
        @SerialName("html_url")
        val htmlUrl: String?
    )

    @Serializable
    data class ReleaseRequest(
        val body: String,
        val draft: Boolean,
        val generate_release_notes: Boolean,
        val name: String,
        val prerelease: Boolean,
        val tag_name: String
    )

    @Serializable
    data class UpdateReleaseRequest(
        val body: String
    )

    @Serializable
    data class ErrorResponse(
        @SerialName("documentation_url")
        val documentationUrl: String,
        @SerialName("errors")
        val errors: List<Error>,
        @SerialName("message")
        val message: String
    )

    @Serializable
    data class Error(
        @SerialName("code")
        val code: String,
        @SerialName("field")
        val `field`: String,
        @SerialName("message")
        val message: String,
        @SerialName("resource")
        val resource: String
    )
}
