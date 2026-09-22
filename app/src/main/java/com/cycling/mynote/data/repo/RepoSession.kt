package com.cycling.mynote.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.core.model.LastRepo
import com.cycling.mynote.data.prefs.PreferencesStore
import com.cycling.mynote.data.saf.DocumentIdLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The currently granted repository, held in memory and mirrored to preferences.
 *
 * Every repository call needs the tree URI synchronously, but reading it from DataStore on each
 * call would make an async read part of every note operation. This keeps the value hot and only
 * touches preferences when it changes.
 *
 * [grantAccess] and [release] also own the persistable URI permission, because a tree URI that is
 * remembered but not persisted fails on the next process start with a confusing provider error.
 */
@Singleton
class RepoSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesStore,
) {

    private val mutex = Mutex()
    private var loaded = false

    private val _isLoaded = MutableStateFlow(false)

    /** True once the persisted grant has been read, so the UI knows not to flash onboarding. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _treeUri = MutableStateFlow<Uri?>(null)
    val treeUri: StateFlow<Uri?> = _treeUri.asStateFlow()

    private val _displayName = MutableStateFlow<String?>(null)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _pathLabel = MutableStateFlow<String?>(null)
    val pathLabel: StateFlow<String?> = _pathLabel.asStateFlow()

    private val _lastRepo = MutableStateFlow<com.cycling.mynote.core.model.LastRepo?>(null)
    val lastRepo: StateFlow<com.cycling.mynote.core.model.LastRepo?> = _lastRepo.asStateFlow()

    /** Loads the persisted grant once per process; safe to call before every operation. */
    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            val stored = preferences.repoTreeUri()
            if (stored != null) {
                _treeUri.value = Uri.parse(stored)
                _displayName.value = preferences.snapshot()[PreferencesStore.Keys.REPO_DISPLAY_NAME]
                _pathLabel.value = preferences.snapshot()[PreferencesStore.Keys.REPO_PATH_LABEL]
            }
            val lastUri = preferences.snapshot()[PreferencesStore.Keys.LAST_REPO_TREE_URI]
            val lastName = preferences.snapshot()[PreferencesStore.Keys.LAST_REPO_DISPLAY_NAME]
            if (lastUri != null && lastName != null) {
                _lastRepo.value = com.cycling.mynote.core.model.LastRepo(lastUri, lastName)
            }
            loaded = true
            _isLoaded.value = true
            if (_treeUri.value != null && !hasPersistedPermission(_treeUri.value!!)) {
                // The grant was revoked between sessions; drop it so the UI routes to onboarding.
                preferences.clearRepo()
                _treeUri.value = null
                _displayName.value = null
                _pathLabel.value = null
            }
        }
    }

    /** The tree URI to operate on, or [DataError.RepoNotConfigured] when there is none. */
    suspend fun requireTreeUri(): Uri {
        ensureLoaded()
        return _treeUri.value ?: throw DataError.RepoNotConfigured()
    }

    /** The tree URI if one is configured, for callers that can degrade instead of fail. */
    suspend fun treeUriOrNull(): Uri? {
        ensureLoaded()
        return _treeUri.value
    }

    suspend fun observeTreeUri(): Flow<Uri?> {
        ensureLoaded()
        return treeUri
    }

    /**
     * Takes a persistable grant on [uri] and makes it current.
     *
     * Only read access is requested when the provider refuses write, because a read-only repository
     * is still useful (the app can index and search it) whereas refusing the grant outright is not.
     */
    suspend fun grantAccess(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (readOnlyFailure: SecurityException) {
                throw DataError.RepoUnavailable(readOnlyFailure)
            }
        }

        val documentId = android.provider.DocumentsContract.getTreeDocumentId(uri) ?: uri.toString()
        val name = DocumentIdLabel.displayName(documentId)
        val label = DocumentIdLabel.describe(documentId)
        val openedAt = System.currentTimeMillis()

        preferences.setRepo(uri.toString(), name, label, openedAt)
        mutex.withLock {
            loaded = true
            _isLoaded.value = true
            _treeUri.value = uri
            _displayName.value = name
            _pathLabel.value = label
            _lastRepo.value = com.cycling.mynote.core.model.LastRepo(uri.toString(), name)
        }
    }

    /** Releases the current grant but keeps it as the "last repository" affordance. */
    suspend fun release() {
        val uri = _treeUri.value
        if (uri != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        preferences.clearRepo()
        mutex.withLock {
            _treeUri.value = null
            _displayName.value = null
            _pathLabel.value = null
        }
    }

    suspend fun markOpened() = preferences.markRepoOpened(System.currentTimeMillis())

    private fun hasPersistedPermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }

    /** Reads the persisted grant directly, for the one caller that runs before [ensureLoaded]. */
    suspend fun storedTreeUri(): String? = preferences.data.first().let {
        it[PreferencesStore.Keys.REPO_TREE_URI]
    }
}
