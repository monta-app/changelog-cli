package com.monta.changelog.git

import com.monta.changelog.git.sorter.DateVerSorter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GitServiceTest :
    StringSpec({

        fun log(sha: String) = LogItem(
            author = Author(date = "2026-08-25", email = "a@b.c", name = "A"),
            committer = Author(date = "2026-08-25", email = "a@b.c", name = "A"),
            commit = sha,
            subject = "feat: $sha #1",
            body = "feat: $sha #1",
            parents = "p0"
        )

        fun service(fake: GitCommandUtil, pathExcludePattern: String?) = GitService(
            tagSorter = DateVerSorter(),
            tagPattern = null,
            pathExcludePattern = pathExcludePattern,
            gitCommandUtil = fake
        )

        // allCommitShas feeds PR discovery. A commit whose files are all excluded
        // must not reach it, or its PR gets commented on for a change that isn't
        // in this changelog.
        "allCommitShas excludes commits whose files all match the path-exclude pattern" {
            val fake = object : GitCommandUtil() {
                override fun getLogs(latestTag: String, previousTag: String) = listOf(log("aaa"), log("bbb"))
                override fun getFilesInCommit(commitId: String) = when (commitId) {
                    "aaa" -> listOf("apps/hub/x.ts")
                    else -> listOf("apps/other/y.ts")
                }
            }
            val info = service(fake, pathExcludePattern = "^apps/other/").getCommitsBetweenTags(fromTag = "from", toTag = "to")
            info.allCommitShas shouldBe listOf("aaa")
        }

        "allCommitShas keeps every commit when no path-exclude pattern is set" {
            val fake = object : GitCommandUtil() {
                override fun getLogs(latestTag: String, previousTag: String) = listOf(log("aaa"), log("bbb"))
            }
            val info = service(fake, pathExcludePattern = null).getCommitsBetweenTags(fromTag = "from", toTag = "to")
            info.allCommitShas shouldBe listOf("aaa", "bbb")
        }
    })
