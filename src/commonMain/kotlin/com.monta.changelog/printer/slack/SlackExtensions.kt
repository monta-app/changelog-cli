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

    for ((scope, commitsGroupedByType) in changeLog.groupedCommitMap) {
        if (scope == null) {
            // Add title with link (if available)
            currentChunk.text {
                "*${formatSlackTitle(changeLog)}*"
            }
            // Add visual separator
            currentChunk.divider()
        } else {
            currentChunk.header {
                (scope).replaceFirstChar { char ->
                    char.uppercaseChar()
                }
            }
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
 * Formats the changelog title for Slack with optional repository link.
 * If repository URL is available, only the service name is linked.
 */
internal fun formatSlackTitle(changeLog: ChangeLog): String = if (changeLog.repositoryUrl != null) {
    // Link only the service name, not the version
    "<${changeLog.repositoryUrl}|${changeLog.serviceName}> Release ${changeLog.tagName}"
} else {
    // Capitalize title when no link is available
    changeLog.title.split(" ").joinToString(" ") {
        it.replaceFirstChar { char -> char.uppercaseChar() }
    }
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
