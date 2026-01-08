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
    ) {
        val blockChunks = buildSlackBlocks(
            linkResolvers = linkResolvers,
            changeLog = changeLog
        )

        val metadataBlocks = buildMetadataBlocks(changeLog)

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
}
