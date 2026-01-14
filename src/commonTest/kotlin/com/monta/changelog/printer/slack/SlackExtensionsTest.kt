package com.monta.changelog.printer.slack

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class SlackExtensionsTest :
    StringSpec({

        "should create single attachment when content fits within limit" {
            val items = List(10) { "Item #$it" }
            val result = splitIntoAttachments(
                header = "Pull Requests (10)",
                items = items,
                color = "#1F2328"
            )

            result shouldHaveSize 1
            result[0].color shouldBe "#1F2328"
            result[0].text shouldStartWith "*Pull Requests (10):*"
            result[0].text shouldContain "Item #0"
            result[0].text shouldContain "Item #9"
        }

        "should split attachments when content exceeds limit" {
            // Create enough items to definitely exceed 2500 char limit
            // Each item is about 80 chars, need many more items to exceed
            val items = List(50) { index ->
                "https://github.com/monta-app/service-geo-with-long-name/pull/${10000 + index}|#${10000 + index}"
            }
            val result = splitIntoAttachments(
                header = "Pull Requests (50)",
                items = items,
                color = "#1F2328"
            )

            // Should be split into at least 2 attachments
            (result.size >= 2) shouldBe true
            result[0].color shouldBe "#1F2328"
            result[0].text shouldStartWith "*Pull Requests (50):*"
            if (result.size > 1) {
                result[1].color shouldBe "#1F2328"
                result[1].text shouldStartWith "*Pull Requests (50) (cont'd):*"
            }
        }

        "should handle empty items list" {
            val items = emptyList<String>()
            val result = splitIntoAttachments(
                header = "Pull Requests (0)",
                items = items,
                color = "#1F2328"
            )

            result shouldHaveSize 0
        }

        "should handle single item" {
            val items = listOf("https://github.com/monta-app/repo/pull/123|#123")
            val result = splitIntoAttachments(
                header = "Pull Requests (1)",
                items = items,
                color = "#1F2328"
            )

            result shouldHaveSize 1
            result[0].color shouldBe "#1F2328"
            result[0].text shouldBe "*Pull Requests (1):*\nhttps://github.com/monta-app/repo/pull/123|#123"
        }

        "should preserve JIRA brand color in attachments" {
            val items = List(5) { "SRE-${1000 + it}" }
            val result = splitIntoAttachments(
                header = "JIRA Tickets (5)",
                items = items,
                color = "#2068DB"
            )

            result shouldHaveSize 1
            result[0].color shouldBe "#2068DB"
            result[0].text shouldStartWith "*JIRA Tickets (5):*"
        }

        "should preserve GitHub brand color in attachments" {
            val items = List(5) { "#${100 + it}" }
            val result = splitIntoAttachments(
                header = "Pull Requests (5)",
                items = items,
                color = "#1F2328"
            )

            result shouldHaveSize 1
            result[0].color shouldBe "#1F2328"
        }

        "should split very long items correctly" {
            // Create items that are individually long
            val items = List(20) { index ->
                "https://github.com/organization-with-very-long-name/repository-with-very-long-name/pull/${10000 + index}|#${10000 + index} - This is a PR with a very long title that contains lots of text"
            }
            val result = splitIntoAttachments(
                header = "Pull Requests (20)",
                items = items,
                color = "#1F2328"
            )

            // Should be split due to length
            result.size shouldBe 2
            // All attachments should have the same color
            result.forEach { attachment ->
                attachment.color shouldBe "#1F2328"
            }
            // First should have main header
            result[0].text shouldStartWith "*Pull Requests (20):*"
            // Second should have continuation header
            result[1].text shouldStartWith "*Pull Requests (20) (cont'd):*"
        }

        "should not split when items fit within limit" {
            // Create items that fit within the 2500 char limit
            // 20 items of 100 chars each = 2000 chars, well under the limit
            val items = List(20) { "x".repeat(100) }

            val result = splitIntoAttachments(
                header = "Pull Requests (20)",
                items = items,
                color = "#1F2328"
            )

            result shouldHaveSize 1
            result[0].color shouldBe "#1F2328"
        }

        "should respect markdown formatting in attachment text" {
            val items = listOf(
                "<https://github.com/org/repo/pull/1|#1>",
                "<https://github.com/org/repo/pull/2|#2>",
                "<https://github.com/org/repo/pull/3|#3>"
            )
            val result = splitIntoAttachments(
                header = "Pull Requests (3)",
                items = items,
                color = "#1F2328"
            )

            result shouldHaveSize 1
            result[0].mrkdwnIn shouldBe listOf("text")
            result[0].text shouldContain "<https://github.com/org/repo/pull/1|#1>"
            result[0].text shouldContain "<https://github.com/org/repo/pull/2|#2>"
            result[0].text shouldContain "<https://github.com/org/repo/pull/3|#3>"
        }
    })
