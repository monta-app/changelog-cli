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

    // Add attachments (container info first, then JIRA, then PRs, then non-conventional commits)
    addTechnicalDetailsAttachment(changeLog, attachments)
    addJiraTicketAttachments(changeLog, attachments)
    addPullRequestAttachments(changeLog, attachments)
    addNonConventionalCommitsAttachment(changeLog, attachments)

    // Add fields as section blocks
    addFieldBlocks(fields, blocks)

    return SlackMessageComponents(
        blocks = blocks,
        attachments = attachments
    )
}

/**
 * Determines if this is a service deployment (has container/Docker info)
 * vs a library/CLI release (no container info).
 */
private fun isServiceDeployment(changeLog: ChangeLog): Boolean = changeLog.dockerImage != null ||
    changeLog.imageTag != null ||
    changeLog.previousImageTag != null

/**
 * Adds release/deployment summary section to blocks.
 * Shows deployment info if available, otherwise shows release info.
 * Distinguishes between service deployments (with Docker info) and library/CLI releases.
 */
private fun addDeploymentSummary(changeLog: ChangeLog, blocks: MutableList<SlackBlock>) {
    val hasDeploymentTimes = changeLog.deploymentStartTime != null && changeLog.deploymentEndTime != null
    val isServiceDeployment = isServiceDeployment(changeLog)

    val summaryText = if (hasDeploymentTimes) {
        val timeRange = DateTimeUtil.formatTimeRange(
            changeLog.deploymentStartTime,
            changeLog.deploymentEndTime
        ) ?: "${changeLog.deploymentStartTime} → ${changeLog.deploymentEndTime}"

        if (isServiceDeployment) {
            // Service with container - use "Deployed" with rocket emoji and stage
            val stageText = if (changeLog.stage != null) {
                " to *${changeLog.stage.replaceFirstChar { it.uppercaseChar() }}*"
            } else {
                ""
            }
            "🚀 Deployed *${changeLog.tagName}*$stageText $timeRange"
        } else {
            // Library/CLI - use "Released" without stage
            "Released *${changeLog.tagName}* $timeRange"
        }
    } else {
        // Release without deployment timing
        if (isServiceDeployment) {
            // Service without timing - deployment is pending
            "Released *${changeLog.tagName}* (⏳ Deployment pending)"
        } else {
            // Library/CLI without timing - just released
            "Released *${changeLog.tagName}*"
        }
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
 * Builds metadata fields.
 * Repository and Triggered By are now always shown in the summary line.
 * Stage is shown in deployment summary when deployment timing is available.
 */
private fun buildMetadataFields(changeLog: ChangeLog): MutableList<SlackField> {
    // No fields needed - everything is in summary or attachments
    return mutableListOf()
}

/**
 * Adds container information attachment with containerd grey color if any Docker info is available.
 */
private fun addTechnicalDetailsAttachment(changeLog: ChangeLog, attachments: MutableList<SlackAttachment>) {
    val containerItems = mutableListOf<String>()
    if (changeLog.dockerImage != null) {
        containerItems.add("Image: `${changeLog.dockerImage}`")
    }
    if (changeLog.imageTag != null) {
        containerItems.add("Deployed: `${changeLog.imageTag}`")
    }
    if (changeLog.previousImageTag != null) {
        containerItems.add("Previous: `${changeLog.previousImageTag}`")
    }

    if (containerItems.isEmpty()) return

    attachments.addAll(
        splitIntoAttachments(
            header = "Container information",
            items = containerItems,
            color = "#575757" // Containerd grey
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
 * Adds non-conventional commits attachment with warning color.
 * Shows commits that didn't follow conventional commit syntax in a git shortlog format.
 */
private fun addNonConventionalCommitsAttachment(changeLog: ChangeLog, attachments: MutableList<SlackAttachment>) {
    if (changeLog.nonConventionalCommits.isEmpty()) return

    val commitCount = changeLog.nonConventionalCommits.size
    val commitLines = changeLog.nonConventionalCommits.map { commit ->
        val shortSha = commit.sha.take(7)
        val repoUrl = changeLog.repositoryUrl
        val commitLink = if (repoUrl != null) {
            "<$repoUrl/commit/${commit.sha}|$shortSha>"
        } else {
            "`$shortSha`"
        }
        "$commitLink ${commit.subject} (${commit.author})"
    }

    attachments.addAll(
        splitIntoAttachments(
            header = "⚠️ Non-Conventional Commits ($commitCount)",
            items = commitLines,
            color = "#FFA500" // Warning orange color
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
