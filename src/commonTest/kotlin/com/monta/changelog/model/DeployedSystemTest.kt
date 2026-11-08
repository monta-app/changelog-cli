package com.monta.changelog.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class DeployedSystemTest :
    StringSpec({

        "parses the deploy pipeline JSON and ignores unknown fields (e.g. app)" {
            val raw = """
                [{"name":"hub","app":"frontend-hub-production","revision":"80aad1c",
                  "start":"2026-08-26T08:19:18Z","end":"2026-08-26T08:30:59Z",
                  "status":"healthy","url":"https://argocd.monta.app/x"}]
            """.trimIndent()
            DeployedSystem.parseAll(raw) shouldBe
                listOf(
                    DeployedSystem(
                        name = "hub",
                        revision = "80aad1c",
                        start = "2026-08-26T08:19:18Z",
                        end = "2026-08-26T08:30:59Z",
                        status = "healthy",
                        url = "https://argocd.monta.app/x"
                    )
                )
        }

        "parses entries with only a name (optional fields absent)" {
            DeployedSystem.parseAll("""[{"name":"hub"},{"name":"portals"}]""") shouldBe
                listOf(DeployedSystem(name = "hub"), DeployedSystem(name = "portals"))
        }

        "returns empty for null, blank, or malformed input rather than failing" {
            DeployedSystem.parseAll(null).shouldBeEmpty()
            DeployedSystem.parseAll("").shouldBeEmpty()
            DeployedSystem.parseAll("   ").shouldBeEmpty()
            DeployedSystem.parseAll("not json").shouldBeEmpty()
            // missing the required name
            DeployedSystem.parseAll("""[{"revision":"x"}]""").shouldBeEmpty()
        }

        "treats healthy, success and a missing status as healthy, in any case" {
            listOf(
                DeployedSystem(name = "a", status = "healthy"),
                DeployedSystem(name = "b", status = "SUCCESS"),
                DeployedSystem(name = "c")
            ).outcome() shouldBe DeployOutcome.HEALTHY
            DeployedSystem(name = "d", status = "success").isNotHealthy() shouldBe false
        }

        "reports failed when no system is healthy and partial when some are" {
            listOf(DeployedSystem(name = "a", status = "degraded")).outcome() shouldBe DeployOutcome.FAILED
            listOf(
                DeployedSystem(name = "a", status = "success"),
                DeployedSystem(name = "b", status = "degraded")
            ).outcome() shouldBe DeployOutcome.PARTIAL
        }
    })
