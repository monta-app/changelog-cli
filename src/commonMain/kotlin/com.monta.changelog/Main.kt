package com.monta.changelog

import com.github.ajalt.clikt.core.main
import com.monta.changelog.util.DebugLogger
import kotlin.time.measureTime

fun main(args: Array<String>) {
    val elapsedTime = measureTime {
        GenerateChangeLogCommand().main(args)
    }
    DebugLogger.info("ran in ${elapsedTime.inWholeMilliseconds}")
}
