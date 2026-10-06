package com.monta.changelog.notify

import com.monta.changelog.util.json
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class IdentityResolverTest :
    StringSpec({

        "extractIdentities should pull the slack user id and email out of a resolve response" {
            val response = IdentityResolveResponse(
                github = mapOf(
                    "john-doe" to GithubIdentity(
                        displayName = "John Doe",
                        email = "jd@test.invalid",
                        slackUserId = "U0123456789"
                    )
                )
            )

            extractIdentities(response) shouldBe mapOf(
                "john-doe" to ResolvedIdentity(slackUserId = "U0123456789", email = "jd@test.invalid")
            )
        }

        "extractIdentities should keep an entry with only an email when there is no slack user id yet" {
            val response = IdentityResolveResponse(
                github = mapOf("john-doe" to GithubIdentity(email = "jd@test.invalid", slackUserId = null))
            )

            extractIdentities(response) shouldBe mapOf(
                "john-doe" to ResolvedIdentity(slackUserId = null, email = "jd@test.invalid")
            )
        }

        "extractIdentities should lowercase login keys" {
            val response = IdentityResolveResponse(
                github = mapOf("John-Doe" to GithubIdentity(email = "jd@test.invalid"))
            )

            extractIdentities(response) shouldBe mapOf(
                "john-doe" to ResolvedIdentity(slackUserId = null, email = "jd@test.invalid")
            )
        }

        "extractIdentities should drop unresolved (null) entries" {
            val response = IdentityResolveResponse(
                github = mapOf(
                    "john-doe" to GithubIdentity(email = "jd@test.invalid"),
                    "unknown-user" to null
                )
            )

            extractIdentities(response) shouldBe mapOf(
                "john-doe" to ResolvedIdentity(slackUserId = null, email = "jd@test.invalid")
            )
        }

        "extractIdentities should drop entries with neither a slack user id nor an email" {
            val response = IdentityResolveResponse(
                github = mapOf("john-doe" to GithubIdentity(displayName = "John Doe"))
            )

            extractIdentities(response) shouldBe emptyMap()
        }

        "extractIdentities should treat a blank slack user id as absent and keep the email" {
            val response = IdentityResolveResponse(
                github = mapOf("john-doe" to GithubIdentity(email = "jd@test.invalid", slackUserId = "  "))
            )

            extractIdentities(response) shouldBe mapOf(
                "john-doe" to ResolvedIdentity(slackUserId = null, email = "jd@test.invalid")
            )
        }

        "extractIdentities should reject a slack user id that is not a valid id" {
            val response = IdentityResolveResponse(
                github = mapOf(
                    "injected" to GithubIdentity(email = "a@test.invalid", slackUserId = "U1> <!channel|x"),
                    "lowercase" to GithubIdentity(email = "b@test.invalid", slackUserId = "u0123456789"),
                    "wrong-prefix" to GithubIdentity(email = "c@test.invalid", slackUserId = "C0123456789")
                )
            )

            extractIdentities(response) shouldBe mapOf(
                "injected" to ResolvedIdentity(slackUserId = null, email = "a@test.invalid"),
                "lowercase" to ResolvedIdentity(slackUserId = null, email = "b@test.invalid"),
                "wrong-prefix" to ResolvedIdentity(slackUserId = null, email = "c@test.invalid")
            )
        }

        "extractIdentities should accept workspace-user (W) ids and trim surrounding whitespace" {
            val response = IdentityResolveResponse(
                github = mapOf("john-doe" to GithubIdentity(slackUserId = " W0123456789 "))
            )

            extractIdentities(response) shouldBe mapOf(
                "john-doe" to ResolvedIdentity(slackUserId = "W0123456789", email = null)
            )
        }

        "extractIdentities should treat a blank email as absent" {
            val response = IdentityResolveResponse(
                github = mapOf("john-doe" to GithubIdentity(email = " ", slackUserId = "U0123456789"))
            )

            extractIdentities(response) shouldBe mapOf(
                "john-doe" to ResolvedIdentity(slackUserId = "U0123456789", email = null)
            )
        }

        "extractIdentities should drop entries whose only slack user id is invalid and that have no email" {
            val response = IdentityResolveResponse(
                github = mapOf("john-doe" to GithubIdentity(slackUserId = "not-an-id"))
            )

            extractIdentities(response) shouldBe emptyMap()
        }

        "extractIdentities should return an empty map when the response is null" {
            extractIdentities(null) shouldBe emptyMap()
        }

        "extractIdentities should return an empty map when the github field is missing" {
            extractIdentities(IdentityResolveResponse(github = null)) shouldBe emptyMap()
        }

        "IdentityResolveResponse should deserialize the documented api shape" {
            val rawJson = """
                {"github":{"john-doe":{"personId":"00000000-0000-0000-0000-000000000000","displayName":"John Doe","email":"jd@test.invalid","isBot":false,"isActive":true,"githubLogin":"john-doe","slackUserId":"U0123456789","identities":[]}}}
            """.trimIndent()

            val response = json.decodeFromString<IdentityResolveResponse>(rawJson)

            extractIdentities(response) shouldBe mapOf(
                "john-doe" to ResolvedIdentity(slackUserId = "U0123456789", email = "jd@test.invalid")
            )
        }

        "IdentityResolveResponse should deserialize unknown logins as null" {
            val rawJson = """{"github":{"some-random-user":null}}"""

            val response = json.decodeFromString<IdentityResolveResponse>(rawJson)

            extractIdentities(response) shouldBe emptyMap()
        }

        "resolveEmail should use the identity-service email without calling the fallback" {
            var fallbackCalled = false

            val email = resolveEmail(identityEmail = "alice@test.invalid") {
                fallbackCalled = true
                "alice@personal.test.invalid"
            }

            email shouldBe "alice@test.invalid"
            fallbackCalled shouldBe false
        }

        "resolveEmail should call the fallback when there is no identity-service email" {
            val email = resolveEmail(identityEmail = null) { "bob@personal.test.invalid" }

            email shouldBe "bob@personal.test.invalid"
        }

        "resolveSlackUserId should use the identity-service slack user id, calling neither fallback nor lookup" {
            var fallbackCalled = false
            var lookupCalled = false

            val slackUserId = resolveSlackUserId(
                identity = ResolvedIdentity(slackUserId = "1234567890", email = "alice@test.invalid"),
                fallbackEmail = {
                    fallbackCalled = true
                    null
                },
                lookupSlackUserId = {
                    lookupCalled = true
                    null
                }
            )

            slackUserId shouldBe "1234567890"
            fallbackCalled shouldBe false
            lookupCalled shouldBe false
        }

        "resolveSlackUserId should look up the identity-service email when there is no slack user id yet" {
            var fallbackCalled = false
            var lookedUpEmail: String? = null

            val slackUserId = resolveSlackUserId(
                identity = ResolvedIdentity(slackUserId = null, email = "alice@test.invalid"),
                fallbackEmail = {
                    fallbackCalled = true
                    null
                },
                lookupSlackUserId = { email ->
                    lookedUpEmail = email
                    "1234567890"
                }
            )

            slackUserId shouldBe "1234567890"
            lookedUpEmail shouldBe "alice@test.invalid"
            fallbackCalled shouldBe false
        }

        "resolveSlackUserId should fall back and look up that email when there is no identity at all" {
            var lookedUpEmail: String? = null

            val slackUserId = resolveSlackUserId(
                identity = null,
                fallbackEmail = { "bob@personal.test.invalid" },
                lookupSlackUserId = { email ->
                    lookedUpEmail = email
                    "1234567890"
                }
            )

            slackUserId shouldBe "1234567890"
            lookedUpEmail shouldBe "bob@personal.test.invalid"
        }

        "resolveSlackUserId should return null when neither the identity nor the fallback resolve an email" {
            val slackUserId = resolveSlackUserId(
                identity = null,
                fallbackEmail = { null },
                lookupSlackUserId = { "should not be called" }
            )

            slackUserId.shouldBeNull()
        }

        "findIdentity should match the login case-insensitively against lowercased keys" {
            val identity = ResolvedIdentity(slackUserId = "1234567890", email = "jd@test.invalid")

            findIdentity("John-Doe", mapOf("john-doe" to identity)) shouldBe identity
        }

        "findIdentity should return null when the login is unknown or missing" {
            val identities = mapOf("john-doe" to ResolvedIdentity(slackUserId = "1234567890", email = null))

            findIdentity("someone-else", identities).shouldBeNull()
            findIdentity(null, identities).shouldBeNull()
        }
    })
