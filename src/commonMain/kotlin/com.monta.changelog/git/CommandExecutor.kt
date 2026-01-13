package com.monta.changelog.git

/**
 * Platform-agnostic interface for executing shell commands.
 * Implementations exist for Native (using popen) and JVM (using ProcessBuilder).
 */
interface CommandExecutor {
    /**
     * Executes a shell command and returns the output as a list of lines.
     *
     * @param command The shell command to execute
     * @return List of output lines from the command
     * @throws RuntimeException if the command fails to execute
     */
    fun execute(command: String): List<String>
}

/**
 * Provides a platform-specific CommandExecutor instance.
 * Implemented using expect/actual pattern for each platform.
 */
expect fun createCommandExecutor(): CommandExecutor
