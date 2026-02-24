package com.monta.changelog.git

import com.monta.changelog.util.DebugLogger

internal class GitCommandUtil {

    private val commandExecutor: CommandExecutor = createCommandExecutor()

    // Delimiter-based format using ASCII Record Separator (\x1E).
    // Fields: commit hash, subject, parents, author name, author email, author date, committer name, committer email, committer date
    private val logFormat = "%H%x1e%s%x1e%P%x1e%aN%x1e%aE%x1e%aI%x1e%cN%x1e%cE%x1e%cI"

    private val commitBodyDelimiter = "---COMMIT-BODY-END---"

    fun getHeadSha(): String = executeCommand("git rev-parse --verify HEAD").first()

    fun getTags(): List<String> = executeCommand("git tag").map { tag ->
        tag.trim()
    }

    fun getFilesInCommit(commitId: String): List<String> = executeCommand("git diff-tree --no-commit-id --name-only $commitId -r").map { file ->
        file.trim()
    }

    fun getLogs(): List<LogItem> = parseLogsWithBody(
        executeCommand(
            buildString {
                append("git log ")
                append("--pretty=format:'$logFormat%n%B%n$commitBodyDelimiter'")
            }
        )
    )

    fun getLogs(latestTag: String, previousTag: String): List<LogItem> = parseLogsWithBody(
        executeCommand(
            buildString {
                append("git log ")
                append("--pretty=format:'$logFormat%n%B%n$commitBodyDelimiter' ")
                append("$latestTag...$previousTag")
            }
        )
    )

    private fun parseLogsWithBody(lines: List<String>): List<LogItem> {
        val commits = mutableListOf<LogItem>()
        val currentLines = mutableListOf<String>()

        for (line in lines) {
            if (line == commitBodyDelimiter && currentLines.isNotEmpty()) {
                // Parse accumulated lines as one commit
                val headerLine = currentLines.first()
                val bodyLines = currentLines.drop(1)
                val body = bodyLines.joinToString("\n").trim()

                parseLogLine(headerLine)?.let { logItem ->
                    commits.add(logItem.copy(body = body))
                }
                currentLines.clear()
            } else {
                currentLines.add(line)
            }
        }

        return commits
    }

    fun getRemoteUrl(): String? = executeCommand("git config --get remote.origin.url").firstOrNull()

    private fun executeCommand(command: String): List<String> = commandExecutor.execute(command)
}

private const val RECORD_SEPARATOR = "\u001E"
private const val EXPECTED_FIELD_COUNT = 9

/**
 * Parses a single git log line in record-separator-delimited format.
 * Returns null if the line cannot be parsed.
 */
internal fun parseLogLine(line: String): LogItem? {
    val fields = line.split(RECORD_SEPARATOR)
    if (fields.size != EXPECTED_FIELD_COUNT) {
        DebugLogger.warn("unexpected field count (${fields.size}) while parsing commit: $line")
        return null
    }
    return LogItem(
        commit = fields[0],
        subject = fields[1],
        parents = fields[2],
        author = Author(name = fields[3], email = fields[4], date = fields[5]),
        committer = Author(name = fields[6], email = fields[7], date = fields[8])
    )
}
