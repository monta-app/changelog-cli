package com.monta.changelog.github

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.GroupedCommitMap
import com.monta.changelog.util.LinkResolver
import com.monta.changelog.util.MarkdownFormatter
import com.monta.changelog.util.client
import com.monta.changelog.util.resolve
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import platform.posix.exit

class GitHubService(
    private val githubToken: String?,
) {

    suspend fun createOrUpdateRelease(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog,
    ): String? {
        val htmlUrl: String? = createRelease(linkResolvers, changeLog)

        if (htmlUrl == null) {
            return updateRelease(linkResolvers, changeLog)
        }

        return htmlUrl
    }

    private suspend fun createRelease(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog,
    ): String? {
        DebugLogger.info("creating release")

        val response = client.githubRequest(
            path = "${changeLog.repoOwner}/${changeLog.repoName}/releases",
            httpMethod = HttpMethod.Post,
            body = ReleaseRequest(
                body = buildBody(
                    markdownFormatter = MarkdownFormatter.GitHub,
                    linkResolvers = linkResolvers,
                    groupedCommitMap = changeLog.groupedCommitMap
                ),
                draft = false,
                generateReleaseNotes = false,
                name = changeLog.tagName,
                prerelease = false,
                tagName = changeLog.tagName
            )
        )

        if (response.status.isSuccess()) {
            try {
                return response.body<ReleaseResponse>().htmlUrl
            } catch (throwable: Throwable) {
                DebugLogger.error("failed to deserialized ReleaseResponse body ${response.bodyAsText()}")
                // No return just let it go to the general exit path at the end
            }
        } else {
            try {
                val errorResponse = response.body<ErrorResponse>()
                if (errorResponse.hasReleaseAlreadyExists()) {
                    // This is ok and we can recover from this, so we return here
                    return null
                }
            } catch (throwable: Throwable) {
                throwable.printStackTrace()
                DebugLogger.error("failed to deserialized ErrorResponse body ${response.bodyAsText()}")
                // No return just let it go to the general exit path at the end
            }
        }

        DebugLogger.error("failed to create release ${response.bodyAsText()}")
        DebugLogger.error("returning with code 1")
        exit(1)
        return null
    }

    private suspend fun updateRelease(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog,
    ): String? {
        DebugLogger.info("updating release")

        val releaseId = getReleaseId(changeLog)

        val response = client.githubRequest(
            path = "${changeLog.repoOwner}/${changeLog.repoName}/releases/$releaseId",
            httpMethod = HttpMethod.Patch,
            body = ReleaseRequest(
                body = buildBody(
                    markdownFormatter = MarkdownFormatter.GitHub,
                    linkResolvers = linkResolvers,
                    groupedCommitMap = changeLog.groupedCommitMap
                )
            )
        )

        if (response.status.isSuccess()) {
            try {
                return response.body<ReleaseResponse>().htmlUrl
            } catch (throwable: Throwable) {
                DebugLogger.error("failed to deserialized ReleaseResponse body ${response.bodyAsText()}")
                // No return just let it go to the general exit path at the end
            }
        }

        DebugLogger.error("failed to update release ${response.bodyAsText()}")
        DebugLogger.error("returning with code 1")
        exit(1)
        return null
    }

    private suspend fun getReleaseId(changeLog: ChangeLog): Int? {
        val response = client.githubRequest(
            path = "${changeLog.repoOwner}/${changeLog.repoName}/releases/tags/${changeLog.tagName}",
            httpMethod = HttpMethod.Get,
            body = null as String?
        )

        if (response.status.isSuccess()) {
            DebugLogger.info("found release ${response.bodyAsText()}")
            return response.body<ReleaseResponse>().id
        }

        DebugLogger.error("could not find release ${response.bodyAsText()}")
        DebugLogger.error("returning with code 1")
        exit(1)
        return null
    }

    private suspend inline fun <reified T> HttpClient.githubRequest(
        path: String,
        httpMethod: HttpMethod,
        body: T?,
    ): HttpResponse = request {
        url {
            url("https://api.github.com/repos/$path")
            method = httpMethod
        }
        header("Authorization", "token $githubToken")
        accept(ContentType.parse("application/vnd.github.v3+json"))
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    private fun buildBody(
        markdownFormatter: MarkdownFormatter,
        linkResolvers: List<LinkResolver>,
        groupedCommitMap: GroupedCommitMap,
    ): String = buildString {
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

    @Serializable
    data class ReleaseResponse(
        @SerialName("id")
        val id: Int?,
        @SerialName("name")
        val name: String?,
        @SerialName("html_url")
        val htmlUrl: String?,
    )

    @Serializable
    data class ReleaseRequest(
        @SerialName("body")
        val body: String,
        @SerialName("draft")
        val draft: Boolean? = null,
        @SerialName("generate_release_notes")
        val generateReleaseNotes: Boolean? = null,
        @SerialName("name")
        val name: String? = null,
        @SerialName("prerelease")
        val prerelease: Boolean? = null,
        @SerialName("tag_name")
        val tagName: String? = null,
    )

    @Serializable
    data class ErrorResponse(
        @SerialName("documentation_url")
        val documentationUrl: String,
        @SerialName("errors")
        val errors: List<Error>?,
        @SerialName("message")
        val message: String,
    ) {
        fun hasReleaseAlreadyExists(): Boolean {
            val error = errors?.find { error ->
                error.resource == "Release" && error.code == "already_exists" && error.field == "tag_name"
            }
            return error != null
        }
    }

    @Serializable
    data class Error(
        @SerialName("code")
        val code: String,
        @SerialName("field")
        val `field`: String,
        @SerialName("resource")
        val resource: String,
    )

    /**
     * Gets the PR numbers associated with a commit SHA.
     * Returns empty list if no token is provided or if the API call fails.
     */
    suspend fun getPullRequestsForCommit(
        repoOwner: String,
        repoName: String,
        commitSha: String,
    ): List<Int> {
        if (githubToken == null) {
            return emptyList()
        }

        return try {
            val response = client.githubRequest<String?>(
                path = "$repoOwner/$repoName/commits/$commitSha/pulls",
                httpMethod = HttpMethod.Get,
                body = null
            )

            if (response.status.isSuccess()) {
                response.body<List<PullRequestResponse>>().mapNotNull { it.number }
            } else {
                DebugLogger.debug("Failed to get PRs for commit $commitSha: ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            DebugLogger.debug("Exception getting PRs for commit $commitSha: ${e.message}")
            emptyList()
        }
    }

    @Serializable
    data class PullRequestResponse(
        @SerialName("number")
        val number: Int?,
        @SerialName("title")
        val title: String?,
        @SerialName("html_url")
        val htmlUrl: String?,
    )
}
