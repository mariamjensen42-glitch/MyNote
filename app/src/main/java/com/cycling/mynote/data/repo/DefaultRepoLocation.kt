package com.cycling.mynote.data.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import com.cycling.mynote.data.saf.DocumentEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where the app's default repository lives, and what the picker should open on. */
@Immutable
data class DefaultRepoLocation(
    val treeUri: String,
    val displayName: String,
    val pathLabel: String,
) {
    val directoryName: String get() = displayName
}

/**
 * The default repository: `Documents/Notes` in shared storage.
 *
 * **Why this needs a hand-off through the system picker.** Android has no API for an app to obtain
 * durable read/write access to a folder in shared storage without the user confirming it — that is
 * the entire point of scoped storage.
 *
 * **Why the folder is seeded first.** `Documents/Notes` is created (with a starter `Inbox.md`)
 * through [MediaStore], which needs no permission and is what makes the folder exist before the
 * picker opens — so "use the default location" is a real, already-populated folder the user can see
 * in their file manager, also sync, and open in any editor. The seeding only ever runs from the
 * onboarding button, so nothing is written to the user's storage without their tap.
 */
@Singleton
class DefaultRepoLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Creates `Documents/Notes/Inbox.md` if it is not already there.
     *
     * MediaStore cannot create an empty directory, so the starter note is what brings its parent
     * into existence. That is also why this is not conditional on the "create a sample note"
     * setting: the folder has to contain something to exist at all, and this is the smallest thing
     * that can be there.
     *
     * @return true when the file is present afterwards, whether this call created it or found it.
     */
    fun ensureSeeded(): Boolean {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "$DOCUMENTS_DIRECTORY/$DIRECTORY_NAME"

        if (exists(collection, relativePath)) return true

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, STARTER_FILE)
            put(MediaStore.MediaColumns.MIME_TYPE, DocumentEntry.MIME_MARKDOWN)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        return runCatching { context.contentResolver.insert(collection, values) }.getOrNull() != null
    }

    /**
     * The URI to open the picker on.
     *
     * A **document** URI, not a tree URI: `EXTRA_INITIAL_URI` is resolved by listing the folder's
     * parent, and DocumentsUI navigates to a document URI directly while a tree URI is only
     * meaningful as a *result*. Passing the tree form here lands the user on the volume root.
     *
     * Built by hand rather than discovered because `DocumentsContract` needs the provider's own
     * authority and external-storage document id, and for the primary volume both are stable.
     */
    fun initialDocumentUri(): Uri = DocumentsContract.buildDocumentUri(
        EXTERNAL_STORAGE_AUTHORITY,
        "$PRIMARY_VOLUME:$DOCUMENTS_DIRECTORY/$DIRECTORY_NAME",
    )

    fun location(): DefaultRepoLocation = DefaultRepoLocation(
        treeUri = initialDocumentUri().toString(),
        displayName = DIRECTORY_NAME,
        pathLabel = "内部存储 / $DOCUMENTS_DIRECTORY / $DIRECTORY_NAME",
    )

    private fun exists(collection: Uri, relativePath: String): Boolean = runCatching {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("$relativePath/", STARTER_FILE),
            null,
        )?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    private companion object {
        const val DIRECTORY_NAME = "Notes"
        const val STARTER_FILE = "Inbox.md"
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val PRIMARY_VOLUME = "primary"

        /** `Documents` as a literal: `Environment.DIRECTORY_DOCUMENTS` is not a compile-time const. */
        val DOCUMENTS_DIRECTORY: String = Environment.DIRECTORY_DOCUMENTS
    }
}
