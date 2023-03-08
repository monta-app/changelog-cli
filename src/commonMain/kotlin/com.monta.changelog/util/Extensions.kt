package com.monta.changelog.util

import com.monta.changelog.model.Commit
import com.monta.changelog.model.ConventionalCommitType

typealias GroupedCommitMap = Map<String?, Map<ConventionalCommitType, List<Commit>>>

fun List<LinkResolver>.resolve(markdownFormatter: MarkdownFormatter, message: String): String {
    var newMessage = message
    this.forEach { linkResolver ->
        newMessage = linkResolver.resolve(markdownFormatter, message)
    }
    return newMessage
}
