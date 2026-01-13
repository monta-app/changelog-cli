package com.monta.changelog.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

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
