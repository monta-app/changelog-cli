package com.monta.changelog.log

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ChangeLogServiceBlacklistTest :
    StringSpec({

        "normalizeProjectBlacklist upper-cases and trims entries" {
            ChangeLogService.normalizeProjectBlacklist(listOf(" cust ", "Partner")) shouldBe setOf("CUST", "PARTNER")
        }

        "normalizeProjectBlacklist drops blank entries" {
            ChangeLogService.normalizeProjectBlacklist(listOf("CUST", "", "  ")) shouldBe setOf("CUST")
        }

        "normalizeProjectBlacklist returns empty set for null" {
            ChangeLogService.normalizeProjectBlacklist(null) shouldBe emptySet()
        }

        "partitionByProjectBlacklist keeps tickets from non-blacklisted projects" {
            val (allowed, blacklisted) = ChangeLogService.partitionByProjectBlacklist(
                tickets = listOf("ENG-1", "CUST-2", "ENG-3", "PARTNER-9"),
                blacklist = setOf("CUST", "PARTNER")
            )

            allowed shouldBe listOf("ENG-1", "ENG-3")
            blacklisted shouldBe listOf("CUST-2", "PARTNER-9")
        }

        "partitionByProjectBlacklist matches project keys case-insensitively" {
            val (allowed, blacklisted) = ChangeLogService.partitionByProjectBlacklist(
                tickets = listOf("cust-2", "eng-1"),
                blacklist = setOf("CUST")
            )

            allowed shouldBe listOf("eng-1")
            blacklisted shouldBe listOf("cust-2")
        }

        "partitionByProjectBlacklist keeps every ticket when blacklist is empty" {
            val (allowed, blacklisted) = ChangeLogService.partitionByProjectBlacklist(
                tickets = listOf("ENG-1", "CUST-2"),
                blacklist = emptySet()
            )

            allowed shouldBe listOf("ENG-1", "CUST-2")
            blacklisted shouldBe emptyList()
        }
    })
