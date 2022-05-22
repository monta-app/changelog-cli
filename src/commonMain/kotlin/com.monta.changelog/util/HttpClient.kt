package com.monta.changelog.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val client by lazy {
    HttpClient(Curl) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json {
                explicitNulls = false
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
}