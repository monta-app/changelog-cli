package com.monta.changelog.git

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GitModelsTest :
    StringSpec({

        "LogItem should detect merge commits with two parents" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "Merge pull request #123",
                body = "",
                parents = "parent1 parent2"
            )

            logItem.isMergeCommit() shouldBe true
        }

        "LogItem should detect merge commits with multiple parents" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "Merge pull request #123",
                body = "",
                parents = "parent1 parent2 parent3"
            )

            logItem.isMergeCommit() shouldBe true
        }

        "LogItem should not detect regular commits as merge commits" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "fix: regular commit",
                body = "",
                parents = "parent1"
            )

            logItem.isMergeCommit() shouldBe false
        }

        "LogItem should handle empty parents field" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "fix: commit",
                body = "",
                parents = ""
            )

            logItem.isMergeCommit() shouldBe false
        }

        "LogItem should handle whitespace in parents field" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "Merge",
                body = "",
                parents = "  parent1   parent2  "
            )

            logItem.isMergeCommit() shouldBe true
        }
    })
