package com.monta.changelog.notify

/**
 * A labeled URL (e.g. a dashboard or monitoring link) to include in a release notification.
 */
data class MonitoringUrl(
    val label: String,
    val url: String,
) {
    companion object {
        /**
         * Parses a single monitoring URL entry, supporting either a bare URL
         * (`https://example.com`) or a labeled one (`Label|https://example.com`).
         */
        fun parse(raw: String): MonitoringUrl? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                return null
            }

            val parts = trimmed.split("|", limit = 2)
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                MonitoringUrl(label = parts[0].trim(), url = parts[1].trim())
            } else {
                MonitoringUrl(label = trimmed, url = trimmed)
            }
        }

        /**
         * Parses a comma-separated list of monitoring URL entries.
         */
        fun parseAll(raw: List<String>?): List<MonitoringUrl> = raw
            ?.mapNotNull(::parse)
            ?: emptyList()
    }
}
