package com.monta.changelog.util

sealed interface LinkResolver {
    fun resolve(markdownFormatter: MarkdownFormatter, message: String): String

    class Github(
        private val repoOwner: String,
        private val repoName: String,
    ) : LinkResolver {

        private val regex = Regex("#\\d+")

        override fun resolve(markdownFormatter: MarkdownFormatter, message: String): String {
            regex.find(message)?.value?.let { pullRequestValue ->
                return message.replace(
                    oldValue = pullRequestValue,
                    newValue = markdownFormatter.hyperlink(
                        text = pullRequestValue,
                        link = getUrl(
                            ownerName = repoOwner,
                            serviceName = repoName,
                            pullRequestNumber = pullRequestValue.replace("#", "")
                        )
                    )
                )
            }
            return message
        }

        private fun getUrl(ownerName: String, serviceName: String, pullRequestNumber: String): String = "https://github.com/$ownerName/$serviceName/pull/$pullRequestNumber"
    }

    class Jira(
        private val jiraAppName: String?,
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
            .associateWith { jiraTicketName ->
                "https://$jiraAppName.atlassian.net/browse/$jiraTicketName"
            }
    }
}
