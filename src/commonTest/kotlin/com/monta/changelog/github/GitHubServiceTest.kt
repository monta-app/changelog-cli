package com.monta.changelog.github

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class GitHubServiceTest :
    StringSpec({

        "fetchAllPages should read past a full first page until a short page" {
            val requested = mutableListOf<Int>()
            val pages = mapOf(1 to List(3) { "a$it" }, 2 to List(3) { "b$it" }, 3 to listOf("c0"))
            val items = GitHubService.fetchAllPages(pageSize = 3) { page ->
                requested += page
                pages.getValue(page)
            }
            items shouldBe pages.getValue(1) + pages.getValue(2) + pages.getValue(3)
            requested shouldBe listOf(1, 2, 3)
        }

        "fetchAllPages should stop after one page when it is short" {
            val requested = mutableListOf<Int>()
            val items = GitHubService.fetchAllPages(pageSize = 3) { page ->
                requested += page
                listOf("only")
            }
            items shouldBe listOf("only")
            requested shouldBe listOf(1)
        }

        "fetchAllPages should return null when any page fails" {
            GitHubService.fetchAllPages(pageSize = 2) { page ->
                if (page == 1) listOf("a", "b") else null
            }.shouldBeNull()
        }

        "fetchAllPages should stop at maxPages" {
            val requested = mutableListOf<Int>()
            val items = GitHubService.fetchAllPages(pageSize = 1, maxPages = 2) { page ->
                requested += page
                listOf("p$page")
            }
            items shouldBe listOf("p1", "p2")
            requested shouldBe listOf(1, 2)
        }

        "automerge label beyond the first page of events is found once every page is read" {
            // PR #28607 on monta-app/server: 39 events, automerge added as event 36
            val events = List(35) { GitHubService.IssueEvent(event = "committed") } +
                GitHubService.IssueEvent(
                    event = "labeled",
                    actor = GitHubService.EventActor("jnamenyi"),
                    label = GitHubService.EventLabel("automerge")
                ) +
                List(3) { GitHubService.IssueEvent(event = "merged", actor = GitHubService.EventActor("monta-zipper[bot]")) }
            val read = GitHubService.fetchAllPages(pageSize = 30) { page ->
                events.drop((page - 1) * 30).take(30)
            }!!
            GitHubService.findLabelAdderFromEvents(events.take(30), "automerge").shouldBeNull()
            GitHubService.findLabelAdderFromEvents(read, "automerge") shouldBe "jnamenyi"
        }

        "isBotActor should return true for bot actors" {
            GitHubService.isBotActor("monta-zipper[bot]") shouldBe true
            GitHubService.isBotActor("dependabot[bot]") shouldBe true
            GitHubService.isBotActor("github-actions[bot]") shouldBe true
            GitHubService.isBotActor("claude") shouldBe true
        }

        "isBotActor should return true for any display name whose first word is Claude" {
            GitHubService.isBotActor("Claude Sonnet 5") shouldBe true
            GitHubService.isBotActor("Claude Opus 4.6") shouldBe true
            GitHubService.isBotActor("Claude") shouldBe true
            GitHubService.isBotActor("claude sonnet 5") shouldBe true
            GitHubService.isBotActor("CLAUDE") shouldBe true
        }

        "isBotActor should not match a name that merely starts with the letters Claude" {
            GitHubService.isBotActor("Claudette Smith") shouldBe false
            GitHubService.isBotActor("Claudio Bianchi") shouldBe false
        }

        "isBotActor should not match Claude appearing later in the name" {
            GitHubService.isBotActor("Not Claude") shouldBe false
        }

        "isBotActor should return false for human actors" {
            GitHubService.isBotActor("JesperTerkelsen") shouldBe false
            GitHubService.isBotActor("some-user") shouldBe false
        }

        "isBotActor should return false for null" {
            GitHubService.isBotActor(null) shouldBe false
        }

        "findLabelAdderFromEvents should find who added automerge label" {
            val events = listOf(
                GitHubService.IssueEvent(
                    event = "labeled",
                    actor = GitHubService.EventActor(login = "JesperTerkelsen"),
                    label = GitHubService.EventLabel(name = "automerge")
                )
            )

            GitHubService.findLabelAdderFromEvents(events, "automerge") shouldBe "JesperTerkelsen"
        }

        "findLabelAdderFromEvents should return last adder when label added multiple times" {
            val events = listOf(
                GitHubService.IssueEvent(
                    event = "labeled",
                    actor = GitHubService.EventActor(login = "user-a"),
                    label = GitHubService.EventLabel(name = "automerge")
                ),
                GitHubService.IssueEvent(
                    event = "unlabeled",
                    actor = GitHubService.EventActor(login = "user-a"),
                    label = GitHubService.EventLabel(name = "automerge")
                ),
                GitHubService.IssueEvent(
                    event = "labeled",
                    actor = GitHubService.EventActor(login = "user-b"),
                    label = GitHubService.EventLabel(name = "automerge")
                )
            )

            GitHubService.findLabelAdderFromEvents(events, "automerge") shouldBe "user-b"
        }

        "findLabelAdderFromEvents should ignore other labels" {
            val events = listOf(
                GitHubService.IssueEvent(
                    event = "labeled",
                    actor = GitHubService.EventActor(login = "some-user"),
                    label = GitHubService.EventLabel(name = "bug")
                ),
                GitHubService.IssueEvent(
                    event = "labeled",
                    actor = GitHubService.EventActor(login = "other-user"),
                    label = GitHubService.EventLabel(name = "enhancement")
                )
            )

            GitHubService.findLabelAdderFromEvents(events, "automerge").shouldBeNull()
        }

        "findLabelAdderFromEvents should return null for empty events" {
            GitHubService.findLabelAdderFromEvents(emptyList(), "automerge").shouldBeNull()
        }

        "findLabelAdderFromEvents should ignore non-labeled events" {
            val events = listOf(
                GitHubService.IssueEvent(
                    event = "closed",
                    actor = GitHubService.EventActor(login = "some-user"),
                    label = null
                ),
                GitHubService.IssueEvent(
                    event = "merged",
                    actor = GitHubService.EventActor(login = "monta-zipper[bot]"),
                    label = null
                )
            )

            GitHubService.findLabelAdderFromEvents(events, "automerge").shouldBeNull()
        }

        "findLabelAdderFromEvents should handle events with null actor" {
            val events = listOf(
                GitHubService.IssueEvent(
                    event = "labeled",
                    actor = null,
                    label = GitHubService.EventLabel(name = "automerge")
                )
            )

            GitHubService.findLabelAdderFromEvents(events, "automerge").shouldBeNull()
        }
    })
