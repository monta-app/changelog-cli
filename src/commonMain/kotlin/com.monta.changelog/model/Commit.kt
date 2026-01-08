package com.monta.changelog.model

import kotlinx.serialization.Serializable

@Serializable
data class Commit(
    val type: ConventionalCommitType,
    val scope: String?,
    val breaking: Boolean,
    val message: String,
    val body: String = "",
)
