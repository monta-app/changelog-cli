package com.monta.changelog.notify

import com.monta.changelog.github.GitHubService
import com.monta.changelog.model.ChangeLog
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun testChangeLog(
    tagName: String = "v1.0.0",
    githubReleaseUrl: String? = null,
    repositoryUrl: String? = "https://github.com/monta-app/changelog-cli",
    previousTagName: String? = null,
) = ChangeLog(
    serviceName = "changelog-cli",
    jiraAppName = null,
    tagName = tagName,
    previousTagName = previousTagName,
    repoOwner = "monta-app",
    repoName = "changelog-cli",
    repositoryUrl = repositoryUrl,
    groupedCommitMap = emptyMap()
).also { it.githubReleaseUrl = githubReleaseUrl }

class ReleaseNotificationServiceTest :
    StringSpec({

        "addContributor should mark a login-based contributor as author" {
            val contributors = mutableMapOf<String, Contributor>()

            addContributor(contributors, login = "alice", prNumber = 1) { it.isAuthor = true }

            contributors.values.single().let { contributor ->
                contributor.login shouldBe "alice"
                contributor.isAuthor shouldBe true
                contributor.isApprover shouldBe false
                contributor.prNumbers shouldBe setOf(1)
            }
        }

        "addContributor should merge author and approver roles for the same login" {
            val contributors = mutableMapOf<String, Contributor>()

            addContributor(contributors, login = "alice", prNumber = 1) { it.isAuthor = true }
            addContributor(contributors, login = "alice", prNumber = 2) { it.isApprover = true }

            contributors.values.single().let { contributor ->
                contributor.isAuthor shouldBe true
                contributor.isApprover shouldBe true
                contributor.prNumbers shouldBe setOf(1, 2)
            }
        }

        "addContributor should skip bot logins" {
            val contributors = mutableMapOf<String, Contributor>()

            addContributor(contributors, login = "dependabot[bot]", prNumber = 1) { it.isAuthor = true }

            contributors.shouldBeEmptyMap()
        }

        "addContributor should skip bot display names when there is no login" {
            val contributors = mutableMapOf<String, Contributor>()

            addContributor(
                contributors,
                email = "1+dependabot[bot]@users.noreply.github.com",
                displayName = "dependabot[bot]",
                prNumber = 1
            ) { it.isCoAuthor = true }

            contributors.shouldBeEmptyMap()
        }

        "addContributor should key a co-author without a login by email" {
            val contributors = mutableMapOf<String, Contributor>()

            addContributor(contributors, email = "jane@example.com", displayName = "Jane Doe", prNumber = 1) { it.isCoAuthor = true }

            contributors.values.single().let { contributor ->
                contributor.login.shouldBeNull()
                contributor.email shouldBe "jane@example.com"
                contributor.displayName shouldBe "Jane Doe"
                contributor.isCoAuthor shouldBe true
            }
        }

        "addContributor should merge a co-author with a known login into an existing author entry" {
            val contributors = mutableMapOf<String, Contributor>()

            addContributor(contributors, login = "alice", prNumber = 1) { it.isApprover = true }
            addContributor(contributors, login = "alice", displayName = "Alice", prNumber = 1) { it.isCoAuthor = true }

            contributors.values.single().let { contributor ->
                contributor.isApprover shouldBe true
                contributor.isCoAuthor shouldBe true
                contributor.isAuthor shouldBe false
            }
        }

        "sortContributors should put approver-only contributors last" {
            val alice = Contributor(login = "alice", email = null, displayName = "alice").apply { isApprover = true }
            val bob = Contributor(login = "bob", email = null, displayName = "bob").apply { isAuthor = true }

            sortContributors(listOf(alice, bob)).map { it.login } shouldContainExactly listOf("bob", "alice")
        }

        "sortContributors should keep co-authors ahead of approver-only contributors" {
            val approver = Contributor(login = "approver-only", email = null, displayName = "approver-only").apply { isApprover = true }
            val coAuthor = Contributor(login = "co-author-only", email = null, displayName = "co-author-only").apply { isCoAuthor = true }

            sortContributors(listOf(approver, coAuthor)).map { it.login } shouldContainExactly listOf("co-author-only", "approver-only")
        }

        "sortContributors should sort alphabetically (case-insensitive) within the same tier" {
            val bob = Contributor(login = "Bob", email = null, displayName = "Bob").apply { isAuthor = true }
            val alice = Contributor(login = "alice", email = null, displayName = "alice").apply { isAuthor = true }

            sortContributors(listOf(bob, alice)).map { it.login } shouldContainExactly listOf("alice", "Bob")
        }

        "buildRoleSuffix should be empty for an author" {
            val contributor = Contributor(login = "alice", email = null, displayName = "alice").apply { isAuthor = true }
            buildRoleSuffix(contributor) shouldBe ""
        }

        "buildRoleSuffix should be empty for an author who also approved or co-authored" {
            val contributor = Contributor(login = "alice", email = null, displayName = "alice").apply {
                isAuthor = true
                isApprover = true
                isCoAuthor = true
            }
            buildRoleSuffix(contributor) shouldBe ""
        }

        "buildRoleSuffix should say approver for an approver-only contributor" {
            val contributor = Contributor(login = "alice", email = null, displayName = "alice").apply { isApprover = true }
            buildRoleSuffix(contributor) shouldBe " (approver)"
        }

        "buildRoleSuffix should say co-author for a co-author-only contributor" {
            val contributor = Contributor(login = "alice", email = null, displayName = "alice").apply { isCoAuthor = true }
            buildRoleSuffix(contributor) shouldBe " (co-author)"
        }

        "buildRoleSuffix should combine co-author and approver roles" {
            val contributor = Contributor(login = "alice", email = null, displayName = "alice").apply {
                isCoAuthor = true
                isApprover = true
            }
            buildRoleSuffix(contributor) shouldBe " (co-author, approver)"
        }

        "buildPrLinks should link every pr number using the known html url" {
            val links = buildPrLinks(
                repoOwner = "monta-app",
                repoName = "changelog-cli",
                prNumbers = setOf(2, 1),
                prUrls = mapOf(1 to "https://github.com/monta-app/changelog-cli/pull/1", 2 to "https://github.com/monta-app/changelog-cli/pull/2")
            )

            links shouldBe "<https://github.com/monta-app/changelog-cli/pull/1|#1> <https://github.com/monta-app/changelog-cli/pull/2|#2>"
        }

        "buildPrLinks should fall back to a constructed url when the html url is unknown" {
            val links = buildPrLinks(
                repoOwner = "monta-app",
                repoName = "changelog-cli",
                prNumbers = setOf(5),
                prUrls = emptyMap()
            )

            links shouldBe "<https://github.com/monta-app/changelog-cli/pull/5|#5>"
        }

        "formatMention should prefer a real slack mention when a slack user id is known" {
            formatMention(slackUserId = "U123", login = "alice", displayName = "alice") shouldBe "<@U123>"
        }

        "formatMention should fall back to a github profile link when there is a login but no slack match" {
            formatMention(slackUserId = null, login = "alice", displayName = "alice") shouldBe "<https://github.com/alice|@alice>"
        }

        "formatMention should fall back to the plain display name when there is no login either" {
            formatMention(slackUserId = null, login = null, displayName = "Jane Doe") shouldBe "Jane Doe"
        }

        "buildMentionLine should combine mention, role suffix and pr links" {
            val contributor = Contributor(login = "alice", email = null, displayName = "alice").apply {
                isApprover = true
                prNumbers.add(1)
            }

            val line = buildMentionLine(
                repoOwner = "monta-app",
                repoName = "changelog-cli",
                contributor = contributor,
                prUrls = emptyMap(),
                mention = "<@U123>"
            )

            line shouldBe "• <@U123> (approver) <https://github.com/monta-app/changelog-cli/pull/1|#1>"
        }

        "shouldSkipNotification should be true when there are no prs and no monitoring urls" {
            shouldSkipNotification(emptyList(), emptyList()) shouldBe true
        }

        "shouldSkipNotification should be false when there are prs even without monitoring urls" {
            shouldSkipNotification(listOf(1), emptyList()) shouldBe false
        }

        "shouldSkipNotification should be false when there are monitoring urls even without prs" {
            shouldSkipNotification(emptyList(), listOf(MonitoringUrl(label = "Grafana", url = "https://grafana.example.com"))) shouldBe false
        }

        "buildContributors should collect the author and approvers of a single pr" {
            val (contributors, prUrls) = buildContributors(
                prNumbers = listOf(42),
                getPullRequestDetails = {
                    GitHubService.PullRequestDetails(
                        number = 42,
                        author = "alice",
                        htmlUrl = "https://github.com/monta-app/changelog-cli/pull/42",
                        approvers = listOf("bob")
                    )
                },
                getPullRequestCommitMessages = { emptyList() }
            )

            prUrls shouldBe mapOf(42 to "https://github.com/monta-app/changelog-cli/pull/42")
            contributors.values.map { Triple(it.login, it.isAuthor, it.isApprover) } shouldContainExactly listOf(
                Triple("alice", true, false),
                Triple("bob", false, true)
            )
        }

        "buildContributors should extract co-authors from pr commit messages" {
            val (contributors, _) = buildContributors(
                prNumbers = listOf(42),
                getPullRequestDetails = {
                    GitHubService.PullRequestDetails(number = 42, author = "alice", htmlUrl = null, approvers = emptyList())
                },
                getPullRequestCommitMessages = {
                    listOf("fix: thing\n\nCo-authored-by: Jane Doe <jane@example.com>")
                }
            )

            contributors.values.map { Triple(it.displayName, it.isAuthor, it.isCoAuthor) } shouldContainExactly listOf(
                Triple("alice", true, false),
                Triple("Jane Doe", false, true)
            )
        }

        "buildContributors should merge a co-author back into an author entry via a github noreply login" {
            val (contributors, _) = buildContributors(
                prNumbers = listOf(1, 2),
                getPullRequestDetails = { prNumber ->
                    if (prNumber == 1) {
                        GitHubService.PullRequestDetails(number = 1, author = "alice", htmlUrl = null, approvers = emptyList())
                    } else {
                        GitHubService.PullRequestDetails(number = 2, author = "bob", htmlUrl = null, approvers = emptyList())
                    }
                },
                getPullRequestCommitMessages = { prNumber ->
                    if (prNumber == 2) {
                        listOf("fix: thing\n\nCo-authored-by: Alice <1+alice@users.noreply.github.com>")
                    } else {
                        emptyList()
                    }
                }
            )

            contributors.size shouldBe 2
            contributors["alice"]!!.let { alice ->
                alice.isAuthor shouldBe true
                alice.isCoAuthor shouldBe true
                alice.prNumbers shouldBe setOf(1, 2)
            }
        }

        "buildContributors should exclude bot authors, approvers and co-authors" {
            val (contributors, _) = buildContributors(
                prNumbers = listOf(1),
                getPullRequestDetails = {
                    GitHubService.PullRequestDetails(
                        number = 1,
                        author = "renovate[bot]",
                        htmlUrl = null,
                        approvers = listOf("dependabot[bot]")
                    )
                },
                getPullRequestCommitMessages = {
                    listOf("fix: thing\n\nCo-authored-by: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>")
                }
            )

            contributors.shouldBeEmptyMap()
        }

        "buildReleaseNotificationBlocks should prefer the github release url over a constructed one" {
            val changeLog = testChangeLog(tagName = "v1.0.0", githubReleaseUrl = "https://github.com/monta-app/changelog-cli/releases/tag/v1.0.0")

            val blocks = buildReleaseNotificationBlocks(changeLog, emptyList(), emptyList())

            blocks shouldHaveSize 1
            blocks[0].text?.text shouldContain "https://github.com/monta-app/changelog-cli/releases/tag/v1.0.0"
        }

        "buildReleaseNotificationBlocks should construct a release url when there is no github release" {
            val changeLog = testChangeLog(tagName = "v1.0.0", githubReleaseUrl = null, repositoryUrl = "https://github.com/monta-app/changelog-cli")

            val blocks = buildReleaseNotificationBlocks(changeLog, emptyList(), emptyList())

            blocks[0].text?.text shouldContain "https://github.com/monta-app/changelog-cli/releases/tag/v1.0.0"
        }

        "buildReleaseNotificationBlocks should omit the dashboards section when there are no monitoring urls" {
            val blocks = buildReleaseNotificationBlocks(testChangeLog(), emptyList(), listOf("• <@U1> #1"))

            blocks shouldHaveSize 2
            blocks.none { it.text?.text?.contains("Dashboards") == true } shouldBe true
        }

        "buildReleaseNotificationBlocks should include dashboards and contributors when both are present" {
            val blocks = buildReleaseNotificationBlocks(
                testChangeLog(),
                listOf(MonitoringUrl(label = "Grafana", url = "https://grafana.example.com")),
                listOf("• <@U1> #1")
            )

            blocks shouldHaveSize 3
            blocks[1].text?.text shouldContain "Grafana"
            blocks[2].text?.text shouldContain "<@U1> #1"
        }
    })

private fun Map<String, Contributor>.shouldBeEmptyMap() {
    this shouldBe emptyMap()
}
