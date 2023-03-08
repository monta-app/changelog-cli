package com.monta.changelog.util

import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    explicitNulls = false
    isLenient = true
    ignoreUnknownKeys = true
}

val client by lazy {
    HttpClient(Curl) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }
}

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
