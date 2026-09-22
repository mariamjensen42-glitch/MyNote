package com.cycling.mynote.data.repository

import com.cycling.mynote.core.model.CommandItem
import com.cycling.mynote.core.model.MatchRun
import com.cycling.mynote.core.model.RecentSearch
import com.cycling.mynote.core.model.SearchHit
import com.cycling.mynote.core.model.SearchScope
import com.cycling.mynote.data.index.NoteIndexDao
import com.cycling.mynote.data.index.NoteIndexEntity
import com.cycling.mynote.data.prefs.PreferencesStore
import com.cycling.mynote.di.IoDispatcher
import com.cycling.mynote.domain.repository.SearchRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Full-text search over the index, plus the `>` command palette.
 *
 * Ranking is done here rather than in SQL because what makes a result convincing is which *field*
 * it matched, not which row it is — a title hit beats a body hit even when the body hit is more
 * recent, and expressing that in the query would mean three `INSTR` calls per row.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val dao: NoteIndexDao,
    private val preferences: PreferencesStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SearchRepository {

    override fun observeRecentSearches(): Flow<List<RecentSearch>> =
        preferences.data.map { decodeRecentSearches(it[PreferencesStore.Keys.RECENT_SEARCHES]) }

    override fun observeRecentTags(): Flow<List<String>> = preferences.data.map { preferences ->
        preferences[PreferencesStore.Keys.RECENT_TAGS]
            ?.lines()
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    override suspend fun search(query: String, scope: SearchScope): List<SearchHit> =
        withContext(io) {
            val term = query.trim().lowercase()
            if (term.isEmpty()) return@withContext emptyList()

            dao.search(term, SEARCH_LIMIT)
                .mapNotNull { entity -> toHit(entity, term) }
                .filter { scope == SearchScope.ALL || it.scope == scope }
                .sortedWith(hitComparator())
        }

    override suspend fun rememberSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(COMMAND_PREFIX)) return

        val existing = decodeRecentSearches(preferences.recentSearchesRaw())
            .filterNot { it.query == trimmed }
            .take(MAX_RECENT - 1)
        val updated = listOf(RecentSearch(trimmed, System.currentTimeMillis())) + existing
        preferences.setRecentSearchesRaw(encodeRecentSearches(updated))
    }

    override suspend fun clearRecentSearches() {
        preferences.setRecentSearchesRaw("")
    }

    override fun commands(): List<CommandItem> = COMMANDS

    /**
     * Decides which field matched and splits the visible text around the match.
     *
     * Scope priority is title, then tag, then body: a note whose *name* contains the term is almost
     * always the one the user meant.
     */
    private fun toHit(entity: NoteIndexEntity, term: String): SearchHit? {
        val titleIndex = entity.fileName.lowercase().indexOf(term)
        val tagMatch = entity.tags.firstOrNull { it.lowercase().contains(term) }
            ?: entity.aliases.firstOrNull { it.lowercase().contains(term) }
        val bodyIndex = entity.plainBody.lowercase().indexOf(term)

        val scope = when {
            titleIndex >= 0 -> SearchScope.TITLE
            tagMatch != null -> SearchScope.TAG
            bodyIndex >= 0 -> SearchScope.CONTENT
            else -> return null
        }

        return SearchHit(
            noteId = entity.noteId,
            titleRuns = splitRuns(entity.fileName, if (titleIndex >= 0) titleIndex else -1, term.length),
            snippetRuns = snippetRuns(entity.plainBody, bodyIndex, term.length),
            scope = scope,
            location = entity.folder.ifEmpty { "根目录" },
            modifiedAt = entity.modifiedAt,
            tags = entity.tags,
        )
    }

    /** Splits [text] into matched and unmatched runs around [matchIndex]. */
    private fun splitRuns(text: String, matchIndex: Int, matchLength: Int): List<MatchRun> {
        if (matchIndex < 0 || matchLength <= 0 || matchIndex + matchLength > text.length) {
            return listOf(MatchRun(text, isMatch = false))
        }
        return buildList {
            if (matchIndex > 0) add(MatchRun(text.substring(0, matchIndex), isMatch = false))
            add(MatchRun(text.substring(matchIndex, matchIndex + matchLength), isMatch = true))
            if (matchIndex + matchLength < text.length) {
                add(MatchRun(text.substring(matchIndex + matchLength), isMatch = false))
            }
        }
    }

    /**
     * Builds the snippet around the body match.
     *
     * When the term only appears in the title the first line of the body is shown instead, which is
     * what the design does for a title hit — a snippet with no highlight still tells the user what
     * the note is about.
     */
    private fun snippetRuns(plainBody: String, matchIndex: Int, matchLength: Int): List<MatchRun> {
        if (matchIndex < 0) {
            val firstLine = plainBody.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            return listOf(MatchRun(firstLine.take(SNIPPET_LENGTH), isMatch = false))
        }

        val start = (matchIndex - SNIPPET_LEAD).coerceAtLeast(0)
        val end = (matchIndex + matchLength + SNIPPET_TRAIL).coerceAtMost(plainBody.length)
        val window = plainBody.substring(start, end).replace('\n', ' ')
        val offsetInWindow = matchIndex - start

        return buildList {
            if (start > 0) add(MatchRun("…", isMatch = false))
            splitRuns(window, offsetInWindow, matchLength).forEach(::add)
            if (end < plainBody.length) add(MatchRun("…", isMatch = false))
        }
    }

    private fun hitComparator(): Comparator<SearchHit> = compareBy<SearchHit> { it.scope.ordinal }
        .thenByDescending { it.modifiedAt }

    private fun decodeRecentSearches(raw: String?): List<RecentSearch> = raw
        .orEmpty()
        .lines()
        .mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val separator = line.indexOf(RECORD_SEPARATOR)
            if (separator <= 0) return@mapNotNull null
            val timestamp = line.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
            RecentSearch(line.substring(separator + 1), timestamp)
        }

    private fun encodeRecentSearches(searches: List<RecentSearch>): String = searches
        .joinToString("\n") { "${it.searchedAt}$RECORD_SEPARATOR${it.query}" }

    private companion object {
        const val COMMAND_PREFIX = ">"
        const val RECORD_SEPARATOR = '\t'
        const val SEARCH_LIMIT = 50
        const val MAX_RECENT = 8
        const val SNIPPET_LEAD = 14
        const val SNIPPET_TRAIL = 40
        const val SNIPPET_LENGTH = 60

        /**
         * The palette's verbs. Ids are stable strings rather than enum ordinals so a reorder never
         * silently changes which command the UI dispatches.
         */
        val COMMANDS = listOf(
            CommandItem("new-note", "新建笔记", "在根目录创建一篇空白笔记", "filePlus"),
            CommandItem("new-diary", "新建日记", "打开今天的日记", "notebookPen"),
            CommandItem("new-folder", "新建文件夹", "在根目录创建文件夹", "folderPlus"),
            CommandItem("rebuild-index", "重建搜索索引", "丢弃索引并从文件夹重新构建", "refreshCw"),
            CommandItem("refresh", "刷新笔记库", "重新扫描文件夹的改动", "rotateCw"),
            CommandItem("quick-capture", "快速捕获", "把一段文字追加到 Inbox.md", "zap"),
            CommandItem("toggle-theme", "切换主题", "在浅色与深色之间切换", "moon"),
            CommandItem("open-settings", "打开设置", "仓库、主题与编辑器设置", "settings"),
        )
    }
}
