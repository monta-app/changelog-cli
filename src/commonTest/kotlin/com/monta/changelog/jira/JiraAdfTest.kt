package com.monta.changelog.jira

import com.monta.changelog.model.ChangeLog
import com.monta.changelog.model.DeployedSystem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun changeLog(
    deployedSystems: List<DeployedSystem> = emptyList(),
    jiraTickets: List<String> = emptyList(),
    pullRequests: List<String> = emptyList(),
    deploymentStartTime: String? = null,
    deploymentEndTime: String? = null,
) = ChangeLog(
    serviceName = "TypeScript Monorepo",
    jiraAppName = "montaapp",
    tagName = "monorepo-2026-08-27-18-48",
    previousTagName = null,
    repoOwner = "monta-app",
    repoName = "monorepo-typescript",
    repositoryUrl = "https://github.com/monta-app/monorepo-typescript",
    groupedCommitMap = emptyMap(),
    pullRequests = pullRequests,
    jiraTickets = jiraTickets,
    stage = "production",
    deploymentStartTime = deploymentStartTime,
    deploymentEndTime = deploymentEndTime,
    deployedSystems = deployedSystems
)

/** All text across a node subtree, in order. */
private fun AdfNode.flatText(): String = (text ?: "") + (content?.joinToString("") { it.flatText() } ?: "")

class JiraAdfTest :
    StringSpec({

        "deployedSystemsExpand nests expand > bulletList > listItem > paragraph with linked revisions" {
            val expand = deployedSystemsExpand(
                changeLog(
                    deployedSystems = listOf(
                        DeployedSystem(
                            name = "hub",
                            revision = "ee2026f0000000",
                            previousRevision = "a71add10000000",
                            start = "2026-08-27T18:44:47Z",
                            end = "2026-08-27T18:47:02Z"
                        )
                    )
                )
            )!!

            expand.type shouldBe "expand"
            expand.attrs?.title shouldBe "Deployed systems (1)"

            val bulletList = expand.content!!.single()
            bulletList.type shouldBe "bulletList"
            val paragraph = bulletList.content!!.single().content!!.single()
            paragraph.type shouldBe "paragraph"

            paragraph.flatText() shouldContain "hub"
            paragraph.flatText() shouldContain "a71add1"
            paragraph.flatText() shouldContain "ee2026f"
            paragraph.flatText() shouldContain "18:44:47 → 18:47:02 UTC"

            // system name is bold
            paragraph.content!!.any { it.text == "hub" && it.marks?.any { m -> m.type == "strong" } == true } shouldBe true
            // previous revision links to its commit
            paragraph.content.any {
                it.text == "a71add1" &&
                    it.marks?.any { m -> m.type == "link" && m.attrs?.href == "https://github.com/monta-app/monorepo-typescript/commit/a71add10000000" } == true
            } shouldBe true
        }

        "deployedSystemsExpand links the system name to its ArgoCD app when a url is set" {
            val expand = deployedSystemsExpand(
                changeLog(
                    deployedSystems = listOf(
                        DeployedSystem(
                            name = "hub",
                            revision = "ee2026f0000000",
                            url = "https://argocd.monta.app/applications/argocd/hub-production"
                        )
                    )
                )
            )!!

            val paragraph = expand.content!!.single().content!!.single().content!!.single()
            paragraph.content!!.any {
                it.text == "hub" &&
                    it.marks?.any { m -> m.type == "strong" } == true &&
                    it.marks?.any { m -> m.type == "link" && m.attrs?.href == "https://argocd.monta.app/applications/argocd/hub-production" } == true
            } shouldBe true
        }

        "deployedSystemsExpand marks an unhealthy system and drops the arrow without a previous" {
            val expand = deployedSystemsExpand(
                changeLog(deployedSystems = listOf(DeployedSystem(name = "studio", revision = "ee2026f0000000", status = "degraded")))
            )!!
            val text = expand.flatText()
            text shouldContain "studio"
            text shouldContain "⚠️ degraded"
            text shouldContain "ee2026f"
            (text.contains("→")) shouldBe false
        }

        "deployedSystemsExpand is null for a single-service release" {
            deployedSystemsExpand(changeLog()) shouldBe null
        }

        "linkExpand titles with a count and is null when empty" {
            linkExpand("Deployed tickets", emptyList()) shouldBe null

            val expand = linkExpand("Deployed tickets", listOf("[ENERGY-2645](https://montaapp.atlassian.net/browse/ENERGY-2645)"))!!
            expand.type shouldBe "expand"
            expand.attrs?.title shouldBe "Deployed tickets (1)"
            expand.flatText() shouldContain "ENERGY-2645"
        }

        "buildJiraCommentDocument keeps header/footer as blocks and adds expand panels" {
            val doc = buildJiraCommentDocument(
                headerMarkdown = "## 🚀 Production Deployment",
                changelogMarkdown = "🚀 **Feature**\n- did a thing",
                footerMarkdown = "---\nDeployed to **Production**",
                changeLog = changeLog(
                    deployedSystems = listOf(DeployedSystem(name = "hub", revision = "ee2026f0000000")),
                    jiraTickets = listOf("ENERGY-2645"),
                    pullRequests = listOf("8023")
                )
            )

            doc.type shouldBe "doc"
            doc.content.first().type shouldBe "heading"
            val expandTitles = doc.content.filter { it.type == "expand" }.mapNotNull { it.attrs?.title }
            expandTitles shouldBe listOf("Deployed systems (1)", "Deployed tickets (1)", "Pull requests (1)")
            doc.content.last().flatText() shouldContain "Deployed to Production"
        }

        "parseInlineMarkdown converts links and bold to marked text nodes" {
            val nodes = parseInlineMarkdown("see [PR](https://x/1) and **bold**")
            nodes.any { it.text == "PR" && it.marks?.any { m -> m.type == "link" } == true } shouldBe true
            nodes.any { it.text == "bold" && it.marks?.any { m -> m.type == "strong" } == true } shouldBe true
        }
    })
