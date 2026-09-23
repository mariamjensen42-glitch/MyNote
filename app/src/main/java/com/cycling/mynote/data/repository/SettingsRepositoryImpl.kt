package com.cycling.mynote.data.repository

import com.cycling.mynote.core.model.EditorFont
import com.cycling.mynote.core.model.EditorSettings
import com.cycling.mynote.core.model.ThemeMode
import com.cycling.mynote.data.prefs.PreferencesStore
import com.cycling.mynote.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Editor and theme preferences.
 *
 * Values are clamped on the way in as well as in the UI: a preference file written by an older
 * build (or edited by hand) must not be able to put the editor into a state the settings screen
 * cannot express.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val preferences: PreferencesStore,
) : SettingsRepository {

    override fun observeSettings(): Flow<EditorSettings> = preferences.editorSettings()

    override suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)

    override suspend fun setFont(font: EditorFont) = preferences.setFont(font.key)

    override suspend fun setFontSize(sizeSp: Int) = preferences.setFontSize(
        sizeSp.coerceIn(EditorSettings.MIN_FONT_SIZE, EditorSettings.MAX_FONT_SIZE),
    )

    override suspend fun setLineHeight(lineHeight: Float) = preferences.setLineHeight(
        lineHeight.coerceIn(EditorSettings.MIN_LINE_HEIGHT, EditorSettings.MAX_LINE_HEIGHT),
    )

    override suspend fun setSoftWrap(enabled: Boolean) = preferences.setSoftWrap(enabled)

    override suspend fun setAttachmentFolder(folder: String) =
        preferences.setAttachmentFolder(folder.trim().trim('/'))

    override suspend fun setAutoSave(enabled: Boolean) = preferences.setAutoSave(enabled)

    override suspend fun setCreateSampleNote(enabled: Boolean) =
        preferences.setCreateSampleNote(enabled)

    override suspend fun shouldCreateSampleNote(): Boolean = preferences.shouldCreateSampleNote()
}
