package com.monta.changelog.printer.slack

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.model.Commit
import com.monta.changelog.model.ConventionalCommitType
import com.monta.changelog.util.DateTimeUtil
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
 * Builds metadata blocks and attachments for the threaded message.
 * Contains additional information about the changelog like repository link.
 * PRs and JIRA tickets are returned as attachments with colored bars.
 */
internal fun buildMetadataBlocks(changeLog: ChangeLog): SlackMessageComponents {
    val blocks = mutableListOf<SlackBlock>()
    val attachments = mutableListOf<SlackAttachment>()

    // Add deployment summary if available
    addDeploymentSummary(changeLog, blocks)

    // Build main information fields
    val fields = buildMetadataFields(changeLog)

    // Add PR and JIRA attachments
    addPullRequestAttachments(changeLog, attachments)
    addJiraTicketAttachments(changeLog, attachments)
    addTechnicalDetailsAttachment(changeLog, attachments)

    // Add fields as section blocks
    addFieldBlocks(fields, blocks)

    return SlackMessageComponents(
        blocks = blocks,
        attachments = attachments
    )
}

/**
 * Adds release/deployment summary section to blocks.
 * Shows deployment info if available, otherwise shows release info.
 */
private fun addDeploymentSummary(changeLog: ChangeLog, blocks: MutableList<SlackBlock>) {
    val hasDeploymentTimes = changeLog.deploymentStartTime != null && changeLog.deploymentEndTime != null

    val summaryText = if (hasDeploymentTimes) {
        // Deployment with timing information
        val timeRange = DateTimeUtil.formatTimeRange(
            changeLog.deploymentStartTime,
            changeLog.deploymentEndTime
        ) ?: "${changeLog.deploymentStartTime} → ${changeLog.deploymentEndTime}"

        val stageText = if (changeLog.stage != null) {
            " to *${changeLog.stage.replaceFirstChar { it.uppercaseChar() }}*"
        } else {
            ""
        }

        "Deployed$stageText $timeRange"
    } else {
        // Release without deployment timing
        "Released *${changeLog.tagName}*"
    }

    val links = buildSummaryLinks(changeLog)
    val linksText = if (links.isNotEmpty()) " • ${links.joinToString(" • ")}" else ""
    val triggeredByText = buildTriggeredByText(changeLog)

    blocks.add(
        SlackBlock(
            type = "section",
            text = SlackText(
                type = "mrkdwn",
                text = "_${summaryText}_$linksText$triggeredByText"
            )
        )
    )
    blocks.add(SlackBlock(type = "divider"))
}

/**
 * Builds summary links (Repository, Changeset, Deployment, Job).
 */
private fun buildSummaryLinks(changeLog: ChangeLog): List<String> {
    val links = mutableListOf<String>()
    if (changeLog.repositoryUrl != null) {
        links.add("<${changeLog.repositoryUrl}|Repository>")
    }
    if (changeLog.repositoryUrl != null && changeLog.previousTagName != null) {
        val compareUrl = "${changeLog.repositoryUrl}/compare/${changeLog.previousTagName}...${changeLog.tagName}"
        links.add("<$compareUrl|Changeset>")
    }
    if (changeLog.deploymentUrl != null) {
        links.add("<${changeLog.deploymentUrl}|Deployment>")
    }
    if (changeLog.jobUrl != null) {
        links.add("<${changeLog.jobUrl}|Job>")
    }
    return links
}

/**
 * Builds triggered by text for deployment summary.
 */
private fun buildTriggeredByText(changeLog: ChangeLog): String {
    if (changeLog.triggeredBy == null) return ""

    val username = changeLog.triggeredBy.removePrefix("@")
    val displayText = if (changeLog.triggeredByName != null) {
        "${changeLog.triggeredByName} (<https://github.com/$username|@$username>)"
    } else {
        "<https://github.com/$username|@$username>"
    }
    return " • $displayText"
}

/**
 * Builds metadata fields (stage only, if no deployment summary with timing).
 * Repository and Triggered By are now always shown in the summary line.
 */
private fun buildMetadataFields(changeLog: ChangeLog): MutableList<SlackField> {
    val fields = mutableListOf<SlackField>()
    val hasDeploymentTiming = changeLog.deploymentStartTime != null && changeLog.deploymentEndTime != null

    // Add stage field only if no deployment timing (since stage is included in deployment summary)
    if (!hasDeploymentTiming) {
        addStageField(changeLog, fields)
    }

    return fields
}

/**
 * Adds stage field if available.
 */
private fun addStageField(changeLog: ChangeLog, fields: MutableList<SlackField>) {
    if (changeLog.stage == null) return

    fields.add(
        SlackField(
            type = "mrkdwn",
            text = "*Stage/Environment:*\n`${changeLog.stage}`"
        )
    )
}

/**
 * Adds container information attachment with Docker brand color if any Docker info is available.
 */
private fun addTechnicalDetailsAttachment(changeLog: ChangeLog, attachments: MutableList<SlackAttachment>) {
    val containerItems = mutableListOf<String>()
    if (changeLog.dockerImage != null) {
        containerItems.add("Image: `${changeLog.dockerImage}`")
    }
    if (changeLog.imageTag != null) {
        containerItems.add("Tag: `${changeLog.imageTag}`")
    }
    if (changeLog.previousImageTag != null) {
        containerItems.add("Previous Tag: `${changeLog.previousImageTag}`")
    }

    if (containerItems.isEmpty()) return

    attachments.addAll(
        splitIntoAttachments(
            header = "Container information",
            items = containerItems,
            color = "#2563ED" // Docker brand color
        )
    )
}

/**
 * Adds pull request attachments with GitHub brand color.
 */
private fun addPullRequestAttachments(changeLog: ChangeLog, attachments: MutableList<SlackAttachment>) {
    if (changeLog.pullRequests.isEmpty() || changeLog.repositoryUrl == null) return

    val prCount = changeLog.pullRequests.size
    val prLinks = changeLog.pullRequests.map { prNumber ->
        "<${changeLog.repositoryUrl}/pull/$prNumber|#$prNumber>"
    }
    attachments.addAll(
        splitIntoAttachments(
            header = "Pull Requests ($prCount)",
            items = prLinks,
            color = "#1F2328" // GitHub brand color
        )
    )
}

/**
 * Adds JIRA ticket attachments with Jira brand color.
 */
private fun addJiraTicketAttachments(changeLog: ChangeLog, attachments: MutableList<SlackAttachment>) {
    if (changeLog.jiraTickets.isEmpty() || changeLog.jiraAppName == null) return

    val ticketCount = changeLog.jiraTickets.size
    val ticketLinks = changeLog.jiraTickets.map { ticket ->
        "<https://${changeLog.jiraAppName}.atlassian.net/browse/$ticket|$ticket>"
    }
    attachments.addAll(
        splitIntoAttachments(
            header = "JIRA Tickets ($ticketCount)",
            items = ticketLinks,
            color = "#2068DB" // Jira brand color
        )
    )
}

/**
 * Adds field blocks to blocks list, splitting if we exceed 10 fields per block.
 */
private fun addFieldBlocks(fields: List<SlackField>, blocks: MutableList<SlackBlock>) {
    if (fields.isEmpty()) return

    fields.chunked(10).forEach { fieldChunk ->
        blocks.add(
            SlackBlock(
                type = "section",
                fields = fieldChunk
            )
        )
    }
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
 * Splits a list of items into multiple SlackAttachment instances if the combined text
 * exceeds Slack's attachment text limit.
 */
internal fun splitIntoAttachments(
    header: String,
    items: List<String>,
    color: String,
): List<SlackAttachment> {
    // Slack's attachment text limit is 3000 chars but we use 2500 to be safe with header overhead
    val maxCharsPerAttachment = 2500
    val attachments = mutableListOf<SlackAttachment>()

    val headerWithIndex = { index: Int ->
        if (index == 0) "*$header:*" else "*$header (cont'd):*"
    }

    var currentItems = mutableListOf<String>()
    var currentLength = headerWithIndex(0).length + 1 // +1 for newline
    var attachmentIndex = 0

    items.forEach { item ->
        val itemWithNewline = item + "\n"

        if (currentLength + itemWithNewline.length > maxCharsPerAttachment && currentItems.isNotEmpty()) {
            // Current attachment is full, create a new attachment
            attachments.add(
                SlackAttachment(
                    color = color,
                    text = "${headerWithIndex(attachmentIndex)}\n${currentItems.joinToString("\n")}"
                )
            )
            attachmentIndex++
            currentItems = mutableListOf()
            currentLength = headerWithIndex(attachmentIndex).length + 1
        }

        currentItems.add(item)
        currentLength += itemWithNewline.length
    }

    // Add the last attachment if it has content
    if (currentItems.isNotEmpty()) {
        attachments.add(
            SlackAttachment(
                color = color,
                text = "${headerWithIndex(attachmentIndex)}\n${currentItems.joinToString("\n")}"
            )
        )
    }

    return attachments
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
