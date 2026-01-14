package com.monta.changelog.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Utility object for date/time formatting operations.
 */
object DateTimeUtil {

    /**
     * Formats an ISO 8601 timestamp string into a human-readable format with seconds.
     * Example: "2026-01-13T22:00:15Z" → "Jan 13, 2026 at 22:00:15 UTC"
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
            val second = dateTime.second.toString().padStart(2, '0')

            "$month $day, $year at $hour:$minute:$second UTC"
        } catch (e: Exception) {
            // If parsing fails, return the original timestamp
            DebugLogger.debug("Failed to parse timestamp: $isoTimestamp - ${e.message}")
            isoTimestamp
        }
    }

    /**
     * Formats a time range intelligently:
     * - If same day: "Jan 14, 2026 at 13:15:01 UTC → 13:15:21 UTC"
     * - If different days: "Jan 14, 2026 at 13:15:01 UTC → Jan 15, 2026 at 02:30:45 UTC"
     *
     * @param startTimestamp ISO 8601 formatted start timestamp string
     * @param endTimestamp ISO 8601 formatted end timestamp string
     * @return Formatted time range string, or null if either input is null
     */
    fun formatTimeRange(startTimestamp: String?, endTimestamp: String?): String? {
        if (startTimestamp == null || endTimestamp == null) return null

        return try {
            val startInstant = Instant.parse(startTimestamp)
            val endInstant = Instant.parse(endTimestamp)
            val startDateTime = startInstant.toLocalDateTime(TimeZone.UTC)
            val endDateTime = endInstant.toLocalDateTime(TimeZone.UTC)

            val monthNames = listOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )

            // Format start time (always full format)
            val startMonth = monthNames[startDateTime.month.ordinal]
            val startDay = startDateTime.day
            val startYear = startDateTime.year
            val startHour = startDateTime.hour.toString().padStart(2, '0')
            val startMinute = startDateTime.minute.toString().padStart(2, '0')
            val startSecond = startDateTime.second.toString().padStart(2, '0')
            val startFormatted = "$startMonth $startDay, $startYear at $startHour:$startMinute:$startSecond UTC"

            // Check if same day
            val sameDay = startDateTime.year == endDateTime.year &&
                startDateTime.month == endDateTime.month &&
                startDateTime.day == endDateTime.day

            val endFormatted = if (sameDay) {
                // Same day: only show time for end
                val endHour = endDateTime.hour.toString().padStart(2, '0')
                val endMinute = endDateTime.minute.toString().padStart(2, '0')
                val endSecond = endDateTime.second.toString().padStart(2, '0')
                "$endHour:$endMinute:$endSecond UTC"
            } else {
                // Different day: show full date and time
                val endMonth = monthNames[endDateTime.month.ordinal]
                val endDay = endDateTime.day
                val endYear = endDateTime.year
                val endHour = endDateTime.hour.toString().padStart(2, '0')
                val endMinute = endDateTime.minute.toString().padStart(2, '0')
                val endSecond = endDateTime.second.toString().padStart(2, '0')
                "$endMonth $endDay, $endYear at $endHour:$endMinute:$endSecond UTC"
            }

            "$startFormatted → $endFormatted"
        } catch (e: Exception) {
            // If parsing fails, return the original timestamps
            DebugLogger.debug("Failed to parse time range: $startTimestamp → $endTimestamp - ${e.message}")
            "$startTimestamp → $endTimestamp"
        }
    }
}
