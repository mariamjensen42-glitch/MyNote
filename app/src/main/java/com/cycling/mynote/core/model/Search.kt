package com.cycling.mynote.core.model

import androidx.compose.runtime.Immutable

/** Where a hit matched, which drives the small `标题 / 内容 / 标签` badge on each result. */
enum class SearchScope(val label: String) {
    ALL("全部"),
    TITLE("标题"),
    CONTENT("内容"),
    TAG("标签"),
}

/**
 * A piece of a result line, split so the matching substring can be tinted without the UI having to
 * re-run the match. [isMatch] runs are drawn in the accent colour and the bold weight.
 */
@Immutable
data class MatchRun(val text: String, val isMatch: Boolean)

/** One search result row. [titleRuns] is the file name, [snippetRuns] the surrounding body text. */
@Immutable
data class SearchHit(
    val noteId: String,
    val titleRuns: List<MatchRun>,
    val snippetRuns: List<MatchRun>,
    val scope: SearchScope,
    val location: String,
    val modifiedAt: Long,
    val tags: List<String>,
)

/** A command offered by the `>` command palette. */
@Immutable
data class CommandItem(
    val id: String,
    val label: String,
    val description: String? = null,
    val iconKey: String,
)

/** One entry of the "最近搜索" strip. */
@Immutable
data class RecentSearch(val query: String, val searchedAt: Long)
