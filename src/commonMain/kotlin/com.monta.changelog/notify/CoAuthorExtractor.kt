package com.monta.changelog.notify

/**
 * Extracts `Co-authored-by: Name <email>` trailers from commit messages, as added by
 * GitHub (and `git commit --amend --author`/`-m` conventions) when a commit has multiple authors.
 */
internal object CoAuthorExtractor {

    private val coAuthorRegex = Regex("(?im)^co-authored-by:\\s*(.+?)\\s*<([^>]+)>\\s*$")

    // GitHub's noreply commit email, e.g. "12345678+username@users.noreply.github.com"
    private val githubNoreplyRegex = Regex("^(?:\\d+\\+)?([A-Za-z0-9-]+)@users\\.noreply\\.github\\.com$", RegexOption.IGNORE_CASE)

    data class CoAuthor(
        val name: String,
        val email: String,
        val login: String?,
    )

    fun extract(commitMessages: List<String>): List<CoAuthor> = commitMessages
        .flatMap { message ->
            coAuthorRegex.findAll(message).map { match ->
                match.groupValues[1].trim() to match.groupValues[2].trim()
            }
        }
        .distinct()
        .map { (name, email) ->
            val login = githubNoreplyRegex.find(email)?.groupValues?.get(1)
            CoAuthor(name = name, email = email, login = login)
        }
}
