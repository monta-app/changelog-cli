package com.monta.changelog.github

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class GitHubServiceTest :
    StringSpec({

        "isBotActor should return true for bot actors" {
            GitHubService.isBotActor("monta-zipper[bot]") shouldBe true
            GitHubService.isBotActor("dependabot[bot]") shouldBe true
            GitHubService.isBotActor("github-actions[bot]") shouldBe true
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
