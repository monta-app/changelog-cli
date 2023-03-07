package com.monta.changelog.util

import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
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