package com.monta.changelog.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Utility object for date/time formatting operations.
 */
object DateTimeUtil {

    /**
     * Formats an ISO 8601 timestamp string into a human-readable format.
     * Example: "2026-01-13T22:00:00Z" → "Jan 13, 2026 at 22:00 UTC"
     *
     * @param isoTimestamp ISO 8601 formatted timestamp string
     * @return Human-readable timestamp string, or null if input is null
     */
    fun formatTimestamp(isoTimestamp: String?): String? {
        if (isoTimestamp == null) return null

        return try {
            val instant = Instant.parse(isoTimestamp)
            val dateTime = instant.toLocalDateTime(TimeZone.UTC)

            val monthNames = listOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )

            val month = monthNames[dateTime.month.ordinal]
            val day = dateTime.day
            val year = dateTime.year
            val hour = dateTime.hour.toString().padStart(2, '0')
            val minute = dateTime.minute.toString().padStart(2, '0')

            "$month $day, $year at $hour:$minute UTC"
        } catch (e: Exception) {
            // If parsing fails, return the original timestamp
            DebugLogger.debug("Failed to parse timestamp: $isoTimestamp - ${e.message}")
            isoTimestamp
        }
    }
}
