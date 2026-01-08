package com.monta.changelog

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.options.versionOption
import com.monta.changelog.log.ChangeLogService
import com.monta.changelog.model.VersionMode
import com.monta.changelog.printer.ChangeLogPrinter
import com.monta.changelog.printer.ConsoleChangeLogPrinter
import com.monta.changelog.printer.slack.SlackChangeLogPrinter
import com.monta.changelog.util.DebugLogger
import kotlinx.coroutines.runBlocking

class GenerateChangeLogCommand : CliktCommand() {
    init {
        versionOption(Version.format())
    }

    private val banner = """
 __    __   ______   __   __   ______  ______
/\ "-./  \ /\  __ \ /\ "-.\ \ /\__  _\/\  __ \
\ \ \-./\ \\ \ \/\ \\ \ \-.  \\/_/\ \/\ \  __ \
 \ \_\ \ \_\\ \_____\\ \_\\"\_\  \ \_\ \ \_\ \_\
  \/_/  \/_/ \/_____/ \/_/ \/_/   \/_/  \/_/\/_/
    """.trimIndent()

    private val debug: Boolean by option(
        help = "Enables debug logging"
    ).flag(
        secondaryNames = arrayOf("--debug", "-D"),
        default = false
    )

    private val serviceName: String by option(
        help = "Name of the service used for generating the changelog",
        envvar = "CHANGELOG_SERVICE_NAME"
    ).required()

    private val githubRelease: Boolean by option(
        help = "If enabled this will create a release on github with a newly generated changelog",
        envvar = "CHANGELOG_GITHUB_RELEASE"
    ).flag("--github-release", "-R", default = false)

    private val githubToken: String? by option(
        help = "Github Token used for creating releases",
        envvar = "CHANGELOG_GITHUB_TOKEN"
    )

    private val jiraAppName: String? by option(
        help = "Name of the Jira app used for generating Jira issue urls (optional)",
        envvar = "CHANGELOG_JIRA_APP_NAME"
    )

    private val versionMode: String? by option(
        help = "Which version format is used in the tags (options: SemVer,DateVer) defaults to DateVer",
        envvar = "CHANGELOG_VERSION_MODE"
    )

    private val tagPattern: String? by option(
        help = "Regex pattern used for matching tag patterns (group 1 in the pattern should match the 'version')",
        envvar = "CHANGELOG_GITHUB_TAG_PATTERN"
    )

    private val pathExcludePattern: String? by option(
        help = "Regex pattern used for matching file patch for which commits should not be included. I.e. if a commit only contains files that match this, it will not be in the change log",
        envvar = "CHANGELOG_GITHUB_PATH_EXCLUDE_PATTERN"
    )

    private val output: PrintingConfig by option(
        help = "Name of the output used for printing the log (defaults to console)",
        envvar = "CHANGELOG_OUTPUT"
    ).groupChoice(
        choices = mapOf(
            "console" to ConsolePrintingConfig(),
            "slack" to SlackPrintingConfig()
        )
    ).required()

    private val commitShaOptions by CommitShaOptions().cooccurring()

    override fun run() {
        runBlocking {
            DebugLogger.info("\n" + banner)
            DebugLogger.info(Version.format())

            val versionMode = VersionMode.fromString(versionMode) ?: VersionMode.DateVer

            val changeLogService = ChangeLogService(
                debug = debug,
                serviceName = serviceName,
                jiraAppName = jiraAppName.valueOrNull(),
                tagSorter = versionMode.sorter,
                githubRelease = githubRelease,
                githubToken = githubToken.valueOrNull(),
                tagPattern = tagPattern.valueOrNull(),
                pathExcludePattern = pathExcludePattern.valueOrNull()
            )

            val commitShaOptions = commitShaOptions

            if (commitShaOptions != null) {
                changeLogService.generate(
                    startSha = commitShaOptions.startSha,
                    endSha = commitShaOptions.endSha,
                    changeLogPrinter = output.changeLogPrinter
                )
            } else {
                changeLogService.generate(
                    changeLogPrinter = output.changeLogPrinter
                )
            }
        }
    }

    /**
     * Needed for optional parameters as the return the empty string instead of null
     * if set via ENV variables (as we do from our GitHub Actions)
     */
    private fun String?.valueOrNull() = if (this.isNullOrBlank()) null else this

    sealed class PrintingConfig(name: String) : OptionGroup(name) {
        abstract val changeLogPrinter: ChangeLogPrinter
    }

    class ConsolePrintingConfig : PrintingConfig("Options for printing to console") {
        override val changeLogPrinter: ChangeLogPrinter by lazy {
            ConsoleChangeLogPrinter()
        }
    }

    class CommitShaOptions : OptionGroup() {
        val startSha: String by option(
            help = "The github commit sha to start the change log from"
        ).required()
        val endSha: String by option(
            help = "The github commit sha to end the change log on"
        ).required()
    }

    class SlackPrintingConfig : PrintingConfig("Options for printing to slack") {
        private val slackToken: String by option(
            help = "Slack token used for publishing",
            envvar = "CHANGELOG_SLACK_TOKEN"
        ).required()

        private val slackChannel: String? by option(
            help = "Slack channel ID (e.g., C02PDBL6GAU) or channel name (e.g., #my-channel) where the changelog will be published to. Channel IDs are recommended.",
            envvar = "CHANGELOG_SLACK_CHANNEL_NAME"
        )

        private val slackChannels: List<String>? by option(
            help = "Comma-separated list of Slack channel IDs (e.g., C02PDBL6GAU,C03ABCDEFGH) or channel names. Channel IDs are recommended.",
            envvar = "CHANGELOG_SLACK_CHANNELS"
        ).split(",")

        override val changeLogPrinter: ChangeLogPrinter by lazy {
            SlackChangeLogPrinter(
                slackToken = slackToken,
                slackChannels = buildSet {
                    slackChannel?.let {
                        if (it.isNotBlank()) {
                            add(it)
                        }
                    }
                    slackChannels?.let { list ->
                        val trimmed = list.filter {
                            it.isNotBlank()
                        }
                        addAll(trimmed)
                    }
                }
            )
        }
    }
}
