package com.monta.changelog.git

import com.monta.changelog.util.DebugLogger
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import platform.posix.FILE
import platform.posix.NULL
import platform.posix.exit
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

internal class GitCommandUtil {

    private val logJsonFormat = """
        {
           "commit":"%H",
           "subject":"%s",
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

    fun getHeadSha(): String {
        return executeCommand("git rev-parse --verify HEAD").first()
    }

    fun getTags(): List<String> {
        return executeCommand("git tag").map { tag ->
            tag.trim()
        }
    }

    fun getFilesInCommit(commitId: String): List<String> {
        return executeCommand("git diff-tree --no-commit-id --name-only $commitId -r").map { file ->
            file.trim()
        }
    }

    fun getLogs(): List<LogItem> {
        return executeCommand(
            buildString {
                append("git log ")
                append("--pretty=format:'$logJsonFormat'")
            }
        ).mapNotNull {
            Json.decodeFromStringNullable(it)
        }
    }

    fun getLogs(latestTag: String, previousTag: String): List<LogItem> {
        return executeCommand(
            buildString {
                append("git log ")
                append("--pretty=format:'$logJsonFormat' ")
                append("$latestTag...$previousTag")
            }
        ).mapNotNull {
            Json.decodeFromStringNullable(it)
        }
    }

    private inline fun <reified T> StringFormat.decodeFromStringNullable(string: String): T? {
        return try {
            decodeFromString(string)
        } catch (exception: Exception) {
            DebugLogger.warn("exception while parsing commit: $string")
            null
        }
    }

    fun getRemoteUrl(): String? {
        return executeCommand("git config --get remote.origin.url").firstOrNull()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun executeCommand(command: String): List<String> {
        val fp: CPointer<FILE>? = popen(command, "r")
        val buffer = ByteArray(4096)

        /* Open the command for reading. */
        if (fp == NULL) {
            DebugLogger.error("Failed to run command $command")
            exit(1)
        }

        /* Read the output a line at a time - output it. */
        var scan = fgets(buffer.refTo(0), buffer.size, fp)
        val result = mutableListOf<String>()

        if (scan != null) {
            while (scan != NULL) {
                result.add(requireNotNull(scan).toKString().trim())
                scan = fgets(buffer.refTo(0), buffer.size, fp)
            }
        }

        /* close */
        pclose(fp)

        return result
    }
}
