package com.monta.changelog.model

import com.monta.changelog.git.sorter.DateVerSorter
import com.monta.changelog.git.sorter.SemVerSorter
import com.monta.changelog.git.sorter.TagSorter

enum class VersionMode(
    val sorter: TagSorter,
) {
    SemVer(
        sorter = SemVerSorter()
    ),
    DateVer(
        sorter = DateVerSorter()
    ),
    ;

    companion object {
        fun fromString(value: String?): VersionMode? = values().find { versionMode ->
            versionMode.name.equals(value, true)
        }
    }
}
