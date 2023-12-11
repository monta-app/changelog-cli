package com.monta.changelog.git

import com.monta.changelog.model.Commit
import com.monta.changelog.model.ConventionalCommitType
import com.monta.changelog.util.DebugLogger

internal class CommitMapper {

    fun fromGitLogItem(
        logItem: LogItem,
    ): Commit? {
        return fromString(
            logItem.commit,
            logItem.subject
        )
    }

    private fun fromString(
        id: String,
        message: String,
    ): Commit? {
        val splitMessage = message.split(":", limit = 2)

        if (splitMessage.size <= 1) {
            DebugLogger.warn("commit did not match conventional commit syntax: $id")
            return null
        }

        var scope: String? = null
        var breaking = false
        val commitMessage = splitMessage[1].trim()
        val commitMeta = splitMessage[0].trim()
            .findIfBreaking {
                breaking = it
            }
            .findScope {
                scope = it
            }

        val type = ConventionalCommitType.fromString(commitMeta)

        if (type == null) {
            DebugLogger.warn("no conventional commit prefix associated with commit: $id")
            return null
        }

        return Commit(
            type = type,
            scope = scope,
            breaking = breaking,
            message = commitMessage
        )
    }

    private fun String.findIfBreaking(block: (Boolean) -> Unit): String {
        if (this.contains("!")) {
            block(true)
            return this.replace("!", "")
        }
        block(false)
        return this
    }

    private fun String.findScope(block: (String) -> Unit): String {
        val (newStringValue, parentheses) = this.extractParentheses()

        parentheses.firstOrNull()?.let { firstParenthesis ->
            block(firstParenthesis)
        }

        return newStringValue
    }

    private fun String.extractParentheses(): Pair<String, List<String>> {
        var sentence = this

        val parentheses = mutableListOf<String>()

        var innerValue = sentence.extractParenthesis()

        // As long as we don't have a negative index on the
        // startOf or endOf we should continue searching for
        // Parentheses to extract
        while (innerValue != null) {
            // Add our innerValue to our return list
            parentheses.add(innerValue)
            // Replace the innerValue value with ""
            sentence = sentence.replace(innerValue, "")
            // Find new value
            innerValue = sentence.extractParenthesis()
        }

        return sentence to parentheses.map { it.stripParenthesis() }
    }

    private fun String.extractParenthesis(): String? {
        val startOf = this.indexOf("(")
        val endOf = this.indexOf(")")
        if (startOf == -1 || endOf == -1) {
            return null
        }
        // + 1 so you include the final ')'
        return this.substring(startOf, endOf + 1)
    }

    private fun String.stripParenthesis(): String {
        return this.replace("(", "").replace(")", "")
    }
}
