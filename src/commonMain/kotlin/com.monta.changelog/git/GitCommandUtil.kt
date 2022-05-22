package com.monta.changelog.git

import com.monta.changelog.util.DebugLogger
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
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
           "abbreviated_commit":"%h",
           "tree":"%T",
           "abbreviated_tree":"%t",
           "parent":"%P",
           "abbreviated_parent":"%p",
           "refs":"%D",
           "subject":"%s",
           "commit_notes":"%N",
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

    fun getTags(): List<String> {
        return executeCommand("git tag --sort=-v:refname").map { tag -> tag.trim() }
    }

    fun getLogs(): List<LogItem> {
        return executeCommand(buildString {
            append("git log ")
            append("--pretty=format:'$logJsonFormat'")
        }).map { Json.decodeFromString(it) }
    }

    fun getLogs(latestTag: String, previousTag: String): List<LogItem> {
        return executeCommand(buildString {
            append("git log ")
            append("--pretty=format:'$logJsonFormat' ")
            append("$latestTag...$previousTag")
        }).map { Json.decodeFromString(it) }
    }

    fun getRemoteUrl(): String? {
        return executeCommand("git config --get remote.origin.url").firstOrNull()
    }

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