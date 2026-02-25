package com.monta.changelog.git.sorter

interface TagSorter {
    fun sort(tags: List<Tag>): List<Tag>
    fun generateInitialTag(): String
}

data class Tag(
    /**
     * Short tag is what will be sorted by - this might be just the date or version part of a tag
     */
    val shortTag: String,
    val fullTag: String = shortTag,
)
