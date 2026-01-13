package com.monta.changelog.git

import com.monta.changelog.util.DebugLogger
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.FILE
import platform.posix.NULL
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

/**
 * Native implementation of CommandExecutor using popen/pclose.
 */
class NativeCommandExecutor : CommandExecutor {

    @OptIn(ExperimentalForeignApi::class)
    override fun execute(command: String): List<String> {
        val fp: CPointer<FILE>? = popen(command, "r")
        val buffer = ByteArray(4096)

        /* Open the command for reading. */
        if (fp == NULL) {
            DebugLogger.error("Failed to run command $command")
            throw RuntimeException("Failed to run command: $command")
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

/**
 * Actual implementation of createCommandExecutor for Native platforms.
 */
actual fun createCommandExecutor(): CommandExecutor = NativeCommandExecutor()
