package com.monta.changelog.printer.slack

internal fun MutableList<SlackBlock>.header(text: () -> String) {
    block {
        SlackBlock(
            type = "section",
            text = SlackText(
                type = "mrkdwn",
                text = "*${text()}*"
            )
        )
    }
}

internal fun MutableList<SlackBlock>.text(text: () -> String) {
    block {
        SlackBlock(
            type = "section",
            text = SlackText(
                type = "mrkdwn",
                text = text()
            )
        )
    }
}

internal fun MutableList<SlackBlock>.button(url: () -> String) {
    block {
        SlackBlock(
            type = "actions",
            elements = listOf(
                SlackBlock(
                    text = SlackText(
                        type = "plain_text",
                        text = "GitHub Release"
                    ),
                    url = url(),
                    type = "button"
                )
            )
        )
    }
}

internal fun MutableList<SlackBlock>.divider() {
    block {
        SlackBlock(
            type = "divider"
        )
    }
}

internal fun MutableList<SlackBlock>.block(block: () -> SlackBlock) {
    add(
        block()
    )
}
