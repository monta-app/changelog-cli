package com.monta.changelog.printer.slack

import kotlinx.serialization.Serializable

@Serializable
internal data class SlackMessageRequest(
    val channel: String,
    val text: String,
    val blocks: List<SlackBlock>,
)

@Serializable
internal class SlackBlock(
    val text: SlackText? = null,
    val type: String,
    val url: String? = null,
    val elements: List<SlackBlock>? = null,
)

@Serializable
internal class SlackText(
    val text: String,
    val type: String,
)