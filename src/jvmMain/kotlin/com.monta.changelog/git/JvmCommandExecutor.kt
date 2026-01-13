package com.monta.changelog.git

import com.monta.changelog.util.DebugLogger

/**
 * JVM implementation of CommandExecutor using ProcessBuilder.
 */
class JvmCommandExecutor : CommandExecutor {

    override fun execute(command: String): List<String> {
        try {
            val process = ProcessBuilder("/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readLines()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                DebugLogger.error("Command failed with exit code $exitCode: $command")
                throw RuntimeException("Failed to run command: $command (exit code: $exitCode)")
            }

            return result
        } catch (e: Exception) {
            DebugLogger.error("Failed to run command $command: ${e.message}")
            throw RuntimeException("Failed to run command: $command", e)
        }
    }
}

/**
 * Actual implementation of createCommandExecutor for JVM platforms.
 */
actual fun createCommandExecutor(): CommandExecutor = JvmCommandExecutor()
