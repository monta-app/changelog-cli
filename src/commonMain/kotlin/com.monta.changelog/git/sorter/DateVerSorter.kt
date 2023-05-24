package com.monta.changelog.git.sorter

import com.monta.changelog.git.getTagValue
import kotlinx.datetime.LocalDateTime

class DateVerSorter : TagSorter {

    override fun sort(tags: List<Tag>): List<Tag> {
        return tags.mapNotNull { tag ->
            val shortTag = tag.shortTag
            // Try to convert to a date
            val localDateTime = shortTag.getTagValue().toDate()
            // Skip if we don't have a valid date
            if (localDateTime == null) {
                null
            } else {
                // Return the date with the tag as a pair if we do have something value
                localDateTime to tag
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

    /**
     *  Kotlin native has no easy way of parsing dates :)
     *  yyyy-MM-dd-HH-mm
     */
    private fun String.toDate(): LocalDateTime? {
        return try {
            val values = split("-").map { it.toInt() }
            LocalDateTime(
                values[0],
                values[1],
                values[2],
                values[3],
                values[4]
            )
        } catch (exception: Exception) {
            null
        }
    }
}
