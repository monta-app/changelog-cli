package com.monta.changelog.log

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.model.DeployedSystem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class CommentSectionsTest :
    StringSpec({

        fun changeLog(
            deployedSystems: List<DeployedSystem> = emptyList(),
            deploymentStartTime: String? = null,
            deploymentEndTime: String? = null,
            jiraAppName: String? = "montaapp",
            jiraTickets: List<String> = emptyList(),
            pullRequests: List<String> = emptyList(),
            repositoryUrl: String? = "https://github.com/monta-app/monorepo-typescript",
        ) = ChangeLog(
            serviceName = "TypeScript Monorepo",
            jiraAppName = jiraAppName,
            tagName = "monorepo-2026-08-27-18-48",
            previousTagName = null,
            repoOwner = "monta-app",
            repoName = "monorepo-typescript",
            repositoryUrl = repositoryUrl,
            groupedCommitMap = emptyMap(),
            pullRequests = pullRequests,
            jiraTickets = jiraTickets,
            stage = "production",
            deploymentStartTime = deploymentStartTime,
            deploymentEndTime = deploymentEndTime,
            deployedSystems = deployedSystems
        )

        "effectiveDeploymentWindow prefers scalar times over derived" {
            val window = effectiveDeploymentWindow(
                changeLog(
                    deploymentStartTime = "2026-08-27T18:26:30Z",
                    deploymentEndTime = "2026-08-27T18:31:02Z",
                    deployedSystems = listOf(DeployedSystem(name = "hub", start = "2026-08-27T10:00:00Z", end = "2026-08-27T11:00:00Z"))
                )
            )
            window shouldBe ("2026-08-27T18:26:30Z" to "2026-08-27T18:31:02Z")
        }

        "effectiveDeploymentWindow derives earliest start and latest end from systems" {
            val window = effectiveDeploymentWindow(
                changeLog(
                    deployedSystems = listOf(
                        DeployedSystem(name = "hub", start = "2026-08-27T18:44:54Z", end = "2026-08-27T18:46:34Z"),
                        DeployedSystem(name = "portals", start = "2026-08-27T18:44:47Z", end = "2026-08-27T18:47:04Z")
                    )
                )
            )
            window shouldBe ("2026-08-27T18:44:47Z" to "2026-08-27T18:47:04Z")
        }

        "deploymentWindowLabel appends the total duration" {
            deploymentWindowLabel(
                changeLog(deploymentStartTime = "2026-08-27T18:26:30Z", deploymentEndTime = "2026-08-27T18:31:02Z")
            ) shouldBe "Aug 27, 2026 at 18:26:30 UTC → 18:31:02 UTC (4m 32s)"
        }

        "deploymentWindowLabel is null when there is no window" {
            deploymentWindowLabel(changeLog()) shouldBe null
        }

        "deployedSystemsSection renders a collapsed table with prev → new commit links" {
            val section = deployedSystemsSection(
                changeLog(
                    deployedSystems = listOf(
                        DeployedSystem(
                            name = "control-v2",
                            revision = "ee2026f0000000",
                            previousRevision = "a71add10000000",
                            start = "2026-08-27T18:44:47Z",
                            end = "2026-08-27T18:47:02Z"
                        )
                    )
                )
            )

            section shouldContain "<details><summary><b>Deployed systems (1)</b></summary>"
            section shouldContain "| System | Version | Rolled out |"
            section shouldContain "**control-v2**"
            section shouldContain "[`a71add1`](https://github.com/monta-app/monorepo-typescript/commit/a71add10000000)"
            section shouldContain "[`ee2026f`](https://github.com/monta-app/monorepo-typescript/commit/ee2026f0000000)"
            section shouldContain "→"
            section shouldContain "18:44:47 → 18:47:02 UTC"
            section shouldContain "</details>"
        }

        "deployedSystemsSection links the system name to its ArgoCD app when a url is set" {
            val section = deployedSystemsSection(
                changeLog(
                    deployedSystems = listOf(
                        DeployedSystem(
                            name = "hub",
                            revision = "ee2026f0000000",
                            url = "https://argocd.monta.app/applications/argocd/hub-production"
                        )
                    )
                )
            )

            section shouldContain "[**hub**](https://argocd.monta.app/applications/argocd/hub-production)"
        }

        "deployedSystemsSection marks an unhealthy system and drops the arrow without a previous" {
            val section = deployedSystemsSection(
                changeLog(
                    deployedSystems = listOf(
                        DeployedSystem(name = "studio", revision = "ee2026f0000000", status = "degraded")
                    )
                )
            )
            section shouldContain "**studio** ⚠️ degraded"
            section shouldContain "[`ee2026f`](https://github.com/monta-app/monorepo-typescript/commit/ee2026f0000000)"
            section shouldNotContain "→"
        }

        "deployedSystemsSection is empty for a single-service release" {
            deployedSystemsSection(changeLog()) shouldBe ""
        }

        "foldableLinkSection lists items with a count and is empty when there are none" {
            foldableLinkSection("Pull requests", emptyList()) shouldBe ""

            val section = foldableLinkSection("Pull requests", listOf("[#1](x)", "[#2](y)"))
            section shouldContain "<details><summary><b>Pull requests (2)</b></summary>"
            section shouldContain "- [#1](x)"
            section shouldContain "- [#2](y)"
        }

        "jiraTicketLinks and pullRequestLinks build markdown links" {
            jiraTicketLinks(changeLog(jiraTickets = listOf("ENERGY-2645"))) shouldBe
                listOf("[ENERGY-2645](https://montaapp.atlassian.net/browse/ENERGY-2645)")

            pullRequestLinks(changeLog(pullRequests = listOf("8023"))) shouldBe
                listOf("[#8023](https://github.com/monta-app/monorepo-typescript/pull/8023)")
        }

        "link builders fall back to plain text without an app name or repository url" {
            jiraTicketLinks(changeLog(jiraAppName = null, jiraTickets = listOf("ENERGY-1"))) shouldBe listOf("ENERGY-1")
            pullRequestLinks(changeLog(repositoryUrl = null, pullRequests = listOf("7"))) shouldBe listOf("#7")
        }
    })
