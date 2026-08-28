package com.monta.changelog.printer.slack

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.model.Commit
import com.monta.changelog.model.ConventionalCommitType
import com.monta.changelog.model.DeployOutcome
import com.monta.changelog.model.DeployedSystem
import com.monta.changelog.model.outcome
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

    deployedSystemsSummaryLine(changeLog.deployedSystems)?.let { line ->
        currentChunk.add(
            SlackBlock(
                type = "section",
                text = SlackText(type = "mrkdwn", text = line)
            )
        )
    }

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

    // Deploy outcome first (multi-service systems, or single-service timing), then container info, JIRA, PRs, non-conventional
    if (changeLog.deployedSystems.isNotEmpty()) {
        addDeployedSystemsAttachment(changeLog, attachments)
        addContainersAttachment(changeLog, attachments)
    } else {
        if (isServiceDeployment(changeLog)) {
            addDeploymentAttachment(changeLog, attachments)
        }
        addTechnicalDetailsAttachment(changeLog, attachments)
    }
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
    changeLog.previousImageTag != null ||
    changeLog.deployedSystems.isNotEmpty()

/**
 * Adds release/deployment summary section to blocks.
 * Shows deployment info if available, otherwise shows release info.
 * Distinguishes between service deployments (with Docker info) and library/CLI releases.
 */
private fun addDeploymentSummary(changeLog: ChangeLog, blocks: MutableList<SlackBlock>) {
    val isServiceDeployment = isServiceDeployment(changeLog)
    val isDeployed = (changeLog.deploymentStartTime != null && changeLog.deploymentEndTime != null) ||
        changeLog.deployedSystems.isNotEmpty()

    val summaryText = when {
        isServiceDeployment && isDeployed -> {
            val stageText = changeLog.stage?.let { " to *${it.replaceFirstChar { char -> char.uppercaseChar() }}*" } ?: ""
            "🚀 Deployed *${changeLog.tagName}*$stageText"
        }
        isServiceDeployment -> "Released *${changeLog.tagName}* (⏳ Deployment pending)"
        else -> "Released *${changeLog.tagName}*"
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

private fun addDeploymentAttachment(changeLog: ChangeLog, attachments: MutableList<SlackAttachment>) {
    val window = DateTimeUtil.formatTimeRange(changeLog.deploymentStartTime, changeLog.deploymentEndTime) ?: return
    val duration = DateTimeUtil.formatDuration(changeLog.deploymentStartTime, changeLog.deploymentEndTime)
    val item = if (duration != null) "$window · *$duration*" else window
    attachments.addAll(
        splitIntoAttachments(
            header = "Deployment",
            items = listOf(item),
            color = "#2EB67D" // green — healthy
        )
    )
}

private fun addDeployedSystemsAttachment(changeLog: ChangeLog, attachments: MutableList<SlackAttachment>) {
    val systems = changeLog.deployedSystems
    if (systems.isEmpty()) return

    // The overall window only adds information across multiple systems; for a single
    // system it just repeats that system's own row, so show the duration on the row instead.
    val isSingle = systems.size == 1
    val items = mutableListOf<String>()
    if (!isSingle) {
        deployedSystemsOverviewLine(systems)?.let { items.add(it) }
    }
    systems.forEach { system ->
        val name = deployedSystemName(system)
        val suffix = deployRowSuffix(system, withDuration = isSingle)
        items.add(if (suffix.isEmpty()) "• $name" else "• $name — $suffix")
    }

    val color = when (systems.outcome()) {
        DeployOutcome.HEALTHY -> "#2EB67D" // green
        DeployOutcome.PARTIAL -> "#ECB22E" // yellow
        DeployOutcome.FAILED -> "#E01E5A" // red
    }

    attachments.addAll(
        splitIntoAttachments(
            header = "Deployed systems (${systems.size})",
            items = items,
            color = color
        )
    )
}

/** Overall rollout window (earliest start → latest end) and total duration across systems. */
private fun deployedSystemsOverviewLine(systems: List<DeployedSystem>): String? {
    val overallStart = systems.mapNotNull { it.start }.minOrNull()
    val overallEnd = systems.mapNotNull { it.end }.maxOrNull()
    val start = overallStart?.let { DateTimeUtil.formatClock(it) }
    val end = overallEnd?.let { DateTimeUtil.formatClock(it) }
    if (start == null || end == null) return null

    val duration = DateTimeUtil.formatDuration(overallStart, overallEnd)
    val durationSuffix = if (duration != null) " · *$duration*" else ""
    return "⏱️ $start → $end UTC$durationSuffix"
}

private fun addContainersAttachment(changeLog: ChangeLog, attachments: MutableList<SlackAttachment>) {
    val items = changeLog.deployedSystems.mapNotNull { system ->
        containerRow(system, changeLog.repositoryUrl)
    }
    if (items.isEmpty()) return

    attachments.addAll(
        splitIntoAttachments(
            header = "Containers (${items.size})",
            items = items,
            color = "#575757" // containerd grey
        )
    )
}

private fun containerRow(system: DeployedSystem, repositoryUrl: String?): String? {
    val revision = system.revision?.takeIf { it.isNotBlank() } ?: return null
    val previous = system.previousRevision?.takeIf { it.isNotBlank() && it != revision }
    val newRef = commitLink(revision, repositoryUrl)
    if (previous == null) {
        return "• *${system.name}* — $newRef"
    }
    return "• *${system.name}* — ${commitLink(previous, repositoryUrl)} → $newRef"
}

private fun commitLink(sha: String, repositoryUrl: String?): String {
    val short = sha.take(7)
    return if (repositoryUrl != null) "<$repositoryUrl/commit/$sha|`$short`>" else "`$short`"
}

/** The system name, bold, linked to its ArgoCD app when a URL is known. */
private fun deployedSystemName(system: DeployedSystem): String {
    val name = "*${system.name}*"
    val url = system.url?.takeIf { it.isNotBlank() } ?: return name
    return "<$url|$name>"
}

private fun deployRowSuffix(system: DeployedSystem, withDuration: Boolean = false): String {
    val parts = mutableListOf<String>()
    if (system.status != null && !system.status.equals("healthy", ignoreCase = true)) {
        parts.add("⚠️ ${system.status}")
    }
    val start = DateTimeUtil.formatClock(system.start)
    val end = DateTimeUtil.formatClock(system.end)
    if (start != null && end != null) {
        val duration = if (withDuration) DateTimeUtil.formatDuration(system.start, system.end) else null
        val durationSuffix = if (duration != null) " · *$duration*" else ""
        parts.add("$start → $end UTC$durationSuffix")
    }
    return parts.joinToString(" · ")
}

private fun deployedSystemsSummaryLine(systems: List<DeployedSystem>): String? {
    if (systems.isEmpty()) return null
    val shown = systems.take(3).joinToString(", ") { it.name }
    val extra = systems.size - minOf(systems.size, 3)
    val suffix = if (extra > 0) " +$extra" else ""
    val noun = if (systems.size == 1) "system" else "systems"
    return "📦 *${systems.size} $noun:* $shown$suffix"
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
    // The API accepts very large attachment text, but the Slack client stops
    // rendering mrkdwn and truncates mid-link around ~7.8k chars. Stay under that
    // so a card renders whole; beyond it we split into a "(cont'd)" attachment.
    // 6000 keeps a 15-system Containers card (two commit links per row, ~3.7k) a
    // single attachment with headroom (~25 rows), matching the deployed-systems card.
    val maxCharsPerAttachment = 6000
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
