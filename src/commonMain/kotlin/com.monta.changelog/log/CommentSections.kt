package com.monta.changelog.log

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.model.DeployedSystem
import com.monta.changelog.model.isNotHealthy
import com.monta.changelog.model.overallWindow
import com.monta.changelog.util.DateTimeUtil

/**
 * Markdown sections shared by the GitHub PR comment. Kept as pure functions over
 * [ChangeLog] so they render identically regardless of how the service is wired.
 */

/**
 * The deployment window to report: the scalar start/end when set (single-service),
 * otherwise derived from the per-system rollout times (multi-service). Null when neither.
 */
internal fun effectiveDeploymentWindow(changeLog: ChangeLog): Pair<String, String>? {
    if (changeLog.deploymentStartTime != null && changeLog.deploymentEndTime != null) {
        return changeLog.deploymentStartTime to changeLog.deploymentEndTime
    }
    return changeLog.deployedSystems.overallWindow()
}

/** "Aug 27, 2026 at 18:26:30 UTC → 18:31:02 UTC (4m 32s)", or null when there's no window. */
internal fun deploymentWindowLabel(changeLog: ChangeLog): String? {
    val (start, end) = effectiveDeploymentWindow(changeLog) ?: return null
    val range = DateTimeUtil.formatTimeRange(start, end) ?: "$start → $end"
    val duration = DateTimeUtil.formatDuration(start, end)
    return if (duration != null) "$range ($duration)" else range
}

/**
 * Renders the deployed systems as a collapsed GitHub table (System · Version · Rolled out).
 * Empty for single-service releases (no per-system data).
 */
internal fun deployedSystemsSection(changeLog: ChangeLog): String {
    val systems = changeLog.deployedSystems
    if (systems.isEmpty()) return ""

    return buildString {
        appendLine("<details><summary><b>Deployed systems (${systems.size})</b></summary>")
        appendLine()
        appendLine("| System | Version | Rolled out |")
        appendLine("| --- | --- | --- |")
        systems.forEach { system ->
            appendLine("| ${systemNameCell(system)} | ${versionCell(system, changeLog.repositoryUrl)} | ${rolloutCell(system)} |")
        }
        appendLine()
        appendLine("</details>")
        appendLine()
    }
}

/** A collapsed section listing one markdown link per line; empty when there are no items. */
internal fun foldableLinkSection(title: String, links: List<String>): String {
    if (links.isEmpty()) return ""

    return buildString {
        appendLine("<details><summary><b>$title (${links.size})</b></summary>")
        appendLine()
        links.forEach { appendLine("- $it") }
        appendLine()
        appendLine("</details>")
        appendLine()
    }
}

internal fun jiraTicketLinks(changeLog: ChangeLog): List<String> {
    val appName = changeLog.jiraAppName ?: return changeLog.jiraTickets
    return changeLog.jiraTickets.map { "[$it](https://$appName.atlassian.net/browse/$it)" }
}

internal fun pullRequestLinks(changeLog: ChangeLog): List<String> {
    val repositoryUrl = changeLog.repositoryUrl ?: return changeLog.pullRequests.map { "#$it" }
    return changeLog.pullRequests.map { "[#$it]($repositoryUrl/pull/$it)" }
}

private fun systemNameCell(system: DeployedSystem): String {
    val warning = if (system.status != null && system.isNotHealthy()) " ⚠️ ${system.status}" else ""
    val name = "**${system.name}**"
    val linked = system.url?.takeIf { it.isNotBlank() }?.let { "[$name]($it)" } ?: name
    return "$linked$warning"
}

private fun versionCell(system: DeployedSystem, repositoryUrl: String?): String {
    val revision = system.revision?.takeIf { it.isNotBlank() } ?: return "—"
    val previous = system.previousRevision?.takeIf { it.isNotBlank() && it != revision }
    val newRef = commitLink(revision, repositoryUrl)
    return if (previous == null) newRef else "${commitLink(previous, repositoryUrl)} → $newRef"
}

private fun rolloutCell(system: DeployedSystem): String {
    val start = DateTimeUtil.formatClock(system.start)
    val end = DateTimeUtil.formatClock(system.end)
    return if (start != null && end != null) "$start → $end UTC" else "—"
}

private fun commitLink(sha: String, repositoryUrl: String?): String {
    val short = sha.take(7)
    return if (repositoryUrl != null) "[`$short`]($repositoryUrl/commit/$sha)" else "`$short`"
}
