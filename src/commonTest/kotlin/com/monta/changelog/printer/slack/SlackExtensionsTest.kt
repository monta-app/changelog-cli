package com.monta.changelog.printer.slack

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.model.DeployedSystem
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
            // Each item is ~74 chars; 120 items (~8.9k) exceeds the 6000 char limit
            val items = List(120) { index ->
                "https://github.com/monta-app/service-geo-with-long-name/pull/${10000 + index}|#${10000 + index}"
            }
            val result = splitIntoAttachments(
                header = "Pull Requests (120)",
                items = items,
                color = "#1F2328"
            )

            // Should be split into at least 2 attachments
            (result.size >= 2) shouldBe true
            result[0].color shouldBe "#1F2328"
            result[0].text shouldStartWith "*Pull Requests (120):*"
            if (result.size > 1) {
                result[1].color shouldBe "#1F2328"
                result[1].text shouldStartWith "*Pull Requests (120) (cont'd):*"
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
            // Each item is ~175 chars; 40 items (~7k) exceeds the 6000 char limit
            val items = List(40) { index ->
                "https://github.com/organization-with-very-long-name/repository-with-very-long-name/pull/${10000 + index}|#${10000 + index} - This is a PR with a very long title that contains lots of text"
            }
            val result = splitIntoAttachments(
                header = "Pull Requests (40)",
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
            result[0].text shouldStartWith "*Pull Requests (40):*"
            // Second should have continuation header
            result[1].text shouldStartWith "*Pull Requests (40) (cont'd):*"
        }

        "should not split when items fit within limit" {
            // 20 items of 100 chars each = 2000 chars, well under the 6000 limit
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

            // Should use containerd grey color
            result.attachments[0].color shouldBe "#575757"

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

            // First: Container info (containerd grey)
            result.attachments[0].color shouldBe "#575757"
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

        "should preserve containerd grey color #575757" {
            val items = listOf("Image: test-image", "Deployed: abc123")
            val result = splitIntoAttachments(
                header = "Container information",
                items = items,
                color = "#575757"
            )

            result shouldHaveSize 1
            result[0].color shouldBe "#575757"
        }

        "should show Deployed for service with Docker info and deployment times" {
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
                imageTag = "abc123",
                deploymentStartTime = "2026-01-14T10:00:00Z",
                deploymentEndTime = "2026-01-14T10:05:00Z",
                stage = "production"
            )

            val result = buildMetadataBlocks(changeLog)

            val summaryBlock = result.blocks.find { it.text?.text?.contains("🚀 Deployed") == true }
            summaryBlock shouldNotBe null
            summaryBlock?.text?.text shouldContain "🚀 Deployed *v1.0.0* to *Production*"
            summaryBlock?.text?.text shouldNotContain "⏳ Deployment pending"
        }

        "should show Released for library/CLI without Docker info but with deployment times" {
            val changeLog = ChangeLog(
                serviceName = "Test CLI",
                jiraAppName = null,
                tagName = "v1.9.58",
                previousTagName = null,
                repoOwner = "test-org",
                repoName = "test-cli",
                repositoryUrl = "https://github.com/test-org/test-cli",
                groupedCommitMap = emptyMap(),
                // No Docker fields
                deploymentStartTime = "2026-01-14T10:00:00Z",
                deploymentEndTime = "2026-01-14T10:05:00Z",
                stage = "production"
            )

            val result = buildMetadataBlocks(changeLog)

            val summaryBlock = result.blocks.find { it.text?.text?.contains("Released") == true }
            summaryBlock shouldNotBe null
            summaryBlock?.text?.text shouldContain "Released *v1.9.58*"
            summaryBlock?.text?.text shouldNotContain "to *Production*" // Libraries don't show stage
            summaryBlock?.text?.text shouldNotContain "Deployed"
        }

        "renders the Deployment timing card when only deploy times are set (no image info)" {
            val changeLog = ChangeLog(
                serviceName = "OCPP Toolkit V2",
                jiraAppName = null,
                tagName = "ocpp-toolkit-v2-2026-08-31-11-19",
                previousTagName = null,
                repoOwner = "monta-app",
                repoName = "service-ocpp-toolkit-v2",
                repositoryUrl = "https://github.com/monta-app/service-ocpp-toolkit-v2",
                groupedCommitMap = emptyMap(),
                // No dockerImage / imageTag — only the deploy window + ArgoCD url
                deploymentStartTime = "2026-08-31T11:18:54Z",
                deploymentEndTime = "2026-08-31T11:19:09Z",
                deploymentUrl = "https://argocd.monta.app/applications/argocd/ocpp-toolkit-v2-production",
                stage = "production"
            )

            val result = buildMetadataBlocks(changeLog)
            result.attachments.any { it.text.startsWith("*Deployment:*") } shouldBe true
        }

        "should show Released for release without deployment times" {
            val changeLog = ChangeLog(
                serviceName = "Test Project",
                jiraAppName = null,
                tagName = "v2.0.0",
                previousTagName = null,
                repoOwner = "test-org",
                repoName = "test-project",
                repositoryUrl = "https://github.com/test-org/test-project",
                groupedCommitMap = emptyMap()
                // No deployment times, no Docker info
            )

            val result = buildMetadataBlocks(changeLog)

            val summaryBlock = result.blocks.find { it.text?.text?.contains("Released") == true }
            summaryBlock shouldNotBe null
            summaryBlock?.text?.text shouldContain "Released *v2.0.0*"
            summaryBlock?.text?.text shouldNotContain "Deployed"
        }

        "should show deployment pending for service without deployment times" {
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
                imageTag = "abc123",
                stage = "production"
                // No deployment times
            )

            val result = buildMetadataBlocks(changeLog)

            val summaryBlock = result.blocks.find { it.text?.text?.contains("Released") == true }
            summaryBlock shouldNotBe null
            summaryBlock?.text?.text shouldContain "Released *v1.0.0*"
            summaryBlock?.text?.text shouldContain "⏳ Deployment pending"
            summaryBlock?.text?.text shouldNotContain "Deployed"
        }

        "should not show deployment pending for library without deployment times" {
            val changeLog = ChangeLog(
                serviceName = "Test Library",
                jiraAppName = null,
                tagName = "v2.5.0",
                previousTagName = null,
                repoOwner = "test-org",
                repoName = "test-lib",
                repositoryUrl = "https://github.com/test-org/test-lib",
                groupedCommitMap = emptyMap(),
                stage = "production"
                // No deployment times, no Docker info - this is a library
            )

            val result = buildMetadataBlocks(changeLog)

            val summaryBlock = result.blocks.find { it.text?.text?.contains("Released") == true }
            summaryBlock shouldNotBe null
            summaryBlock?.text?.text shouldContain "Released *v2.5.0*"
            // Libraries don't show "Deployment pending" - they're just released
            summaryBlock?.text?.text shouldNotContain "⏳ Deployment pending"
        }

        "should show rocket emoji for service with Docker info and deployment times" {
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
                imageTag = "abc123",
                deploymentStartTime = "2026-01-15T10:00:00Z",
                deploymentEndTime = "2026-01-15T10:05:00Z",
                stage = "production"
            )

            val result = buildMetadataBlocks(changeLog)

            val summaryBlock = result.blocks.find { it.text?.text?.contains("🚀") == true }
            summaryBlock shouldNotBe null
            summaryBlock?.text?.text shouldContain "🚀 Deployed *v1.0.0*"
            summaryBlock?.text?.text shouldContain "to *Production*"
            summaryBlock?.text?.text shouldNotContain "⏳ Deployment pending"
        }

        "should not show rocket emoji for library with deployment times" {
            val changeLog = ChangeLog(
                serviceName = "Test Library",
                jiraAppName = null,
                tagName = "v2.5.0",
                previousTagName = null,
                repoOwner = "test-org",
                repoName = "test-lib",
                repositoryUrl = "https://github.com/test-org/test-lib",
                groupedCommitMap = emptyMap(),
                deploymentStartTime = "2026-01-15T10:00:00Z",
                deploymentEndTime = "2026-01-15T10:05:00Z",
                stage = "production"
                // No Docker info - this is a library
            )

            val result = buildMetadataBlocks(changeLog)

            val summaryBlock = result.blocks.find { it.text?.text?.contains("Released") == true }
            summaryBlock shouldNotBe null
            summaryBlock?.text?.text shouldContain "Released *v2.5.0*"
            summaryBlock?.text?.text shouldNotContain "to *Production*" // Libraries don't show stage
            summaryBlock?.text?.text shouldNotContain "🚀"
            summaryBlock?.text?.text shouldNotContain "⏳ Deployment pending"
        }

        fun monorepoChangeLog(systems: List<DeployedSystem>) = ChangeLog(
            serviceName = "TypeScript Monorepo",
            jiraAppName = null,
            tagName = "monorepo-2026-08-27-14-50",
            previousTagName = "monorepo-2026-08-27-14-40",
            repoOwner = "monta-app",
            repoName = "monorepo-typescript",
            repositoryUrl = "https://github.com/monta-app/monorepo-typescript",
            groupedCommitMap = emptyMap(),
            stage = "production",
            deployedSystems = systems
        )

        "deployed systems attachment replaces container info, green when all healthy" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(
                    listOf(
                        DeployedSystem(name = "hub", start = "2026-08-27T14:19:01Z", end = "2026-08-27T14:21:00Z", status = "healthy"),
                        DeployedSystem(name = "portals", start = "2026-08-27T14:19:05Z", end = "2026-08-27T14:22:30Z", status = "healthy")
                    )
                )
            )
            val deployed = result.attachments.first()
            deployed.color shouldBe "#2EB67D"
            deployed.text shouldStartWith "*Deployed systems (2):*"
            deployed.text shouldContain "*hub*"
            deployed.text shouldContain "14:19:01 → 14:21:00 UTC"
            result.attachments.any { it.color == "#575757" } shouldBe false
        }

        "deployed systems attachment links the system name to its ArgoCD app when a url is set" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(
                    listOf(
                        DeployedSystem(
                            name = "hub",
                            status = "healthy",
                            url = "https://argocd.monta.app/applications/argocd/hub-production"
                        )
                    )
                )
            )
            result.attachments.first().text shouldContain "<https://argocd.monta.app/applications/argocd/hub-production|*hub*>"
        }

        "deployed systems attachment is yellow on partial and red on all-failed" {
            fun colorFor(statuses: List<String>) = buildMetadataBlocks(
                monorepoChangeLog(statuses.mapIndexed { i, s -> DeployedSystem(name = "svc$i", status = s) })
            ).attachments.first().color

            colorFor(listOf("healthy", "degraded")) shouldBe "#ECB22E"
            colorFor(listOf("degraded", "timeout")) shouldBe "#E01E5A"
        }

        "not-healthy system shows a status glyph" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(listOf(DeployedSystem(name = "studio", status = "degraded")))
            )
            result.attachments.first().text shouldContain "⚠️ degraded"
        }

        "main message includes a deployed-systems summary line with +N" {
            val summary = buildSlackBlocks(emptyList(), monorepoChangeLog((1..6).map { DeployedSystem(name = "svc$it") }))
                .flatten()
                .mapNotNull { it.text?.text }
                .firstOrNull { it.contains("systems:") }
            summary shouldNotBe null
            summary!! shouldContain "*6 systems:*"
            summary shouldContain "+3"
        }

        "deployment summary shows deployed state without inlining the window" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(
                    listOf(
                        DeployedSystem(name = "hub", start = "2026-08-27T14:19:01Z", end = "2026-08-27T14:21:00Z"),
                        DeployedSystem(name = "portals", start = "2026-08-27T14:19:05Z", end = "2026-08-27T14:22:30Z")
                    )
                )
            )

            val summary = result.blocks.mapNotNull { it.text?.text }.firstOrNull { it.contains("🚀 Deployed") }
            summary shouldNotBe null
            summary!! shouldContain "to *Production*"
            summary shouldNotContain "14:19:01"

            val deployed = result.attachments.first { it.text.startsWith("*Deployed systems") }
            deployed.text shouldContain "14:19:01 → 14:21:00 UTC"
            deployed.text shouldContain "14:19:05 → 14:22:30 UTC"
        }

        "deployed systems card leads with the overall window and total duration" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(
                    listOf(
                        DeployedSystem(name = "hub", start = "2026-08-27T18:44:47Z", end = "2026-08-27T18:47:02Z"),
                        DeployedSystem(name = "portals", start = "2026-08-27T18:44:51Z", end = "2026-08-27T18:47:04Z")
                    )
                )
            )

            val deployed = result.attachments.first { it.text.startsWith("*Deployed systems") }
            // earliest start 18:44:47 -> latest end 18:47:04, total 2m 17s
            deployed.text shouldContain "⏱️ 18:44:47 → 18:47:04 UTC · *2m 17s*"
            // overview precedes the per-system rows
            (deployed.text.indexOf("⏱️") < deployed.text.indexOf("*hub*")) shouldBe true
        }

        "single system shows duration on its row without a repeated overview line" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(
                    listOf(DeployedSystem(name = "hub", start = "2026-08-27T21:09:48Z", end = "2026-08-27T21:11:16Z"))
                )
            )

            val deployed = result.attachments.first { it.text.startsWith("*Deployed systems") }
            deployed.text shouldContain "• *hub* — 21:09:48 → 21:11:16 UTC · *1m 28s*"
            // no separate overview line repeating the same window
            deployed.text shouldNotContain "⏱️"
        }

        "single-service deployment shows how long it took" {
            val changeLog = ChangeLog(
                serviceName = "Monta PHP Monolith",
                jiraAppName = null,
                tagName = "2026-08-27-18-31",
                previousTagName = null,
                repoOwner = "monta-app",
                repoName = "server-php",
                repositoryUrl = "https://github.com/monta-app/server-php",
                groupedCommitMap = emptyMap(),
                dockerImage = "server-php-production",
                imageTag = "2f4caa6",
                stage = "production",
                deploymentStartTime = "2026-08-27T18:26:30Z",
                deploymentEndTime = "2026-08-27T18:31:02Z"
            )

            val result = buildMetadataBlocks(changeLog)

            val deployment = result.attachments.first { it.text.startsWith("*Deployment:*") }
            deployment.color shouldBe "#2EB67D"
            deployment.text shouldContain "→ 18:31:02 UTC · *4m 32s*"
        }

        "containers attachment links previous → new revision per system" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(
                    listOf(
                        DeployedSystem(name = "hub", revision = "80aad1c0000000", previousRevision = "1f4c9a20000000"),
                        DeployedSystem(name = "portals", revision = "80aad1c0000000", previousRevision = "abcdef10000000")
                    )
                )
            )

            val containers = result.attachments.first { it.text.startsWith("*Containers") }
            containers.color shouldBe "#575757"
            containers.text shouldStartWith "*Containers (2):*"
            containers.text shouldContain "*hub*"
            containers.text shouldContain "https://github.com/monta-app/monorepo-typescript/commit/1f4c9a20000000|`1f4c9a2`"
            containers.text shouldContain "https://github.com/monta-app/monorepo-typescript/commit/80aad1c0000000|`80aad1c`"
            containers.text shouldContain "→"
        }

        "containers card stays a single attachment for 15 systems (no cont'd split)" {
            val systems = (1..15).map { i ->
                DeployedSystem(name = "service-number-$i", revision = "80aad1c0000000", previousRevision = "1f4c9a20000000")
            }
            val result = buildMetadataBlocks(monorepoChangeLog(systems))

            val containerAttachments = result.attachments.filter { it.text.startsWith("*Containers") }
            containerAttachments shouldHaveSize 1
            containerAttachments.single().text shouldStartWith "*Containers (15):*"
            result.attachments.none { it.text.contains("(cont'd)") } shouldBe true
        }

        "container row drops the arrow when there is no previous revision" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(listOf(DeployedSystem(name = "hub", revision = "80aad1c0000000")))
            )

            val containers = result.attachments.first { it.text.startsWith("*Containers") }
            containers.text shouldContain "*hub* — <https://github.com/monta-app/monorepo-typescript/commit/80aad1c0000000|`80aad1c`>"
            containers.text shouldNotContain "→"
        }

        "multi-service deployment suppresses the single-service container card" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(listOf(DeployedSystem(name = "hub", revision = "80aad1c0000000")))
                    .copy(dockerImage = "ghcr.io/monta-app/x", imageTag = "80aad1c0000000")
            )

            result.attachments.none { it.text.startsWith("*Container information") } shouldBe true
            result.attachments.any { it.text.startsWith("*Containers") } shouldBe true
        }

        "systems without a revision produce no containers card" {
            val result = buildMetadataBlocks(
                monorepoChangeLog(listOf(DeployedSystem(name = "hub", start = "2026-08-27T14:19:01Z", end = "2026-08-27T14:21:00Z")))
            )
            result.attachments.any { it.text.startsWith("*Containers") } shouldBe false
        }
    })
