package com.monta.changelog.notify

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private fun contributor(
    login: String?,
    prs: Set<Int>,
    author: Boolean = false,
    coAuthor: Boolean = false,
    approver: Boolean = false,
) = Contributor(
    login = login,
    email = null,
    displayName = login ?: "Someone",
    prNumbers = prs.toMutableSet(),
    isAuthor = author,
    isApprover = approver,
    isCoAuthor = coAuthor
)

class ReleaseMentionServiceTest :
    StringSpec({

        "parseSubscribers lowercases logins and treats a missing or malformed file as nobody" {
            parseSubscribers("""{"logins":["Alice","bob"]}""") shouldBe setOf("alice", "bob")
            parseSubscribers(null) shouldBe emptySet()
            parseSubscribers("not json") shouldBe emptySet()
        }

        "selectReleaseMentions keeps subscribed authors and co-authors, never approvers or non-subscribers" {
            val mentions = selectReleaseMentions(
                contributors = listOf(
                    contributor("Alice", setOf(1, 2), author = true),
                    contributor("bob", setOf(3), coAuthor = true),
                    contributor("carol", setOf(1), approver = true),
                    contributor("dave", setOf(4), author = true),
                    contributor(null, setOf(5), coAuthor = true)
                ),
                triggeredBy = null,
                subscribers = setOf("alice", "bob", "carol")
            )

            mentions shouldContainExactly listOf(
                ReleaseMention("Alice", setOf(1, 2), triggeredRelease = false),
                ReleaseMention("bob", setOf(3), triggeredRelease = false)
            )
        }

        "selectReleaseMentions flags the subscriber who triggered the release, with or without PRs of their own" {
            val withPrs = selectReleaseMentions(
                contributors = listOf(contributor("alice", setOf(1), author = true)),
                triggeredBy = "@Alice",
                subscribers = setOf("alice")
            )
            withPrs shouldContainExactly listOf(ReleaseMention("alice", setOf(1), triggeredRelease = true))

            val triggerOnly = selectReleaseMentions(
                contributors = listOf(contributor("bob", setOf(1), approver = true)),
                triggeredBy = "bob",
                subscribers = setOf("bob")
            )
            triggerOnly shouldContainExactly listOf(ReleaseMention("bob", emptySet(), triggeredRelease = true))

            selectReleaseMentions(emptyList(), triggeredBy = "erin", subscribers = setOf("alice")).shouldBeEmpty()
        }

        "buildReleaseMentionLine links the PRs and notes who triggered the release" {
            buildReleaseMentionLine(
                repoOwner = "monta-app",
                repoName = "changelog-cli",
                mention = ReleaseMention("alice", setOf(2, 1), triggeredRelease = true),
                prUrls = emptyMap(),
                slackUserId = "U123"
            ) shouldBe "• <@U123> <https://github.com/monta-app/changelog-cli/pull/1|#1> " +
                "<https://github.com/monta-app/changelog-cli/pull/2|#2> (triggered this release)"

            buildReleaseMentionLine(
                repoOwner = "monta-app",
                repoName = "changelog-cli",
                mention = ReleaseMention("bob", emptySet(), triggeredRelease = true),
                prUrls = emptyMap(),
                slackUserId = "U456"
            ) shouldBe "• <@U456> (triggered this release)"
        }
    })
