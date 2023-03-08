package com.monta.changelog.printer.slack

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.model.Commit
import com.monta.changelog.model.ConventionalCommitType
import com.monta.changelog.printer.ChangeLogPrinter
import com.monta.changelog.util.LinkResolver
import com.monta.changelog.util.MarkdownFormatter
import com.monta.changelog.util.client
import com.monta.changelog.util.getBodySafe
import com.monta.changelog.util.resolve
import io.ktor.client.request.*
import io.ktor.http.*

class SlackChangeLogPrinter(
    private val slackToken: String,
    private val slackChannels: Set<String>
) : ChangeLogPrinter {

    override suspend fun print(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog
    ) {
        val blockChunks = buildRequest(
            linkResolvers = linkResolvers,
            changeLog = changeLog
        )

        for (channel in slackChannels) {
            var threadTs: String? = null

            blockChunks.forEach { blocks ->
                threadTs = makeRequest(
                    SlackMessageRequest(
                        channel = channel,
                        threadTs = threadTs,
                        text = changeLog.title,
                        blocks = blocks
                    )
                )
            }
        }
    }

    private fun buildRequest(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog
    ): List<List<SlackBlock>> {
        val chunkBlockList = mutableListOf<List<SlackBlock>>()

        var currentChunk = mutableListOf<SlackBlock>()

        for ((scope, commitsGroupedByType) in changeLog.groupedCommitMap) {
            if (scope == null) {
                currentChunk.header {
                    changeLog.title.split(" ").joinToString(" ") {
                        it.replaceFirstChar { char ->
                            char.uppercaseChar()
                        }
                    }
                }
            } else {
                currentChunk.header {
                    (scope).replaceFirstChar { char ->
                        char.uppercaseChar()
                    }
                }
            }

            currentChunk.extracted(
                commitsGroupedByType = commitsGroupedByType,
                markdownFormatter = MarkdownFormatter.Slack,
                linkResolvers = linkResolvers
            )

            // Increment (If needed)
            if (currentChunk.count() > 25) {
                chunkBlockList.add(currentChunk)
                currentChunk = mutableListOf()
            }
        }

        chunkBlockList.add(currentChunk)

        return chunkBlockList
    }

    private fun MutableList<SlackBlock>.extracted(
        commitsGroupedByType: Map<ConventionalCommitType, List<Commit>>,
        markdownFormatter: MarkdownFormatter.Slack,
        linkResolvers: List<LinkResolver>
    ) {
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

    private suspend fun makeRequest(slackMessageRequest: SlackMessageRequest): String? {
        val response = client.post("https://slack.com/api/chat.postMessage") {
            header("Authorization", "Bearer $slackToken")
            contentType(ContentType.Application.Json)
            setBody(slackMessageRequest)
        }

        return response.getBodySafe<SlackMessageResponse>()?.ts
    }
}
