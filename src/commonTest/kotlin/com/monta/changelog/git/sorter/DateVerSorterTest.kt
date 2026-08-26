package com.monta.changelog.git.sorter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DateVerSorterTest :
    StringSpec({

        "sorts DateVer tags in descending order" {
            val tags = listOf("2026-08-24-23-59", "2026-08-25-13-18", "2026-08-25-09-16").map { Tag(it) }
            DateVerSorter().sort(tags) shouldBe
                listOf("2026-08-25-13-18", "2026-08-25-09-16", "2026-08-24-23-59").map { Tag(it) }
        }

        // A tag with any non-numeric dash segment fails to parse and is dropped —
        // this is what keeps other apps' prefixed tags out of a per-app range.
        "drops tags with a non-numeric segment" {
            val tags = listOf("2026-08-25-13-18", "hub-2026-08-25-13-18", "not-a-date").map { Tag(it) }
            DateVerSorter().sort(tags) shouldBe listOf(Tag("2026-08-25-13-18"))
        }

        // Only the first five dash segments are read, so a sixth numeric segment
        // does NOT prevent parsing — documented so callers don't rely on it failing.
        "retains a tag whose first five segments are numeric" {
            DateVerSorter().sort(listOf(Tag("2026-08-25-13-18-1"))) shouldBe listOf(Tag("2026-08-25-13-18-1"))
        }
    })
