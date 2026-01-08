package com.monta.changelog.model

enum class ConventionalCommitType(
    val title: String,
    val emoji: String,
    val sortOrder: Int? = null,
    private val values: List<String>,
) {
    Feature(
        title = "Feature",
        emoji = "\uD83D\uDE80",
        sortOrder = 0,
        values = listOf("feature", "feat")
    ),
    Refactor(
        title = "Refactor",
        emoji = "\uD83C\uDFD7️",
        sortOrder = 1,
        values = listOf("refactor")
    ),
    Style(
        title = "Style",
        emoji = "\uD83C\uDFA8",
        sortOrder = 2,
        values = listOf("style")
    ),
    Fix(
        title = "Fix",
        emoji = "\uD83D\uDC1B",
        sortOrder = 3,
        values = listOf("fix")
    ),
    Test(
        title = "Test",
        emoji = "\uD83E\uDDEA",
        sortOrder = 4,
        values = listOf("test")
    ),
    Docs(
        title = "Docs",
        emoji = "\uD83D\uDCDD",
        sortOrder = 5,
        values = listOf("docs")
    ),
    Performance(
        title = "Performance",
        emoji = "\uD83C\uDFCE️",
        sortOrder = 6,
        values = listOf("perf")
    ),
    Chore(
        title = "Chore",
        emoji = "\uD83E\uDDF9",
        sortOrder = 7,
        values = listOf("chore")
    ),
    CI(
        title = "CI",
        emoji = "\uD83D\uDD27",
        sortOrder = 8,
        values = listOf("ci")
    ),
    ;

    companion object {
        fun fromString(value: String): ConventionalCommitType? = values().firstOrNull { it.values.contains(value) }
    }
}
