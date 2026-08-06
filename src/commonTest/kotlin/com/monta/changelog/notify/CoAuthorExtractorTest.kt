package com.monta.changelog.notify

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CoAuthorExtractorTest :
    StringSpec({

        "extract should return empty list when there are no trailers" {
            CoAuthorExtractor.extract(listOf("fix: do the thing\n\nno trailers here")) shouldBe emptyList()
        }

        "extract should parse a co-authored-by trailer with a github noreply email" {
            val message = """
                fix: do the thing

                Co-authored-by: Jane Doe <12345+janedoe@users.noreply.github.com>
            """.trimIndent()

            CoAuthorExtractor.extract(listOf(message)) shouldBe listOf(
                CoAuthorExtractor.CoAuthor(name = "Jane Doe", email = "12345+janedoe@users.noreply.github.com", login = "janedoe")
            )
        }

        "extract should parse a co-authored-by trailer with a regular email and no login" {
            val message = "fix: do the thing\n\nCo-authored-by: Jane Doe <jane@example.com>"

            CoAuthorExtractor.extract(listOf(message)) shouldBe listOf(
                CoAuthorExtractor.CoAuthor(name = "Jane Doe", email = "jane@example.com", login = null)
            )
        }

        "extract should be case-insensitive on the trailer keyword" {
            val message = "fix: do the thing\n\nco-authored-by: Jane Doe <jane@example.com>"

            CoAuthorExtractor.extract(listOf(message)) shouldBe listOf(
                CoAuthorExtractor.CoAuthor(name = "Jane Doe", email = "jane@example.com", login = null)
            )
        }

        "extract should handle multiple co-authors across multiple commit messages" {
            val messages = listOf(
                "fix: a\n\nCo-authored-by: Jane Doe <jane@example.com>",
                "fix: b\n\nCo-authored-by: John Smith <54321+johnsmith@users.noreply.github.com>"
            )

            CoAuthorExtractor.extract(messages) shouldBe listOf(
                CoAuthorExtractor.CoAuthor(name = "Jane Doe", email = "jane@example.com", login = null),
                CoAuthorExtractor.CoAuthor(name = "John Smith", email = "54321+johnsmith@users.noreply.github.com", login = "johnsmith")
            )
        }

        "extract should deduplicate identical trailers appearing in multiple commits" {
            val messages = listOf(
                "fix: a\n\nCo-authored-by: Jane Doe <jane@example.com>",
                "fix: b\n\nCo-authored-by: Jane Doe <jane@example.com>"
            )

            CoAuthorExtractor.extract(messages) shouldBe listOf(
                CoAuthorExtractor.CoAuthor(name = "Jane Doe", email = "jane@example.com", login = null)
            )
        }

        "extract should not match a bot noreply email without a plus-prefixed id" {
            val message = "fix: a\n\nCo-authored-by: dependabot[bot] <dependabot[bot]@users.noreply.github.com>"

            val result = CoAuthorExtractor.extract(listOf(message))
            result shouldBe listOf(
                CoAuthorExtractor.CoAuthor(name = "dependabot[bot]", email = "dependabot[bot]@users.noreply.github.com", login = null)
            )
        }
    })
