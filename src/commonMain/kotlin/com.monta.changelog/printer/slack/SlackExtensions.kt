package com.monta.changelog.printer.slack

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.model.Commit
import com.monta.changelog.model.ConventionalCommitType
import com.monta.changelog.util.LinkResolver
import com.monta.changelog.util.MarkdownFormatter
import com.monta.changelog.util.resolve

/**
 * Builds Slack block chunks from a changelog.
 * Returns a list of block lists, where each list is a separate message chunk
 * (limited to 25 blocks per chunk due to Slack's API limits).
 */
internal fun buildSlackBlocks(
    linkResolvers: List<LinkResolver>,
    changeLog: ChangeLog,
): List<List<SlackBlock>> {
    val chunkBlockList = mutableListOf<List<SlackBlock>>()
    var currentChunk = mutableListOf<SlackBlock>()

    // Add title at the beginning using proper header block for larger text
    currentChunk.add(
        SlackBlock(
            type = "header",
            text = SlackText(
                type = "plain_text",
                text = changeLog.title.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
            )
        )
    )

    for ((scope, commitsGroupedByType) in changeLog.groupedCommitMap) {
        // Add scope header if this is a scoped section
        if (scope != null) {
            currentChunk.add(
                SlackBlock(
                    type = "header",
                    text = SlackText(
                        type = "plain_text",
                        text = scope.replaceFirstChar { char -> char.uppercaseChar() }
                    )
                )
            )
        }

        currentChunk.addCommitBlocks(
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

/**
 * Builds metadata blocks for the threaded message.
 * Contains additional information about the changelog like repository link.
 */
internal fun buildMetadataBlocks(changeLog: ChangeLog): List<SlackBlock> {
    val blocks = mutableListOf<SlackBlock>()
    val fields = mutableListOf<SlackField>()

    // Add repository field if available
    if (changeLog.repositoryUrl != null) {
        fields.add(
            SlackField(
                type = "mrkdwn",
                text = "*Repository:*\n<${changeLog.repositoryUrl}|${changeLog.serviceName}>"
            )
        )
    }

    // Add changeset compare link if both current and previous tags are available
    if (changeLog.repositoryUrl != null && changeLog.previousTagName != null) {
        val compareUrl = "${changeLog.repositoryUrl}/compare/${changeLog.previousTagName}...${changeLog.tagName}"
        fields.add(
            SlackField(
                type = "mrkdwn",
                text = "*Changeset:*\n<$compareUrl|${changeLog.previousTagName}...${changeLog.tagName}>"
            )
        )
    }

    // Add pull requests if available (split if too long)
    if (changeLog.pullRequests.isNotEmpty() && changeLog.repositoryUrl != null) {
        val prCount = changeLog.pullRequests.size
        fields.addAll(
            splitIntoFields(
                header = "Pull Requests ($prCount)",
                items = changeLog.pullRequests.map { prNumber ->
                    "<${changeLog.repositoryUrl}/pull/$prNumber|#$prNumber>"
                }
            )
        )
    }

    // Add JIRA tickets if available (split if too long)
    if (changeLog.jiraTickets.isNotEmpty() && changeLog.jiraAppName != null) {
        val ticketCount = changeLog.jiraTickets.size
        fields.addAll(
            splitIntoFields(
                header = "JIRA Tickets ($ticketCount)",
                items = changeLog.jiraTickets.map { ticket ->
                    "<https://${changeLog.jiraAppName}.atlassian.net/browse/$ticket|$ticket>"
                }
            )
        )
    }

    // Add job URL if available
    if (changeLog.jobUrl != null) {
        fields.add(
            SlackField(
                type = "mrkdwn",
                text = "*Job:*\n<${changeLog.jobUrl}|View Job>"
            )
        )
    }

    // Add triggered by if available
    if (changeLog.triggeredBy != null) {
        val username = changeLog.triggeredBy.removePrefix("@")
        val displayText = if (changeLog.triggeredByName != null) {
            "${changeLog.triggeredByName} (<https://github.com/$username|@$username>)"
        } else {
            "<https://github.com/$username|@$username>"
        }
        fields.add(
            SlackField(
                type = "mrkdwn",
                text = "*Triggered By:*\n$displayText"
            )
        )
    }

    // Add deployment stage if available
    if (changeLog.stage != null) {
        fields.add(
            SlackField(
                type = "mrkdwn",
                text = "*Stage:*\n`${changeLog.stage}`"
            )
        )
    }

    // Add Docker image information if available
    if (changeLog.dockerImage != null) {
        fields.add(
            SlackField(
                type = "mrkdwn",
                text = "*Docker Image:*\n`${changeLog.dockerImage}`"
            )
        )
    }

    if (changeLog.imageTag != null) {
        fields.add(
            SlackField(
                type = "mrkdwn",
                text = "*Image Tag:*\n`${changeLog.imageTag}`"
            )
        )
    }

    if (changeLog.previousImageTag != null) {
        fields.add(
            SlackField(
                type = "mrkdwn",
                text = "*Previous Image Tag (Rollback):*\n`${changeLog.previousImageTag}`"
            )
        )
    }

    // Add fields as section blocks, splitting if we exceed 10 fields per block (Slack's limit)
    if (fields.isNotEmpty()) {
        fields.chunked(10).forEach { fieldChunk ->
            blocks.add(
                SlackBlock(
                    type = "section",
                    fields = fieldChunk
                )
            )
        }
    }

    return blocks
}

/**
 * Adds commit blocks grouped by type to the current list of blocks.
 * Splits blocks that exceed Slack's 2000 character limit.
 */
internal fun MutableList<SlackBlock>.addCommitBlocks(
    commitsGroupedByType: Map<ConventionalCommitType, List<Commit>>,
    markdownFormatter: MarkdownFormatter.Slack,
    linkResolvers: List<LinkResolver>,
) {
    // Slack's limit is 3000 chars but we use 1900 to be safe with title overhead
    val maxCharsPerBlock = 1900

    commitsGroupedByType.map { (type, commits) ->
        val titleText = markdownFormatter.title("${type.emoji} ${type.title}")
        val titleLength = titleText.length

        // Build commit items
        val commitItems = commits.map { commit ->
            MarkdownFormatter.Slack.listItem(
                linkResolvers.resolve(
                    markdownFormatter = markdownFormatter,
                    message = commit.message
                )
            )
        }

        // Split commits into chunks that fit within the character limit
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder(titleText)

        commitItems.forEach { item ->
            if (currentChunk.length + item.length > maxCharsPerBlock) {
                // Current chunk is full, start a new one
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder(titleText)
            }
            currentChunk.append(item)
        }

        // Add the last chunk if it has content beyond the title
        if (currentChunk.length > titleLength) {
            chunks.add(currentChunk.toString())
        }

        // Add each chunk as a separate block
        chunks.forEach { chunk ->
            text { chunk }
        }
    }
}

internal fun MutableList<SlackBlock>.header(text: () -> String) {
    block {
        SlackBlock(
            type = "section",
            text = SlackText(
                type = "mrkdwn",
                text = "*${text()}*"
            )
        )
    }
}

internal fun MutableList<SlackBlock>.text(text: () -> String) {
    block {
        SlackBlock(
            type = "section",
            text = SlackText(
                type = "mrkdwn",
                text = text()
            )
        )
    }
}

internal fun MutableList<SlackBlock>.button(url: () -> String) {
    block {
        SlackBlock(
            type = "actions",
            elements = listOf(
                SlackBlock(
                    text = SlackText(
                        type = "plain_text",
                        text = "GitHub Release"
                    ),
                    url = url(),
                    type = "button"
                )
            )
        )
    }
}

internal fun MutableList<SlackBlock>.divider() {
    block {
        SlackBlock(
            type = "divider"
        )
    }
}

internal fun MutableList<SlackBlock>.block(block: () -> SlackBlock) {
    add(
        block()
    )
}

/**
 * Splits a list of items into multiple SlackField instances if the combined text
 * exceeds Slack's 2000 character limit per field.
 */
internal fun splitIntoFields(
    header: String,
    items: List<String>,
): List<SlackField> {
    // Slack's limit is 2000 chars but we use 1800 to be safe with header overhead
    val maxCharsPerField = 1800
    val fields = mutableListOf<SlackField>()

    val headerWithIndex = { index: Int ->
        if (index == 0) "*$header:*" else "*$header (cont'd):*"
    }

    var currentItems = mutableListOf<String>()
    var currentLength = headerWithIndex(0).length + 1 // +1 for newline
    var fieldIndex = 0

    items.forEach { item ->
        val itemWithNewline = item + "\n"

        if (currentLength + itemWithNewline.length > maxCharsPerField && currentItems.isNotEmpty()) {
            // Current field is full, create a new field
            fields.add(
                SlackField(
                    type = "mrkdwn",
                    text = "${headerWithIndex(fieldIndex)}\n${currentItems.joinToString("\n")}"
                )
            )
            fieldIndex++
            currentItems = mutableListOf()
            currentLength = headerWithIndex(fieldIndex).length + 1
        }

        currentItems.add(item)
        currentLength += itemWithNewline.length
    }

    // Add the last field if it has content
    if (currentItems.isNotEmpty()) {
        fields.add(
            SlackField(
                type = "mrkdwn",
                text = "${headerWithIndex(fieldIndex)}\n${currentItems.joinToString("\n")}"
            )
        )
    }

    return fields
}
