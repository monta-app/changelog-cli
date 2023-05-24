package com.monta.changelog.git.sorter

import com.monta.changelog.git.getTagValue

class SemVerSorter : TagSorter {

    override fun sort(tags: List<Tag>): List<Tag> {
        return tags.mapNotNull { tag ->
            val shortTag = tag.shortTag
            // Get the last part of the tag so stuff like releases/tag/2023-02-28-14-39 can still be valid
            var tagValue = shortTag.getTagValue()
            // Remove the V portion if it's there (Not required for sorting)
            tagValue = tagValue.replace("v", "")
            // We check it's a valid tag by seeing it contains three dots (there is 100% a better way to do this)
            if (shortTag.count { it == '.' } >= 2) {
                tagValue to tag
            } else {
                null
            }
        }
            // Sort by the date
            .sortedByDescending { (localDateTime, _) ->
                localDateTime
            }
            // Return only the tag
            .map { (_, tag) ->
                tag
            }
    }
}
