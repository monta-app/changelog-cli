package com.monta.changelog.printer

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.util.LinkResolver
import com.monta.changelog.util.MarkdownFormatter
import com.monta.changelog.util.resolve

class ConsoleChangeLogPrinter : ChangeLogPrinter {
    override suspend fun print(linkResolvers: List<LinkResolver>, changeLog: ChangeLog): ChangeLogPrinter.PrintResult? {
        println(
            buildString {
                changeLog.groupedCommitMap.forEach { (scope, commitsGroupedByType) ->
                    if (scope != null) {
                        appendLine()
                        append(
                            MarkdownFormatter.GitHub.header(
                                (scope).replaceFirstChar { char ->
                                    char.uppercaseChar()
                                }
                            )
                        )
                        appendLine()
                    }
                    commitsGroupedByType.forEach { (type, commits) ->
                        append(
                            MarkdownFormatter.GitHub.title("${type.emoji} ${type.title}")
                        )
                        commits.forEach { commit ->
                            append(
                                MarkdownFormatter.GitHub.listItem(
                                    linkResolvers.resolve(
                                        markdownFormatter = MarkdownFormatter.GitHub,
                                        message = commit.message
                                    )
                                )
                            )
                        }
                    }
                    appendLine()
                }
            }
        )
        return null
    }
}
