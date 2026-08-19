package com.monta.changelog.notify

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.printer.slack.SlackBlock
import com.monta.changelog.printer.slack.SlackText

/**
 * How long we ask an author to keep an eye on the dashboards after their change goes out.
 */
private const val MONITORING_WINDOW = "the next 30 minutes"

/**
 * True once the deployment has actually finished. Both ends of the timing have to be known,
 * matching how the PR and Jira comments tell "Deployed" from "Deployment pending" - we don't want
 * to tell someone their change is live while it's still rolling out.
 */
internal val ChangeLog.isDeploymentComplete: Boolean
    get() = deploymentStartTime != null && deploymentEndTime != null

/**
 * Builds the DM's opening line. Says the release is deployed once it actually is, and otherwise
 * says it's on the way, so the message is honest either way about what the author is looking at.
 */
internal fun buildAuthorDmHeadline(changeLog: ChangeLog): String {
    val releaseUrl = changeLog.githubReleaseUrl
        ?: changeLog.repositoryUrl?.let { "$it/releases/tag/${changeLog.tagName}" }
    val release = if (releaseUrl != null) {
        "<$releaseUrl|${changeLog.tagName}>"
    } else {
        changeLog.tagName
    }
    val stageText = changeLog.stage
        ?.let { stage -> " to ${stage.replaceFirstChar { char -> char.uppercaseChar() }}" }
        ?: ""

    return if (changeLog.isDeploymentComplete) {
        ":rocket: *Your changes are live* - ${changeLog.serviceName} release $release is now deployed$stageText."
    } else {
        ":hourglass_flowing_sand: *Your changes are on the way* - " +
            "${changeLog.serviceName} release $release is being deployed$stageText now."
    }
}

/**
 * Builds the monitoring ask, listing the dashboards the author should watch.
 */
internal fun buildAuthorDmMonitoringAsk(monitoringUrls: List<MonitoringUrl>): String {
    val dashboardLines = monitoringUrls.joinToString("\n") { "• <${it.url}|${it.label}>" }
    return "Please keep an eye out for errors over $MONITORING_WINDOW:\n$dashboardLines"
}

/**
 * Builds the DM sent to one author: what shipped, that it's deployed, and where to watch it.
 * The author's own pull requests are listed so a busy release still tells them which change of
 * theirs to keep an eye on.
 */
internal fun buildAuthorDmBlocks(
    changeLog: ChangeLog,
    monitoringUrls: List<MonitoringUrl>,
    prLinks: String,
): List<SlackBlock> = buildList {
    add(dmSectionBlock(buildAuthorDmHeadline(changeLog)))
    add(dmSectionBlock(buildAuthorDmMonitoringAsk(monitoringUrls)))

    if (prLinks.isNotBlank()) {
        add(dmSectionBlock("Your pull requests in this release: $prLinks"))
    }
}

/**
 * The plain-text fallback, which is what Slack shows in the sidebar and in a push notification -
 * for a DM that is often the only part the author reads before deciding to open it.
 */
internal fun buildAuthorDmFallbackText(changeLog: ChangeLog): String = if (changeLog.isDeploymentComplete) {
    "Your changes are live in ${changeLog.serviceName} release ${changeLog.tagName} - please monitor for errors"
} else {
    "Your changes are being deployed in ${changeLog.serviceName} release ${changeLog.tagName} - please monitor for errors"
}

private fun dmSectionBlock(text: String) = SlackBlock(
    type = "section",
    text = SlackText(type = "mrkdwn", text = text)
)
