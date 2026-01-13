package com.monta.changelog.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class LinkResolverTest :
    StringSpec({

        "should create hyperlinks for valid JIRA tickets" {
            val resolver = LinkResolver.Jira(
                jiraAppName = "montaapp",
                validTickets = setOf("ABC-123", "DEF-456")
            )

            val result = resolver.resolve(
                markdownFormatter = MarkdownFormatter.Slack,
                message = "Fix bug [ABC-123] and [DEF-456]"
            )

            result shouldContain "<https://montaapp.atlassian.net/browse/ABC-123|ABC-123>"
            result shouldContain "<https://montaapp.atlassian.net/browse/DEF-456|DEF-456>"
        }

        "should not create hyperlinks for invalid JIRA tickets" {
            val resolver = LinkResolver.Jira(
                jiraAppName = "montaapp",
                validTickets = setOf("ABC-123") // Only ABC-123 is valid
            )

            val result = resolver.resolve(
                markdownFormatter = MarkdownFormatter.Slack,
                message = "Fix [ABC-123] and [FAKE-999]"
            )

            // ABC-123 should be hyperlinked
            result shouldContain "<https://montaapp.atlassian.net/browse/ABC-123|ABC-123>"

            // FAKE-999 should remain as plain text (not hyperlinked)
            result shouldContain "[FAKE-999]"
            result shouldNotContain "https://montaapp.atlassian.net/browse/FAKE-999"
        }

        "should create hyperlinks for all tickets when validTickets is null" {
            val resolver = LinkResolver.Jira(
                jiraAppName = "montaapp",
                validTickets = null // No validation
            )

            val result = resolver.resolve(
                markdownFormatter = MarkdownFormatter.Slack,
                message = "Fix [ABC-123] and [FAKE-999]"
            )

            // Both should be hyperlinked when validation is disabled
            result shouldContain "<https://montaapp.atlassian.net/browse/ABC-123|ABC-123>"
            result shouldContain "<https://montaapp.atlassian.net/browse/FAKE-999|FAKE-999>"
        }

        "should create hyperlinks for valid PRs" {
            val resolver = LinkResolver.Github(
                repoOwner = "monta-app",
                repoName = "changelog-cli",
                validPullRequests = setOf("123", "456")
            )

            val result = resolver.resolve(
                markdownFormatter = MarkdownFormatter.Slack,
                message = "Merge #123 and #456"
            )

            result shouldContain "<https://github.com/monta-app/changelog-cli/pull/123|#123>"
            result shouldContain "<https://github.com/monta-app/changelog-cli/pull/456|#456>"
        }

        "should not create hyperlinks for invalid PRs" {
            val resolver = LinkResolver.Github(
                repoOwner = "monta-app",
                repoName = "changelog-cli",
                validPullRequests = setOf("123") // Only #123 is valid
            )

            val result = resolver.resolve(
                markdownFormatter = MarkdownFormatter.Slack,
                message = "Merge #123 and #999"
            )

            // #123 should be hyperlinked
            result shouldContain "<https://github.com/monta-app/changelog-cli/pull/123|#123>"

            // #999 should remain as plain text (not hyperlinked)
            result shouldContain "#999"
            result shouldNotContain "https://github.com/monta-app/changelog-cli/pull/999"
        }

        "should create hyperlinks for all PRs when validPullRequests is null" {
            val resolver = LinkResolver.Github(
                repoOwner = "monta-app",
                repoName = "changelog-cli",
                validPullRequests = null // No validation
            )

            val result = resolver.resolve(
                markdownFormatter = MarkdownFormatter.Slack,
                message = "Merge #123 and #999"
            )

            // Both should be hyperlinked when validation is disabled
            result shouldContain "<https://github.com/monta-app/changelog-cli/pull/123|#123>"
            result shouldContain "<https://github.com/monta-app/changelog-cli/pull/999|#999>"
        }

        "should handle multiple JIRA tickets in one message" {
            val resolver = LinkResolver.Jira(
                jiraAppName = "montaapp",
                validTickets = setOf("ABC-123", "ABC-456")
            )

            val result = resolver.resolve(
                markdownFormatter = MarkdownFormatter.Slack,
                message = "Fix [ABC-123] [ABC-456] and [INVALID-999]"
            )

            result shouldContain "<https://montaapp.atlassian.net/browse/ABC-123|ABC-123>"
            result shouldContain "<https://montaapp.atlassian.net/browse/ABC-456|ABC-456>"
            result shouldContain "[INVALID-999]"
            result shouldNotContain "https://montaapp.atlassian.net/browse/INVALID-999"
        }

        "should handle multiple PRs in one message" {
            val resolver = LinkResolver.Github(
                repoOwner = "monta-app",
                repoName = "test-repo",
                validPullRequests = setOf("10", "20")
            )

            val result = resolver.resolve(
                markdownFormatter = MarkdownFormatter.Slack,
                message = "Merge #10 #20 and #99"
            )

            result shouldContain "<https://github.com/monta-app/test-repo/pull/10|#10>"
            result shouldContain "<https://github.com/monta-app/test-repo/pull/20|#20>"
            result shouldContain "#99"
            result shouldNotContain "https://github.com/monta-app/test-repo/pull/99"
        }
    })
