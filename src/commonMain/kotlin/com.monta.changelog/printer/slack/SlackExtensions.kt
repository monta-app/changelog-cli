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
    // Add visual separator
    currentChunk.divider()

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

    // Add repository field if available
    if (changeLog.repositoryUrl != null) {
        blocks.add(
            SlackBlock(
                type = "section",
                fields = listOf(
                    SlackField(
                        type = "mrkdwn",
                        text = "*Repository:*\n<${changeLog.repositoryUrl}|${changeLog.serviceName}>"
                    )
                )
            )
        )
    }

    return blocks
}

/**
 * Adds commit blocks grouped by type to the current list of blocks.
 */
internal fun MutableList<SlackBlock>.addCommitBlocks(
    commitsGroupedByType: Map<ConventionalCommitType, List<Commit>>,
    markdownFormatter: MarkdownFormatter.Slack,
    linkResolvers: List<LinkResolver>,
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
