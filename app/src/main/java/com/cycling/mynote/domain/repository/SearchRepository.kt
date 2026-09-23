package com.cycling.mynote.domain.repository

import com.cycling.mynote.core.model.CommandItem
import com.cycling.mynote.core.model.EditorSettings
import com.cycling.mynote.core.model.RecentSearch
import com.cycling.mynote.core.model.SearchHit
import com.cycling.mynote.core.model.SearchScope
import kotlinx.coroutines.flow.Flow

/**
 * Full-text search over the indexed repository, plus the `>` command palette that shares the same
 * input field.
 */
interface SearchRepository {

    fun observeRecentSearches(): Flow<List<RecentSearch>>

    fun observeRecentTags(): Flow<List<String>>

    /**
     * Ranks matches so the most convincing ones come first: title hits before tag hits before body
     * hits, then by how early in the field the term appears, then by recency.
     */
    suspend fun search(query: String, scope: SearchScope = SearchScope.ALL): List<SearchHit>

    suspend fun rememberSearch(query: String)

    suspend fun clearRecentSearches()

    /** Commands offered when the query starts with `>`. */
    fun commands(): List<CommandItem>
}

/** Editor and theme preferences, exposed as a stream so changes apply without a restart. */
interface SettingsRepository {
    fun observeSettings(): Flow<EditorSettings>

    suspend fun setThemeMode(mode: com.cycling.mynote.core.model.ThemeMode)

    suspend fun setFont(font: com.cycling.mynote.core.model.EditorFont)

    suspend fun setFontSize(sizeSp: Int)

    suspend fun setLineHeight(lineHeight: Float)

    suspend fun setSoftWrap(enabled: Boolean)

    /** Where pictures picked in the editor are filed, as a path inside the repository. */
    suspend fun setAttachmentFolder(folder: String)

    suspend fun setAutoSave(enabled: Boolean)

    suspend fun setCreateSampleNote(enabled: Boolean)

    suspend fun shouldCreateSampleNote(): Boolean
}
