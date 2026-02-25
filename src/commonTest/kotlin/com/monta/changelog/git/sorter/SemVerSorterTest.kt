package com.monta.changelog.git.sorter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SemVerSorterTest :
    StringSpec({

        val sorter = SemVerSorter()

        listOf(
            listOf("5.9.0", "5.9.1") to listOf("5.9.1", "5.9.0"),
            listOf("5.9.0", "v5.9.1") to listOf("v5.9.1", "5.9.0"),
            listOf("4.1.1", "5.0.0") to listOf("5.0.0", "4.1.1"),
            listOf("5.10.0", "5.0.0") to listOf("5.10.0", "5.0.0"),
            listOf("5.10.0", "5.9.0") to listOf("5.10.0", "5.9.0"),
            listOf("10.10.0", "5.9.0") to listOf("10.10.0", "5.9.0"),
            listOf("10.10.0", "ignored tag", "5.9.0") to listOf("10.10.0", "5.9.0")
        ).forEach { (input, expected) ->
            "should sort tags in descending order" {
                val tags = input.map { Tag(it) }
                val sortedTags = sorter.sort(tags)
                sortedTags shouldBe expected.map { Tag(it) }
            }
        }

        "generateInitialTag should produce valid semver" {
            sorter.generateInitialTag() shouldBe "0.1.0"
        }

        "generateInitialTag should be parseable by the sorter" {
            val tag = sorter.generateInitialTag()
            val sorted = sorter.sort(listOf(Tag(tag)))
            sorted.size shouldBe 1
            sorted[0].shortTag shouldBe tag
        }
    })
