package com.monta.changelog.util

sealed interface LinkResolver {
    fun resolve(markdownFormatter: MarkdownFormatter, message: String): String

    class Github(
        private val repoOwner: String,
        private val repoName: String,
        private val validPullRequests: Set<String>? = null,
    ) : LinkResolver {

        private val regex = Regex("#\\d+")

        override fun resolve(markdownFormatter: MarkdownFormatter, message: String): String {
            val pullRequests = getPullRequests(message)

            var newMessage = message

            pullRequests.forEach { (key, value) ->
                newMessage = newMessage.replace(
                    oldValue = key,
                    newValue = markdownFormatter.hyperlink(
                        text = key,
                        link = value
                    )
                )
            }

            return newMessage
        }

        private fun getPullRequests(commitMessage: String): Map<String, String> = regex.findAll(commitMessage)
            .mapNotNull { matchResult ->
                matchResult.value
            }
            .filter { prRef ->
                // If validPullRequests is provided, only include PRs in that set
                // Otherwise, include all PRs (for backward compatibility)
                val prNumber = prRef.replace("#", "")
                validPullRequests == null || prNumber in validPullRequests
            }
            .associateWith { prRef ->
                getUrl(
                    ownerName = repoOwner,
                    serviceName = repoName,
                    pullRequestNumber = prRef.replace("#", "")
                )
            }

        private fun getUrl(ownerName: String, serviceName: String, pullRequestNumber: String): String = "https://github.com/$ownerName/$serviceName/pull/$pullRequestNumber"
    }

    class Jira(
        private val jiraAppName: String?,
        private val validTickets: Set<String>? = null,
    ) : LinkResolver {

        private val jiraRegex = Regex("[A-Z]{2,}-\\d+")

        override fun resolve(markdownFormatter: MarkdownFormatter, message: String): String {
            if (jiraAppName == null) {
                return message
            }

            val jiraTags = getJiraTags(message)

            var newCommitMessage = message

            jiraTags.forEach { (key, value) ->
                newCommitMessage = newCommitMessage.replace(
                    oldValue = key,
                    newValue = markdownFormatter.hyperlink(
                        text = key,
                        link = value
                    )
                )
            }

            return newCommitMessage
        }

        private fun getJiraTags(commitMessage: String): Map<String, String> = jiraRegex.findAll(commitMessage)
            .mapNotNull { matchResult ->
                matchResult.groupValues.firstOrNull()
            }
            .filter { ticketName ->
                // If validTickets is provided, only include tickets in that set
                // Otherwise, include all tickets (for backward compatibility)
                validTickets == null || ticketName in validTickets
            }
            .associateWith { jiraTicketName ->
                "https://$jiraAppName.atlassian.net/browse/$jiraTicketName"
            }
    }
}
