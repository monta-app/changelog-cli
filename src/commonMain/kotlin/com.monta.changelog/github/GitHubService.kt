package com.monta.changelog.github

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.GroupedCommitMap
import com.monta.changelog.util.LinkResolver
import com.monta.changelog.util.MarkdownFormatter
import com.monta.changelog.util.client
import com.monta.changelog.util.resolve
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import platform.posix.exit

class GitHubService(
    private val githubToken: String?,
) {

    suspend fun createRelease(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog,
    ): String {

        val url = "https://api.github.com/repos/${changeLog.repoOwner}/${changeLog.repoName}/releases"

        val response = client.post(url) {
            header("Authorization", "token $githubToken")
            contentType(ContentType.Application.Json)
            accept(ContentType.parse("application/vnd.github.v3+json"))
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

        if (response.status.value in 200..299) {
            println("successfully created release ${response.bodyAsText()}")
            val responseBody = response.body<ReleaseResponse>()
            return responseBody.html_url
        } else {
            DebugLogger.error("failed to create release ${response.bodyAsText()}")
            DebugLogger.error("returning with code 1")
            exit(1)
            return ""
        }
    }

    private fun buildBody(
        markdownFormatter: MarkdownFormatter,
        linkResolvers: List<LinkResolver>,
        groupedCommitMap: GroupedCommitMap,
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
        val id: Int,
        val name: String,
        val html_url: String,
    )

    @Serializable
    data class ReleaseRequest(
        val body: String,
        val draft: Boolean,
        val generate_release_notes: Boolean,
        val name: String,
        val prerelease: Boolean,
        val tag_name: String,
    )

    @Serializable
    data class ErrorResponse(
        @SerialName("documentation_url")
        val documentationUrl: String,
        @SerialName("errors")
        val errors: List<Error>,
        @SerialName("message")
        val message: String,
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
        val resource: String,
    )
}