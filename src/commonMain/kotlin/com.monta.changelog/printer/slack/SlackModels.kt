package com.monta.changelog.printer.slack

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SlackMessageRequest(
    @SerialName("channel")
    val channel: String,
    @SerialName("thread_ts")
    val threadTs: String?,
    @SerialName("text")
    val text: String,
    @SerialName("blocks")
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

@Serializable
internal data class SlackMessageResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("error")
    val error: String?,
    @SerialName("ts")
    val ts: String,
)
