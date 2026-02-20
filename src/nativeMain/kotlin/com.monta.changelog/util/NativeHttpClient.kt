package com.monta.changelog.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform

/**
 * Native implementation of HTTP client using Curl engine.
 */
@OptIn(ExperimentalNativeApi::class)
actual val client: HttpClient by lazy {
    HttpClient(Curl) {
        engine {
            // Explicitly set CA bundle path on Linux to fix TLS verification
            // for cross-compiled ARM64 binaries where the statically linked
            // libcurl may have an incorrect default CA path from the build sysroot
            if (Platform.osFamily == OsFamily.LINUX) {
                caInfo = "/etc/ssl/certs/ca-certificates.crt"
            }
        }
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }
}
