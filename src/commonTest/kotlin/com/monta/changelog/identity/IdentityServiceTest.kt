package com.monta.changelog.identity

import com.monta.changelog.util.json
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * A real response from project-tracker's resolver, trimmed to the fields we read. Note that
 * `slackUserId` is null for everyone today - the Slack identities aren't synced yet - which is
 * exactly why the work email matters as a second step.
 */
private const val REAL_RESPONSE = """
{
  "github": {
    "Casperhr": {
      "personId": "f336fb11-ccd9-4a5f-984e-9ca78e37012e",
      "displayName": "Casper Rasmussen",
      "email": "cr@monta.com",
      "isBot": false,
      "isActive": true,
      "githubLogin": "Casperhr",
      "slackUserId": null,
      "identities": [{ "platform": "github", "id": "casperhr", "username": "Casperhr" }]
    },
    "ghost": null
  }
}
"""

class IdentityServiceTest :
    StringSpec({

        "should deserialize a real resolver response, including unknown fields" {
            val response = json.decodeFromString<IdentityResolveResponse>(REAL_RESPONSE)

            response.github.size shouldBe 2

            val casper = response.github["Casperhr"]
            casper?.displayName shouldBe "Casper Rasmussen"
            // The address Slack knows - note GitHub's public profile says cr@monta.app instead.
            casper?.email shouldBe "cr@monta.com"
            casper?.githubLogin shouldBe "Casperhr"
            casper?.slackUserId.shouldBeNull()
            casper?.isBot shouldBe false
        }

        "should represent an unknown handle as a null person" {
            val response = json.decodeFromString<IdentityResolveResponse>(REAL_RESPONSE)

            response.github.containsKey("ghost") shouldBe true
            response.github["ghost"].shouldBeNull()
        }

        "should tolerate a response with no github map at all" {
            json.decodeFromString<IdentityResolveResponse>("""{"slack":{"U1":null}}""").github shouldBe emptyMap()
        }
    })
