package com.monta.changelog.printer.slack

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.printer.ChangeLogPrinter
import com.monta.changelog.util.LinkResolver
import com.monta.changelog.util.MarkdownFormatter
import com.monta.changelog.util.client
import com.monta.changelog.util.resolve
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

class SlackChangeLogPrinter(
    private val slackToken: String,
    private val slackChannels: Set<String>,
) : ChangeLogPrinter {

    override suspend fun print(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog,
    ) {
        for (channel in slackChannels) {
            makeRequest(
                SlackMessageRequest(
                    channel = channel,
                    text = changeLog.title,
                    blocks = buildRequest(
                        linkResolvers = linkResolvers,
                        changeLog = changeLog,
                    )
                )
            )
        }
    }

    private fun buildRequest(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog,
    ): List<SlackBlock> {
        val markdownFormatter = MarkdownFormatter.Slack

        return buildList {

            header { changeLog.title }

            changeLog.groupedCommitMap.forEach { (scope, commitsGroupedByType) ->
                if (scope != null) {
                    divider()
                    header {
                        (scope).replaceFirstChar { char ->
                            char.uppercaseChar()
                        }
                    }
                }
                commitsGroupedByType.map { (type, commits) ->
                    text {
                        buildString {
                            // Create our title
                            append(
                                markdownFormatter.title("${type.emoji} ${type.title}")
                            )
                            // Add the markdown list after
                            commits.forEach { commit ->
                                append(
                                    MarkdownFormatter.Slack.listItem(
                                        linkResolvers.resolve(
                                            markdownFormatter = markdownFormatter,
                                            message = commit.message
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }

            changeLog.githubReleaseUrl?.let { githubReleaseUrl ->
                button { githubReleaseUrl }
            }
        }
    }

    private suspend fun makeRequest(slackMessageRequest: SlackMessageRequest) {

        val response = client.post("https://slack.com/api/chat.postMessage") {
            header("Authorization", "Bearer $slackToken")
            contentType(ContentType.Application.Json)
            setBody(slackMessageRequest)
        }

        val body = response.body<SlackMessageResponse>()

        if (response.status.value in 200..299 && body.ok) {
            println("successfully posted message ${response.bodyAsText()}")
        } else {
            println("failed to post message ${response.bodyAsText()}")
        }
    }
}
