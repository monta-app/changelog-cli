package com.monta.changelog.git.sorter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

class DateVerSorterTest :
    StringSpec({

        val sorter = DateVerSorter()

        listOf(
            listOf("2026-02-25-14-30", "2026-02-25-15-00") to listOf("2026-02-25-15-00", "2026-02-25-14-30"),
            listOf("2026-01-01-00-00", "2025-12-31-23-59") to listOf("2026-01-01-00-00", "2025-12-31-23-59"),
            listOf("2026-02-25-14-30", "not-a-date", "2026-02-26-10-00") to listOf("2026-02-26-10-00", "2026-02-25-14-30"),
            listOf("v91", "some-tag") to emptyList(),
        ).forEach { (input, expected) ->
            "should sort tags in descending order: $input" {
                val tags = input.map { Tag(it) }
                val sortedTags = sorter.sort(tags)
                sortedTags shouldBe expected.map { Tag(it) }
            }
        }

        "generateInitialTag should produce yyyy-MM-dd-HH-mm format" {
            val tag = sorter.generateInitialTag()
            tag shouldMatch Regex("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}")
        }

        "generateInitialTag should be parseable by the sorter" {
            val tag = sorter.generateInitialTag()
            val sorted = sorter.sort(listOf(Tag(tag)))
            sorted.size shouldBe 1
            sorted[0].shortTag shouldBe tag
        }
    })
