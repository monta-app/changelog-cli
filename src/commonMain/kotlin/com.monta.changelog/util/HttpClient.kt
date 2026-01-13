package com.monta.changelog.util

import io.ktor.client.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    explicitNulls = false
    isLenient = true
    ignoreUnknownKeys = true
}

/**
 * Platform-specific HTTP client instance.
 * Implementations use Curl for Native and OkHttp for JVM.
 */
expect val client: HttpClient

suspend inline fun <reified T> HttpResponse.getBodySafe(): T? {
    val responseBodyText: String = bodyAsText()

    return if (status.isSuccess()) {
        try {
            json.decodeFromString<T>(responseBodyText)
        } catch (throwable: Throwable) {
            DebugLogger.error("Request failed to deserialized with body $responseBodyText")
            null
        }
    } else {
        DebugLogger.error("Request failed with code ${status.value} and body $responseBodyText")
        null
    }
}
