package com.monta.changelog.util

sealed interface MarkdownFormatter {
    fun header(value: String): String
    fun title(value: String): String
    fun listItem(value: String): String
    fun hyperlink(text: String, link: String): String

    object GitHub : MarkdownFormatter {
        override fun header(value: String): String = "## $value\n"

        override fun title(value: String): String = "### $value\n"

        override fun listItem(value: String): String = "- $value\n"

        override fun hyperlink(text: String, link: String): String = "[$text]($link)"
    }

    object Slack : MarkdownFormatter {
        override fun header(value: String): String = "*$value*\n"

        override fun title(value: String): String = "*$value*\n"

        override fun listItem(value: String): String = "• $value\n"

        override fun hyperlink(text: String, link: String): String = "<$link|$text>"
    }
}
