package com.monta.changelog.printer.slack

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.printer.ChangeLogPrinter
import com.monta.changelog.util.LinkResolver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Prints Slack block JSON to stdout for testing in Slack Block Kit Builder.
 * Does not require Slack token or channels.
 */
class SlackJsonPrinter : ChangeLogPrinter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
    }

    override suspend fun print(
        linkResolvers: List<LinkResolver>,
        changeLog: ChangeLog,
    ) {
        val blockChunks = buildSlackBlocks(
            linkResolvers = linkResolvers,
            changeLog = changeLog
        )

        val metadataBlocks = buildMetadataBlocks(changeLog)

        // Print each chunk as a separate JSON payload
        blockChunks.forEachIndexed { index, blocks ->
            if (index > 0) {
                println("\n--- Message Chunk ${index + 1} ---\n")
            }
            val messageRequest = SlackMessageRequest(
                channel = "PREVIEW",
                threadTs = if (index == 0) null else "1234567890.123456",
                text = changeLog.title,
                blocks = blocks
            )
            println(json.encodeToString(messageRequest))
        }

        // Print metadata message in thread if available
        if (metadataBlocks.isNotEmpty()) {
            println("\n--- Metadata Message (in thread) ---\n")
            val metadataRequest = SlackMessageRequest(
                channel = "PREVIEW",
                threadTs = "1234567890.123456",
                text = "Metadata",
                blocks = metadataBlocks
            )
            println(json.encodeToString(metadataRequest))
        }
    }
}
