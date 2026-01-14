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
    @SerialName("attachments")
    val attachments: List<SlackAttachment>? = null,
)

@Serializable
internal class SlackBlock(
    val text: SlackText? = null,
    val type: String,
    val url: String? = null,
    val elements: List<SlackBlock>? = null,
    val fields: List<SlackField>? = null,
)

@Serializable
internal class SlackText(
    val text: String,
    val type: String,
)

@Serializable
internal class SlackField(
    val type: String,
    val text: String,
)

@Serializable
internal data class SlackAttachment(
    @SerialName("color")
    val color: String,
    @SerialName("text")
    val text: String,
    @SerialName("mrkdwn_in")
    val mrkdwnIn: List<String> = listOf("text"),
)

/**
 * Container for Slack message components.
 */
internal data class SlackMessageComponents(
    val blocks: List<SlackBlock>,
    val attachments: List<SlackAttachment> = emptyList(),
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

@Serializable
internal data class SlackPermalinkResponse(
    @SerialName("ok")
    val ok: Boolean,
    @SerialName("error")
    val error: String?,
    @SerialName("permalink")
    val permalink: String?,
)
