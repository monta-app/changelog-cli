package com.monta.changelog.git.sorter

interface TagSorter {
    fun sort(tags: List<String>): List<String>
}
