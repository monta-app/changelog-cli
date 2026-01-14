package com.monta.changelog.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DateTimeUtilTest :
    StringSpec({

        "should format valid ISO 8601 timestamp" {
            val result = DateTimeUtil.formatTimestamp("2026-01-13T22:00:00Z")
            result shouldBe "Jan 13, 2026 at 22:00 UTC"
        }

        "should format timestamp with different month" {
            val result = DateTimeUtil.formatTimestamp("2025-12-25T15:30:00Z")
            result shouldBe "Dec 25, 2025 at 15:30 UTC"
        }

        "should format timestamp at midnight" {
            val result = DateTimeUtil.formatTimestamp("2026-06-15T00:00:00Z")
            result shouldBe "Jun 15, 2026 at 00:00 UTC"
        }

        "should format timestamp at end of day" {
            val result = DateTimeUtil.formatTimestamp("2026-03-01T23:59:00Z")
            result shouldBe "Mar 1, 2026 at 23:59 UTC"
        }

        "should handle all months correctly" {
            val months = mapOf(
                "2026-01-01T12:00:00Z" to "Jan 1, 2026 at 12:00 UTC",
                "2026-02-01T12:00:00Z" to "Feb 1, 2026 at 12:00 UTC",
                "2026-03-01T12:00:00Z" to "Mar 1, 2026 at 12:00 UTC",
                "2026-04-01T12:00:00Z" to "Apr 1, 2026 at 12:00 UTC",
                "2026-05-01T12:00:00Z" to "May 1, 2026 at 12:00 UTC",
                "2026-06-01T12:00:00Z" to "Jun 1, 2026 at 12:00 UTC",
                "2026-07-01T12:00:00Z" to "Jul 1, 2026 at 12:00 UTC",
                "2026-08-01T12:00:00Z" to "Aug 1, 2026 at 12:00 UTC",
                "2026-09-01T12:00:00Z" to "Sep 1, 2026 at 12:00 UTC",
                "2026-10-01T12:00:00Z" to "Oct 1, 2026 at 12:00 UTC",
                "2026-11-01T12:00:00Z" to "Nov 1, 2026 at 12:00 UTC",
                "2026-12-01T12:00:00Z" to "Dec 1, 2026 at 12:00 UTC"
            )

            months.forEach { (input, expected) ->
                DateTimeUtil.formatTimestamp(input) shouldBe expected
            }
        }

        "should pad single digit hours with zero" {
            val result = DateTimeUtil.formatTimestamp("2026-01-01T09:05:00Z")
            result shouldBe "Jan 1, 2026 at 09:05 UTC"
        }

        "should return null for null input" {
            val result = DateTimeUtil.formatTimestamp(null)
            result shouldBe null
        }

        "should return original timestamp for invalid format" {
            val invalid = "not-a-timestamp"
            val result = DateTimeUtil.formatTimestamp(invalid)
            result shouldBe invalid
        }

        "should handle malformed ISO 8601 gracefully" {
            val malformed = "2026-13-45T99:99:99Z"
            val result = DateTimeUtil.formatTimestamp(malformed)
            result shouldBe malformed
        }

        "should format timestamp with fractional seconds" {
            val result = DateTimeUtil.formatTimestamp("2026-01-13T22:00:00.123456Z")
            result shouldBe "Jan 13, 2026 at 22:00 UTC"
        }

        "should format leap year date" {
            val result = DateTimeUtil.formatTimestamp("2024-02-29T14:30:00Z")
            result shouldBe "Feb 29, 2024 at 14:30 UTC"
        }
    })
