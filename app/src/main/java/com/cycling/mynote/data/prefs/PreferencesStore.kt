package com.cycling.mynote.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cycling.mynote.core.model.EditorSettings
import com.cycling.mynote.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.myNoteDataStore: DataStore<Preferences> by preferencesDataStore(name = "mynote")

/**
 * Typed access to the app's single preferences file.
 *
 * One file rather than one per repository: everything stored here is small, flat and written from
 * the same places, so splitting it would add files without adding isolation.
 *
 * Read errors are swallowed to an empty preference set. A corrupt preferences file costs the user
 * their theme choice; failing the flow would cost them the app.
 */
@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val data: Flow<Preferences> = context.myNoteDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    suspend fun edit(transform: suspend (MutablePreferences) -> Unit) {
        context.myNoteDataStore.edit(transform)
    }

    suspend fun snapshot(): Preferences = data.first()

    object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EDITOR_FONT = stringPreferencesKey("editor_font")
        val FONT_SIZE = intPreferencesKey("editor_font_size")
        val LINE_HEIGHT = floatPreferencesKey("editor_line_height")
        val SOFT_WRAP = booleanPreferencesKey("editor_soft_wrap")
        val ATTACHMENT_FOLDER = stringPreferencesKey("editor_attachment_folder")
        val AUTO_SAVE = booleanPreferencesKey("editor_auto_save")

        val REPO_TREE_URI = stringPreferencesKey("repo_tree_uri")
        val REPO_DISPLAY_NAME = stringPreferencesKey("repo_display_name")
        val REPO_PATH_LABEL = stringPreferencesKey("repo_path_label")
        val REPO_LAST_OPENED = stringPreferencesKey("repo_last_opened")
        val LAST_REPO_TREE_URI = stringPreferencesKey("last_repo_tree_uri")
        val LAST_REPO_DISPLAY_NAME = stringPreferencesKey("last_repo_display_name")

        val INDEX_BUILT_AT = stringPreferencesKey("index_built_at")

        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val RECENT_TAGS = stringPreferencesKey("recent_tags")

        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val CREATE_SAMPLE_NOTE = booleanPreferencesKey("create_sample_note")
    }

    /** Reader for the settings screen; defaults match [EditorSettings]. */
    fun editorSettings(): Flow<EditorSettings> = data.map { preferences ->
        EditorSettings(
            themeMode = ThemeMode.fromKey(preferences[Keys.THEME_MODE]),
            font = com.cycling.mynote.core.model.EditorFont.fromKey(preferences[Keys.EDITOR_FONT]),
            fontSizeSp = preferences[Keys.FONT_SIZE] ?: EditorSettings.DEFAULT_FONT_SIZE,
            lineHeight = preferences[Keys.LINE_HEIGHT] ?: EditorSettings.DEFAULT_LINE_HEIGHT,
            softWrap = preferences[Keys.SOFT_WRAP] ?: true,
            attachmentFolder = preferences[Keys.ATTACHMENT_FOLDER]
                ?: EditorSettings.DEFAULT_ATTACHMENT_FOLDER,
            autoSave = preferences[Keys.AUTO_SAVE] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.key }

    suspend fun setFont(key: String) = edit { it[Keys.EDITOR_FONT] = key }

    suspend fun setFontSize(sizeSp: Int) = edit { it[Keys.FONT_SIZE] = sizeSp }

    suspend fun setLineHeight(lineHeight: Float) = edit { it[Keys.LINE_HEIGHT] = lineHeight }

    suspend fun setSoftWrap(enabled: Boolean) = edit { it[Keys.SOFT_WRAP] = enabled }

    suspend fun setAttachmentFolder(folder: String) = edit { it[Keys.ATTACHMENT_FOLDER] = folder }

    suspend fun setAutoSave(enabled: Boolean) = edit { it[Keys.AUTO_SAVE] = enabled }

    suspend fun repoTreeUri(): String? = snapshot()[Keys.REPO_TREE_URI]

    /** When the repository was last opened, as a stream so the info card can re-render on change. */
    fun lastOpenedAt(): Flow<Long> = data.map {
        it[Keys.REPO_LAST_OPENED]?.toLongOrNull() ?: 0L
    }

    /** When the search index was last built, or `null` if it never has been. */
    fun indexBuiltAt(): Flow<Long?> = data.map { it[Keys.INDEX_BUILT_AT]?.toLongOrNull() }

    suspend fun repoDisplayName(): String? = snapshot()[Keys.REPO_DISPLAY_NAME]

    suspend fun setRepo(treeUri: String, displayName: String, pathLabel: String, openedAt: Long) = edit {
        it[Keys.REPO_TREE_URI] = treeUri
        it[Keys.REPO_DISPLAY_NAME] = displayName
        it[Keys.REPO_PATH_LABEL] = pathLabel
        it[Keys.REPO_LAST_OPENED] = openedAt.toString()
        it[Keys.LAST_REPO_TREE_URI] = treeUri
        it[Keys.LAST_REPO_DISPLAY_NAME] = displayName
    }

    suspend fun clearRepo() = edit { it.remove(Keys.REPO_TREE_URI) }

    suspend fun markRepoOpened(openedAt: Long) = edit { it[Keys.REPO_LAST_OPENED] = openedAt.toString() }

    suspend fun setIndexBuiltAt(builtAt: Long) = edit { it[Keys.INDEX_BUILT_AT] = builtAt.toString() }

    /**
     * Recent searches as the single encoded string they are stored as.
     *
     * The encoding is the caller's business: this only knows the value is a newline-separated list
     * of records, which keeps the record format (query plus timestamp) out of the preference layer.
     */
    suspend fun recentSearchesRaw(): String? = snapshot()[Keys.RECENT_SEARCHES]

    suspend fun setRecentSearchesRaw(value: String) = edit { it[Keys.RECENT_SEARCHES] = value }

    suspend fun recentTags(): List<String> =
        snapshot()[Keys.RECENT_TAGS]?.lines()?.filter { it.isNotBlank() }.orEmpty()

    suspend fun setRecentTags(tags: List<String>) = edit {
        it[Keys.RECENT_TAGS] = tags.joinToString("\n")
    }

    suspend fun isOnboardingSeen(): Boolean = snapshot()[Keys.ONBOARDING_SEEN] ?: false

    suspend fun setOnboardingSeen(seen: Boolean) = edit { it[Keys.ONBOARDING_SEEN] = seen }

    suspend fun shouldCreateSampleNote(): Boolean = snapshot()[Keys.CREATE_SAMPLE_NOTE] ?: true

    suspend fun setCreateSampleNote(enabled: Boolean) = edit { it[Keys.CREATE_SAMPLE_NOTE] = enabled }
}
