package com.cycling.mynote.data.saf

import androidx.compose.runtime.Immutable

/** One child of a folder in the repository, as reported by the Storage Access Framework. */
@Immutable
data class DocumentEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
) {
    val isDirectory: Boolean
        get() = mimeType == MIME_DIRECTORY

    /** Only Markdown files are notes; everything else in the folder is left alone. */
    val isMarkdown: Boolean
        get() = !isDirectory && displayName.substringAfterLast('.', "").lowercase() in MARKDOWN_EXTENSIONS

    companion object {
        const val MIME_DIRECTORY = "vnd.android.document/directory"
        const val MIME_MARKDOWN = "text/markdown"

        val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd")
    }
}
