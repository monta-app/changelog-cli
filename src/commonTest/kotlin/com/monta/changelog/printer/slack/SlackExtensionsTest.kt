package com.monta.changelog.printer.slack

import com.monta.changelog.model.ChangeLog
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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

        "should create container attachment with all Docker fields" {
            val changeLog = ChangeLog(
                serviceName = "Test Service",
                jiraAppName = null,
                tagName = "v1.0.0",
                previousTagName = null,
                repoOwner = "test-org",
                repoName = "test-repo",
                repositoryUrl = "https://github.com/test-org/test-repo",
                groupedCommitMap = emptyMap(),
                dockerImage = "077199819609.dkr.ecr.eu-west-1.amazonaws.com/test-service",
                imageTag = "04824e5bbe9884e6000de802b17e2ddeed931b88",
                previousImageTag = "a1b2c3d4e5f6789012345678901234567890abcd"
            )

            val result = buildMetadataBlocks(changeLog)

            // Should have container attachment
            result.attachments shouldHaveSize 1

            // Should use Docker blue color
            result.attachments[0].color shouldBe "#2563ED"

            // Should use new labels "Deployed" and "Previous"
            result.attachments[0].text shouldContain "Deployed:"
            result.attachments[0].text shouldContain "Previous:"
            result.attachments[0].text shouldContain "Image:"

            // Should NOT use old labels
            result.attachments[0].text shouldNotContain "Tag:"
            result.attachments[0].text shouldNotContain "Previous Tag:"
        }

        "should not create container attachment when Docker info is missing" {
            val changeLog = ChangeLog(
                serviceName = "Test Service",
                jiraAppName = null,
                tagName = "v1.0.0",
                previousTagName = null,
                repoOwner = "test-org",
                repoName = "test-repo",
                repositoryUrl = "https://github.com/test-org/test-repo",
                groupedCommitMap = emptyMap()
                // No Docker fields
            )

            val result = buildMetadataBlocks(changeLog)

            // Should have no attachments
            result.attachments shouldHaveSize 0
        }

        "should create container attachment with partial Docker info" {
            val changeLog = ChangeLog(
                serviceName = "Test Service",
                jiraAppName = null,
                tagName = "v1.0.0",
                previousTagName = null,
                repoOwner = "test-org",
                repoName = "test-repo",
                repositoryUrl = "https://github.com/test-org/test-repo",
                groupedCommitMap = emptyMap(),
                dockerImage = "test-image",
                imageTag = "abc123"
                // No previousImageTag
            )

            val result = buildMetadataBlocks(changeLog)

            result.attachments shouldHaveSize 1
            result.attachments[0].text shouldContain "Image:"
            result.attachments[0].text shouldContain "Deployed:"
            result.attachments[0].text shouldNotContain "Previous:"
        }

        "should order attachments correctly: container, JIRA, PRs" {
            val changeLog = ChangeLog(
                serviceName = "Test Service",
                jiraAppName = "testapp",
                tagName = "v1.0.0",
                previousTagName = null,
                repoOwner = "test-org",
                repoName = "test-repo",
                repositoryUrl = "https://github.com/test-org/test-repo",
                groupedCommitMap = emptyMap(),
                dockerImage = "test-image",
                imageTag = "abc123",
                previousImageTag = "def456",
                jiraTickets = listOf("SRE-123"),
                pullRequests = listOf("1", "2")
            )

            val result = buildMetadataBlocks(changeLog)

            result.attachments shouldHaveSize 3

            // First: Container info (Docker blue)
            result.attachments[0].color shouldBe "#2563ED"
            result.attachments[0].text shouldStartWith "*Container information:*"

            // Second: JIRA (JIRA blue)
            result.attachments[1].color shouldBe "#2068DB"
            result.attachments[1].text shouldStartWith "*JIRA Tickets"

            // Third: PRs (GitHub gray)
            result.attachments[2].color shouldBe "#1F2328"
            result.attachments[2].text shouldStartWith "*Pull Requests"
        }

        "should not include duplicate Repository and Triggered By fields" {
            val changeLog = ChangeLog(
                serviceName = "Test Service",
                jiraAppName = null,
                tagName = "v1.0.0",
                previousTagName = null,
                repoOwner = "test-org",
                repoName = "test-repo",
                repositoryUrl = "https://github.com/test-org/test-repo",
                groupedCommitMap = emptyMap(),
                triggeredBy = "testuser",
                triggeredByName = "Test User"
            )

            val result = buildMetadataBlocks(changeLog)

            // Should have summary block with Repository and Triggered By
            val summaryBlock = result.blocks.find { it.text?.text?.contains("Released") == true }
            summaryBlock shouldNotBe null
            summaryBlock?.text?.text shouldContain "Repository"
            summaryBlock?.text?.text shouldContain "testuser"

            // Should have no field blocks with Repository or Triggered By
            val fieldBlocks = result.blocks.filter { it.fields != null }
            fieldBlocks.forEach { block ->
                block.fields?.forEach { field ->
                    field.text shouldNotContain "Repository:"
                    field.text shouldNotContain "Triggered By:"
                }
            }
        }

        "should preserve Docker brand color #2563ED" {
            val items = listOf("Image: test-image", "Deployed: abc123")
            val result = splitIntoAttachments(
                header = "Container information",
                items = items,
                color = "#2563ED"
            )

            result shouldHaveSize 1
            result[0].color shouldBe "#2563ED"
        }
    })
