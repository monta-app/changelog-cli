package com.monta.changelog

import com.monta.changelog.util.DebugLogger
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    val elapsedTime = measureTimeMillis {
        GenerateChangeLogCommand().main(args)
    }
    DebugLogger.info("ran in $elapsedTime")
}
