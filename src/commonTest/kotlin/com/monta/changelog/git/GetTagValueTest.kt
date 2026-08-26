package com.monta.changelog.git

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GetTagValueTest :
    StringSpec({

        // getTagValue splits on '/' and takes the last segment. Dash-style
        // prefixes (hub-<date>) pass through intact, which is what lets prefixed
        // per-app tags keep working; slash-style prefixes would be stripped.
        listOf(
            "2026-08-25-13-18" to "2026-08-25-13-18",
            "hub-2026-08-25-13-18" to "hub-2026-08-25-13-18",
            "v1.2.3" to "v1.2.3",
            "myapp/2026-01-01-00-00" to "2026-01-01-00-00",
            "releases/tag/v1.2.3" to "v1.2.3"
        ).forEach { (input, expected) ->
            "getTagValue('$input') == '$expected'" {
                input.getTagValue() shouldBe expected
            }
        }
    })
