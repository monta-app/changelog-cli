package com.monta.changelog.git

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CommitMapperTest :
    StringSpec({

        val mapper = CommitMapper()

        "should map conventional commit correctly" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "feat: add new feature",
                body = "This is the body",
                parents = "parent1"
            )

            val commit = mapper.fromGitLogItem(logItem)

            commit.shouldNotBeNull()
            commit.message shouldBe "add new feature"
            commit.sha shouldBe "abc123"
            commit.body shouldBe "This is the body"
        }

        "should return null for merge commits" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "merge123",
                subject = "Merge pull request #123",
                body = "",
                parents = "parent1 parent2"
            )

            val commit = mapper.fromGitLogItem(logItem)

            commit.shouldBeNull()
        }

        "should map commit with scope" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "fix(api): fix bug in API",
                body = "",
                parents = "parent1"
            )

            val commit = mapper.fromGitLogItem(logItem)

            commit.shouldNotBeNull()
            commit.message shouldBe "fix bug in API"
            commit.scope shouldBe "api"
        }

        "should map breaking change commit" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "feat!: breaking change",
                body = "",
                parents = "parent1"
            )

            val commit = mapper.fromGitLogItem(logItem)

            commit.shouldNotBeNull()
            commit.breaking shouldBe true
            commit.message shouldBe "breaking change"
        }

        "should return null for non-conventional commit" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "this is not a conventional commit",
                body = "",
                parents = "parent1"
            )

            val commit = mapper.fromGitLogItem(logItem)

            commit.shouldBeNull()
        }

        "should return null for commit with invalid type" {
            val logItem = LogItem(
                author = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                committer = Author(date = "2026-01-12", email = "test@test.com", name = "Test"),
                commit = "abc123",
                subject = "invalidtype: message",
                body = "",
                parents = "parent1"
            )

            val commit = mapper.fromGitLogItem(logItem)

            commit.shouldBeNull()
        }
    })
