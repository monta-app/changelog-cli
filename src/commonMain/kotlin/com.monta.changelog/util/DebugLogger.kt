package com.monta.changelog.util

import kotlinx.atomicfu.atomic
import kotlinx.datetime.Clock

object DebugLogger {
    enum class Level {
        Verbose,
        Debug,
        Info,
        Warn,
        Error;
    }

    private const val ANSI_RESET = "\u001B[0m"
    private const val ANSI_RED = "\u001B[31m"
    private const val ANSI_GREEN = "\u001B[32m"
    private const val ANSI_YELLOW = "\u001B[33m"
    private const val ANSI_BLUE = "\u001B[34m"

    private val loggingLevel = atomic(Level.Info)

    fun setLoggingLevel(loggingLevel: Level) {
        this.loggingLevel.value = loggingLevel
    }

    fun debug(message: String) {
        if (loggingLevel.value > Level.Debug) return
        println("${ANSI_GREEN}$timeStamp [DEBUG] $message$ANSI_RESET")
    }

    fun info(message: String) {
        if (loggingLevel.value > Level.Info) return
        println("${ANSI_BLUE}$timeStamp [INFO ] $message$ANSI_RESET")
    }

    fun warn(message: String) {
        if (loggingLevel.value > Level.Warn) return
        println("${ANSI_YELLOW}$timeStamp [WARN ] $message$ANSI_RESET")
    }

    fun error(message: String) {
        println("${ANSI_RED}$timeStamp [ERROR] $message$ANSI_RESET")
    }

    private val timeStamp: String
        get() = "[${Clock.System.now()}]"
}
