package com.monta.changelog.notify

import com.monta.changelog.identity.IdentityPerson
import com.monta.changelog.model.ChangeLog
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private const val DASHBOARD_URL =
    "https://montaapp.grafana.net/d/ja52q4d/server-error-dashboard?from=now-30m&to=now&var-container=\$__all"

private val serverErrorDashboard = MonitoringUrl(label = "Server Error Dashboard", url = DASHBOARD_URL)

private fun dmChangeLog(
    serviceName: String = "Monta PHP Monolith",
    tagName: String = "2026-08-19-11-52",
    githubReleaseUrl: String? = null,
    repositoryUrl: String? = "https://github.com/monta-app/server",
    stage: String? = "production",
    deploymentStartTime: String? = "2026-08-19T11:52:00Z",
    deploymentEndTime: String? = "2026-08-19T11:55:00Z",
) = ChangeLog(
    serviceName = serviceName,
    jiraAppName = null,
    tagName = tagName,
    previousTagName = null,
    repoOwner = "monta-app",
    repoName = "server",
    repositoryUrl = repositoryUrl,
    groupedCommitMap = emptyMap(),
    stage = stage,
    deploymentStartTime = deploymentStartTime,
    deploymentEndTime = deploymentEndTime
).also { it.githubReleaseUrl = githubReleaseUrl }

private fun contributor(
    login: String? = "alice",
    email: String? = null,
) = Contributor(login = login, email = email, displayName = login ?: email ?: "someone")

class AuthorDmTest :
    StringSpec({

        "isDeploymentComplete should require both ends of the deployment timing" {
            dmChangeLog().isDeploymentComplete shouldBe true
            dmChangeLog(deploymentEndTime = null).isDeploymentComplete shouldBe false
            dmChangeLog(deploymentStartTime = null).isDeploymentComplete shouldBe false
            dmChangeLog(deploymentStartTime = null, deploymentEndTime = null).isDeploymentComplete shouldBe false
        }

        "buildAuthorDmHeadline should say the change is live once the deployment has finished" {
            val headline = buildAuthorDmHeadline(dmChangeLog())

            headline shouldContain "*Your changes are live*"
            headline shouldContain "Monta PHP Monolith release"
            headline shouldContain "is now deployed to Production."
        }

        "buildAuthorDmHeadline should say the change is on the way while the deployment is pending" {
            val headline = buildAuthorDmHeadline(dmChangeLog(deploymentStartTime = null, deploymentEndTime = null))

            headline shouldContain "*Your changes are on the way*"
            headline shouldContain "is being deployed to Production now."
            headline shouldNotContain "are live"
        }

        "buildAuthorDmHeadline should link the github release when there is one" {
            val headline = buildAuthorDmHeadline(
                dmChangeLog(githubReleaseUrl = "https://github.com/monta-app/server/releases/tag/2026-08-19-11-52")
            )

            headline shouldContain "<https://github.com/monta-app/server/releases/tag/2026-08-19-11-52|2026-08-19-11-52>"
        }

        "buildAuthorDmHeadline should construct a release url from the repository when there is no github release" {
            buildAuthorDmHeadline(dmChangeLog()) shouldContain
                "<https://github.com/monta-app/server/releases/tag/2026-08-19-11-52|2026-08-19-11-52>"
        }

        "buildAuthorDmHeadline should fall back to a plain tag name when there is no url at all" {
            val headline = buildAuthorDmHeadline(dmChangeLog(repositoryUrl = null))

            headline shouldContain "release 2026-08-19-11-52 is now deployed"
            headline shouldNotContain "<http"
        }

        "buildAuthorDmHeadline should omit the stage when it is unknown" {
            buildAuthorDmHeadline(dmChangeLog(stage = null)) shouldContain "is now deployed."
        }

        "buildAuthorDmMonitoringAsk should list every dashboard" {
            val ask = buildAuthorDmMonitoringAsk(
                listOf(serverErrorDashboard, MonitoringUrl(label = "Sentry", url = "https://sentry.example.com"))
            )

            ask shouldContain "keep an eye out for errors over the next 30 minutes:"
            ask shouldContain "• <$DASHBOARD_URL|Server Error Dashboard>"
            ask shouldContain "• <https://sentry.example.com|Sentry>"
        }

        "buildAuthorDmBlocks should include the headline, the ask and the author's own prs" {
            val blocks = buildAuthorDmBlocks(
                changeLog = dmChangeLog(),
                monitoringUrls = listOf(serverErrorDashboard),
                prLinks = "<https://github.com/monta-app/server/pull/25545|#25545>"
            )

            blocks shouldHaveSize 3
            blocks[0].text?.text shouldContain "*Your changes are live*"
            blocks[1].text?.text shouldContain "Server Error Dashboard"
            blocks[2].text?.text shouldBe
                "Your pull requests in this release: <https://github.com/monta-app/server/pull/25545|#25545>"
        }

        "buildAuthorDmBlocks should omit the pull request section when there are no links" {
            val blocks = buildAuthorDmBlocks(dmChangeLog(), listOf(serverErrorDashboard), prLinks = "")

            blocks shouldHaveSize 2
            blocks.none { it.text?.text?.contains("Your pull requests") == true } shouldBe true
        }

        "buildAuthorDmFallbackText should describe the deployed and the pending case" {
            buildAuthorDmFallbackText(dmChangeLog()) shouldBe
                "Your changes are live in Monta PHP Monolith release 2026-08-19-11-52 - please monitor for errors"
            buildAuthorDmFallbackText(dmChangeLog(deploymentEndTime = null)) shouldBe
                "Your changes are being deployed in Monta PHP Monolith release 2026-08-19-11-52 - please monitor for errors"
        }

        // --- Slack identity resolution -------------------------------------------------------

        "resolveSlackUserId should prefer the slack id the resolver already holds" {
            val slackUserId = resolveSlackUserId(
                contributor = contributor(),
                identity = IdentityPerson(email = "alice@monta.com", slackUserId = "U_FROM_RESOLVER"),
                githubProfileEmail = { error("should not read the github profile") },
                lookupByEmail = { error("should not look up an email") }
            )

            slackUserId shouldBe "U_FROM_RESOLVER"
        }

        "resolveSlackUserId should use the resolver's work email when it holds no slack id" {
            // Casper's real case: cr@monta.app on GitHub, cr@monta.com in Slack.
            val slackUserId = resolveSlackUserId(
                contributor = contributor(login = "Casperhr"),
                identity = IdentityPerson(email = "cr@monta.com"),
                githubProfileEmail = { "cr@monta.app" },
                lookupByEmail = { email -> if (email == "cr@monta.com") "U_CASPER" else null }
            )

            slackUserId shouldBe "U_CASPER"
        }

        "resolveSlackUserId should fall back to the co-author trailer email" {
            val slackUserId = resolveSlackUserId(
                contributor = contributor(login = null, email = "jane@monta.com"),
                identity = null,
                githubProfileEmail = { null },
                lookupByEmail = { email -> if (email == "jane@monta.com") "U_JANE" else null }
            )

            slackUserId shouldBe "U_JANE"
        }

        "resolveSlackUserId should fall back to the public github profile email last" {
            var profileReads = 0
            val slackUserId = resolveSlackUserId(
                contributor = contributor(email = "stale@example.com"),
                identity = null,
                githubProfileEmail = {
                    profileReads++
                    "alice@monta.com"
                },
                lookupByEmail = { email -> if (email == "alice@monta.com") "U_ALICE" else null }
            )

            slackUserId shouldBe "U_ALICE"
            profileReads shouldBe 1
        }

        "resolveSlackUserId should not read the github profile when an earlier email already matched" {
            var profileReads = 0
            resolveSlackUserId(
                contributor = contributor(email = "alice@monta.com"),
                identity = null,
                githubProfileEmail = {
                    profileReads++
                    "alice@monta.com"
                },
                lookupByEmail = { "U_ALICE" }
            ) shouldBe "U_ALICE"

            profileReads shouldBe 0
        }

        "resolveSlackUserId should try each known address only once" {
            val attempted = mutableListOf<String>()
            resolveSlackUserId(
                contributor = contributor(email = "alice@monta.com"),
                identity = IdentityPerson(email = "alice@monta.com"),
                githubProfileEmail = { null },
                lookupByEmail = { email ->
                    attempted.add(email)
                    null
                }
            ).shouldBeNull()

            attempted shouldBe listOf("alice@monta.com")
        }

        "resolveSlackUserId should be null when nothing reaches a slack account" {
            resolveSlackUserId(
                contributor = contributor(login = "ghost"),
                identity = null,
                githubProfileEmail = { null },
                lookupByEmail = { null }
            ).shouldBeNull()
        }
    })
