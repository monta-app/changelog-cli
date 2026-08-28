package com.monta.changelog.jira

import com.monta.changelog.log.jiraTicketLinks
import com.monta.changelog.log.pullRequestLinks
import com.monta.changelog.model.ChangeLog
import com.monta.changelog.model.DeployedSystem
import com.monta.changelog.model.isNotHealthy
import com.monta.changelog.util.DateTimeUtil
import kotlinx.serialization.Serializable

/**
 * Atlassian Document Format (ADF) — a nestable node tree. A node is either a
 * structural node (`content` holds child nodes) or a text node (`text` + `marks`).
 * Null fields are dropped on serialization (explicitNulls = false), so the same
 * class serves both shapes.
 */
@Serializable
internal data class AdfDocument(
    val type: String = "doc",
    val version: Int = 1,
    val content: List<AdfNode>,
)

@Serializable
internal data class AdfNode(
    val type: String,
    val content: List<AdfNode>? = null,
    val text: String? = null,
    val marks: List<AdfMark>? = null,
    val attrs: AdfNodeAttrs? = null,
)

@Serializable
internal data class AdfNodeAttrs(
    val level: Int? = null,
    val title: String? = null,
)

@Serializable
internal data class AdfMark(
    val type: String,
    val attrs: AdfMarkAttrs? = null,
)

@Serializable
internal data class AdfMarkAttrs(
    val href: String? = null,
)

/**
 * Builds the JIRA comment document: the markdown header + changelog and footer go
 * through the line-based converter, while deployed systems, tickets and PRs render
 * as native collapsible `expand` panels so the comment stays scannable.
 */
internal fun buildJiraCommentDocument(
    headerMarkdown: String,
    changelogMarkdown: String,
    footerMarkdown: String,
    changeLog: ChangeLog,
): AdfDocument {
    val nodes = mutableListOf<AdfNode>()
    nodes += markdownToAdfNodes(headerMarkdown)
    nodes += markdownToAdfNodes(changelogMarkdown)
    deployedSystemsExpand(changeLog)?.let { nodes += it }
    linkExpand("Deployed tickets", jiraTicketLinks(changeLog))?.let { nodes += it }
    linkExpand("Pull requests", pullRequestLinks(changeLog))?.let { nodes += it }
    nodes += markdownToAdfNodes(footerMarkdown)
    return AdfDocument(content = nodes)
}

/** A collapsible panel of deployed systems, one bullet per system, or null when there are none. */
internal fun deployedSystemsExpand(changeLog: ChangeLog): AdfNode? {
    val systems = changeLog.deployedSystems
    if (systems.isEmpty()) return null

    val items = systems.map { system ->
        listItem(parseInlineMarkdown(deployedSystemRow(system, changeLog.repositoryUrl)))
    }
    return expand("Deployed systems (${systems.size})", listOf(bulletList(items)))
}

/** A collapsible panel listing markdown links (one per line), or null when empty. */
internal fun linkExpand(title: String, links: List<String>): AdfNode? {
    if (links.isEmpty()) return null

    val items = links.map { listItem(parseInlineMarkdown(it)) }
    return expand("$title (${links.size})", listOf(bulletList(items)))
}

private fun deployedSystemRow(system: DeployedSystem, repositoryUrl: String?): String {
    val row = StringBuilder("**${system.name}**")
    if (system.status != null && system.isNotHealthy()) {
        row.append(" ⚠️ ${system.status}")
    }
    versionMarkdown(system, repositoryUrl)?.let { row.append(" — $it") }
    val start = DateTimeUtil.formatClock(system.start)
    val end = DateTimeUtil.formatClock(system.end)
    if (start != null && end != null) {
        row.append(" · $start → $end UTC")
    }
    return row.toString()
}

private fun versionMarkdown(system: DeployedSystem, repositoryUrl: String?): String? {
    val revision = system.revision?.takeIf { it.isNotBlank() } ?: return null
    val previous = system.previousRevision?.takeIf { it.isNotBlank() && it != revision }
    val newRef = commitMarkdown(revision, repositoryUrl)
    return if (previous == null) newRef else "${commitMarkdown(previous, repositoryUrl)} → $newRef"
}

private fun commitMarkdown(sha: String, repositoryUrl: String?): String {
    val short = sha.take(7)
    return if (repositoryUrl != null) "[$short]($repositoryUrl/commit/$sha)" else short
}

private fun expand(title: String, content: List<AdfNode>): AdfNode = AdfNode(type = "expand", attrs = AdfNodeAttrs(title = title), content = content)

private fun bulletList(items: List<AdfNode>): AdfNode = AdfNode(type = "bulletList", content = items)

private fun listItem(paragraphContent: List<AdfNode>): AdfNode = AdfNode(type = "listItem", content = listOf(AdfNode(type = "paragraph", content = paragraphContent)))

/**
 * Converts markdown text to ADF block nodes line by line: `---` rules, `#`/`##`/`###`
 * headings, everything else a paragraph. Empty lines are skipped (ADF has no empty paragraph).
 */
internal fun markdownToAdfNodes(markdown: String): List<AdfNode> = markdown.split("\n")
    .filter { it.isNotEmpty() }
    .map { line ->
        when {
            line.trim() == "---" -> AdfNode(type = "rule")
            line.startsWith("### ") -> AdfNode(type = "heading", attrs = AdfNodeAttrs(level = 3), content = parseInlineMarkdown(line.substring(4)))
            line.startsWith("## ") -> AdfNode(type = "heading", attrs = AdfNodeAttrs(level = 2), content = parseInlineMarkdown(line.substring(3)))
            line.startsWith("# ") -> AdfNode(type = "heading", attrs = AdfNodeAttrs(level = 1), content = parseInlineMarkdown(line.substring(2)))
            else -> AdfNode(type = "paragraph", content = parseInlineMarkdown(line))
        }
    }

/** Parses inline `[text](url)` links and `**bold**` into ADF text nodes with marks. */
internal fun parseInlineMarkdown(line: String): List<AdfNode> {
    val elements = mutableListOf<InlineElement>()

    Regex("""\[([^\]]+)]\(([^)]+)\)""").findAll(line).forEach { match ->
        elements.add(InlineElement(match.range.first, match.range.last + 1, match.groupValues[1], InlineType.LINK, match.groupValues[2]))
    }
    Regex("""\*\*([^*]+)\*\*""").findAll(line).forEach { match ->
        elements.add(InlineElement(match.range.first, match.range.last + 1, match.groupValues[1], InlineType.BOLD))
    }
    elements.sortBy { it.start }

    val result = mutableListOf<AdfNode>()
    var lastIndex = 0
    elements.forEach { element ->
        if (element.start > lastIndex) {
            result.add(textNode(line.substring(lastIndex, element.start)))
        }
        when (element.type) {
            InlineType.LINK -> result.add(textNode(element.text, listOf(AdfMark(type = "link", attrs = AdfMarkAttrs(href = element.url)))))
            InlineType.BOLD -> result.add(textNode(element.text, listOf(AdfMark(type = "strong"))))
        }
        lastIndex = element.end
    }
    if (lastIndex < line.length) {
        result.add(textNode(line.substring(lastIndex)))
    }
    if (result.isEmpty()) {
        result.add(textNode(line))
    }
    return result
}

private fun textNode(text: String, marks: List<AdfMark>? = null): AdfNode = AdfNode(type = "text", text = text, marks = marks)

private enum class InlineType { LINK, BOLD }

private data class InlineElement(
    val start: Int,
    val end: Int,
    val text: String,
    val type: InlineType,
    val url: String? = null,
)
