package com.monta.changelog.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Utility object for date/time formatting operations.
 */
object DateTimeUtil {

    /**
     * Formats an ISO 8601 timestamp to a compact UTC clock time.
     * Example: "2026-08-27T14:19:01Z" → "14:19:01". Null on parse failure.
     */
    fun formatClock(isoTimestamp: String?): String? {
        if (isoTimestamp == null) return null
        return try {
            val dateTime = Instant.parse(isoTimestamp).toLocalDateTime(TimeZone.UTC)
            val hour = dateTime.hour.toString().padStart(2, '0')
            val minute = dateTime.minute.toString().padStart(2, '0')
            val second = dateTime.second.toString().padStart(2, '0')
            "$hour:$minute:$second"
        } catch (e: Exception) {
            DebugLogger.debug("Failed to parse timestamp: $isoTimestamp - ${e.message}")
            null
        }
    }

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

    /**
     * Formats the elapsed time between two ISO 8601 timestamps as a compact,
     * human-readable duration. Example: 272s → "4m 32s", 3800s → "1h 3m 20s".
     * Null on parse failure or when the range is negative.
     */
    fun formatDuration(startTimestamp: String?, endTimestamp: String?): String? {
        if (startTimestamp == null || endTimestamp == null) return null

        return try {
            val totalSeconds = (Instant.parse(endTimestamp) - Instant.parse(startTimestamp)).inWholeSeconds
            if (totalSeconds < 0) return null

            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            buildString {
                if (hours > 0) append("${hours}h ")
                if (hours > 0 || minutes > 0) append("${minutes}m ")
                append("${seconds}s")
            }
        } catch (e: Exception) {
            DebugLogger.debug("Failed to parse duration: $startTimestamp → $endTimestamp - ${e.message}")
            null
        }
    }
}
