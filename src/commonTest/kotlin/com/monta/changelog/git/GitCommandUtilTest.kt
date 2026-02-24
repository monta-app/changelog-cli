package com.monta.changelog.git

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class GitCommandUtilTest :
    StringSpec({

        "parseLogLine should parse normal input" {
            val line = "abc123\u001Efeat: add feature\u001Eparent1\u001EJohn Doe\u001Ejohn@test.com\u001E2026-01-12T10:00:00+00:00\u001EJane Doe\u001Ejane@test.com\u001E2026-01-12T10:00:00+00:00"

            val result = parseLogLine(line)

            result.shouldNotBeNull()
            result.commit shouldBe "abc123"
            result.subject shouldBe "feat: add feature"
            result.parents shouldBe "parent1"
            result.author.name shouldBe "John Doe"
            result.author.email shouldBe "john@test.com"
            result.author.date shouldBe "2026-01-12T10:00:00+00:00"
            result.committer.name shouldBe "Jane Doe"
            result.committer.email shouldBe "jane@test.com"
            result.committer.date shouldBe "2026-01-12T10:00:00+00:00"
        }

        "parseLogLine should handle double quotes in subject" {
            val line = "abc123\u001Efix: revert \"prevent duplicate charge points\"\u001Eparent1\u001EJohn Doe\u001Ejohn@test.com\u001E2026-01-12T10:00:00+00:00\u001EJane Doe\u001Ejane@test.com\u001E2026-01-12T10:00:00+00:00"

            val result = parseLogLine(line)

            result.shouldNotBeNull()
            result.subject shouldBe "fix: revert \"prevent duplicate charge points\""
        }

        "parseLogLine should handle special characters in author name" {
            val line = "abc123\u001Efeat: something\u001Eparent1\u001EJosé O'Brien-Smith\u001Ejose@test.com\u001E2026-01-12T10:00:00+00:00\u001EJosé O'Brien-Smith\u001Ejose@test.com\u001E2026-01-12T10:00:00+00:00"

            val result = parseLogLine(line)

            result.shouldNotBeNull()
            result.author.name shouldBe "José O'Brien-Smith"
        }

        "parseLogLine should return null for wrong field count" {
            val result = parseLogLine("abc123\u001Efeat: something\u001Etoo-few-fields")

            result.shouldBeNull()
        }

        "parseLogLine should return null for empty string" {
            val result = parseLogLine("")

            result.shouldBeNull()
        }
    })
