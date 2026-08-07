package com.monta.changelog.notify

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class MonitoringUrlTest :
    StringSpec({

        "parse should return null for blank input" {
            MonitoringUrl.parse("").shouldBeNull()
            MonitoringUrl.parse("   ").shouldBeNull()
        }

        "parse should use the url as the label for a bare url" {
            MonitoringUrl.parse("https://grafana.example.com/d/abc") shouldBe MonitoringUrl(
                label = "https://grafana.example.com/d/abc",
                url = "https://grafana.example.com/d/abc"
            )
        }

        "parse should split label and url on the pipe character" {
            MonitoringUrl.parse("Grafana|https://grafana.example.com/d/abc") shouldBe MonitoringUrl(
                label = "Grafana",
                url = "https://grafana.example.com/d/abc"
            )
        }

        "parse should trim whitespace around label and url" {
            MonitoringUrl.parse("  Grafana  |  https://grafana.example.com/d/abc  ") shouldBe MonitoringUrl(
                label = "Grafana",
                url = "https://grafana.example.com/d/abc"
            )
        }

        "parse should treat a url containing a trailing pipe with a blank side as a bare url" {
            MonitoringUrl.parse("https://grafana.example.com/d/abc|") shouldBe MonitoringUrl(
                label = "https://grafana.example.com/d/abc|",
                url = "https://grafana.example.com/d/abc|"
            )
        }

        "parseAll should return an empty list for null input" {
            MonitoringUrl.parseAll(null) shouldBe emptyList()
        }

        "parseAll should parse every non-blank entry" {
            MonitoringUrl.parseAll(
                listOf(
                    "Grafana|https://grafana.example.com",
                    "",
                    "https://sentry.example.com"
                )
            ) shouldBe listOf(
                MonitoringUrl(label = "Grafana", url = "https://grafana.example.com"),
                MonitoringUrl(label = "https://sentry.example.com", url = "https://sentry.example.com")
            )
        }
    })
