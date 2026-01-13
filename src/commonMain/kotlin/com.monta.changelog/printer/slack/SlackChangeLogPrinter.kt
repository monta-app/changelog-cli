package com.monta.changelog.printer.slack

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.printer.ChangeLogPrinter
import com.monta.changelog.util.DebugLogger
import com.monta.changelog.util.LinkResolver
import com.monta.changelog.util.client
import com.monta.changelog.util.getBodySafe
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*

class SlackChangeLogPrinter(
    private val slackToken: String,
    private val slackChannels: Set<String>,
) : ChangeLogPrinter {

    override suspend fun print(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog,
    ): ChangeLogPrinter.PrintResult? {
        val blockChunks = buildSlackBlocks(
            linkResolvers = linkResolvers,
            changeLog = changeLog
        )

        val metadataBlocks = buildMetadataBlocks(changeLog)

        var firstMessageChannel: String? = null
        var firstMessageTs: String? = null

        for (channel in slackChannels) {
            var threadTs: String? = null

            // Send all changelog chunks
            blockChunks.forEach { blocks ->
                threadTs = makeRequest(
                    SlackMessageRequest(
                        channel = channel,
                        threadTs = threadTs,
                        text = changeLog.title,
                        blocks = blocks
                    )
                )

                // Store first message's channel and ts for permalink
                if (firstMessageChannel == null && threadTs != null) {
                    firstMessageChannel = channel
                    firstMessageTs = threadTs
                }
            }

            // Send metadata message in the thread if we have metadata and a thread
            if (metadataBlocks.isNotEmpty() && threadTs != null) {
                makeRequest(
                    SlackMessageRequest(
                        channel = channel,
                        threadTs = threadTs,
                        text = "Metadata",
                        blocks = metadataBlocks
                    )
                )
            }
        }

        // Get permalink for the first message
        val permalink = if (firstMessageChannel != null && firstMessageTs != null) {
            getPermalink(firstMessageChannel, firstMessageTs)
        } else {
            null
        }

        return if (permalink != null) {
            ChangeLogPrinter.PrintResult(slackMessageUrl = permalink)
        } else {
            null
        }
    }

    private suspend fun makeRequest(slackMessageRequest: SlackMessageRequest): String? {
        val response = client.post("https://slack.com/api/chat.postMessage") {
            header("Authorization", "Bearer $slackToken")
            contentType(ContentType.Application.Json.withParameter("charset", Charsets.UTF_8.name))
            setBody(slackMessageRequest)
        }

        val result = response.getBodySafe<SlackMessageResponse>()?.ts
        if (result == null) {
            DebugLogger.error("Could not post message to slack channel '${slackMessageRequest.channel}'")
        }
        return result
    }

    private suspend fun getPermalink(channel: String, messageTs: String): String? {
        val response = client.get("https://slack.com/api/chat.getPermalink") {
            header("Authorization", "Bearer $slackToken")
            url {
                parameters.append("channel", channel)
                parameters.append("message_ts", messageTs)
            }
        }

        val result = response.getBodySafe<SlackPermalinkResponse>()
        if (result?.ok == true && result.permalink != null) {
            DebugLogger.debug("Got Slack permalink: ${result.permalink}")
            return result.permalink
        } else {
            DebugLogger.warn("⚠️  Could not get Slack permalink for message")
            if (result?.error != null) {
                DebugLogger.warn("   → Slack API error: ${result.error}")
            }
        }
        return null
    }
}
