package com.monta.changelog.util

import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Native implementation of HTTP client using Curl engine.
 */
actual val client: HttpClient by lazy {
    HttpClient(Curl) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }
}
