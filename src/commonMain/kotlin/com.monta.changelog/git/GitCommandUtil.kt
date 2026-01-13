package com.monta.changelog.git

import com.monta.changelog.util.DebugLogger
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal class GitCommandUtil {

    private val commandExecutor: CommandExecutor = createCommandExecutor()

    private val logJsonFormat = """
        {
           "commit":"%H",
           "subject":"%s",
           "parents":"%P",
           "author":{
              "name":"%aN",
              "email":"%aE",
              "date":"%aI"
           },
           "committer":{
              "name":"%cN",
              "email":"%cE",
              "date":"%cI"
           }
        }
    """.replace("\n", "").trimIndent()

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
                append("--pretty=format:'$logJsonFormat%n%B%n$commitBodyDelimiter'")
            }
        )
    )

    fun getLogs(latestTag: String, previousTag: String): List<LogItem> = parseLogsWithBody(
        executeCommand(
            buildString {
                append("git log ")
                append("--pretty=format:'$logJsonFormat%n%B%n$commitBodyDelimiter' ")
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
                val jsonLine = currentLines.first()
                val bodyLines = currentLines.drop(1)
                val body = bodyLines.joinToString("\n").trim()

                Json.decodeFromStringNullable<LogItem>(jsonLine)?.let { logItem ->
                    commits.add(logItem.copy(body = body))
                }
                currentLines.clear()
            } else {
                currentLines.add(line)
            }
        }

        return commits
    }

    private inline fun <reified T> StringFormat.decodeFromStringNullable(string: String): T? = try {
        decodeFromString(string)
    } catch (exception: Exception) {
        DebugLogger.warn("exception while parsing commit: $string - ${exception.message}")
        null
    }

    fun getRemoteUrl(): String? = executeCommand("git config --get remote.origin.url").firstOrNull()

    private fun executeCommand(command: String): List<String> = commandExecutor.execute(command)
}
