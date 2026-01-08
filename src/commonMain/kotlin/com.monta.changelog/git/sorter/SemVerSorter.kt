package com.monta.changelog.git.sorter

import com.monta.changelog.git.getTagValue
import com.monta.changelog.util.DebugLogger
import io.github.z4kn4fein.semver.Version

class SemVerSorter : TagSorter {

    override fun sort(tags: List<Tag>): List<Tag> = tags.mapNotNull { tag ->
        val shortTag = tag.shortTag
        // Get the last part of the tag so stuff like releases/tag/v1.2.3 can still be valid
        var tagValue = shortTag.getTagValue()
        // Remove the V portion if it's there (Not required for sorting)
        tagValue = tagValue.replace("v", "")
        try {
            Version.parse(tagValue) to tag
        } catch (e: Exception) {
            DebugLogger.warn("Failed to parse tag '$tagValue' - ignored")
            null
        }
    }
        // Sort by the date
        .sortedByDescending { (shortTag, _) ->
            shortTag
        }
        // Return only the tag
        .map { (_, tag) ->
            tag
        }
}
