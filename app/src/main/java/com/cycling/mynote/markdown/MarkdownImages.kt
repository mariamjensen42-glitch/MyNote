package com.cycling.mynote.markdown

/**
 * Where an image reference in a note points.
 *
 * Resolving this is plain string work with no I/O, so the preview, the loader and the tests all
 * agree on what `![](…)` means before anything is read or fetched.
 */
sealed interface ImageReference {
    /** An `http(s)` address, fetched over the network. */
    data class Remote(val url: String) : ImageReference

    /**
     * A file inside the repository, as a repo-relative path.
     *
     * The app can only read the folder the user granted, so a path that escapes it — or one that
     * points somewhere else on the device — ends up here too and simply fails to resolve.
     */
    data class RepoFile(val path: String) : ImageReference

    /** Something the app cannot read: another app's `content://` URI, a `file://` path, etc. */
    data class External(val reference: String) : ImageReference
}

/**
 * Interprets the target of a Markdown image.
 *
 * Markdown readers resolve a relative path against the *note*, not against the repository root, so a
 * note in `日记/` writing `![](img/a.png)` means `日记/img/a.png`. A leading slash means the
 * repository root, which is how a note refers to a shared folder without counting `../`.
 */
object MarkdownImages {

    private val SCHEME = Regex("""^([A-Za-z][A-Za-z0-9+.-]*):""")

    /** `%20` is how a space is written in a Markdown path when the author quotes it as a URL. */
    private const val ENCODED_SPACE = "%20"

    fun resolve(reference: String, noteFolder: String): ImageReference {
        val trimmed = reference.trim().replace(ENCODED_SPACE, " ")
        val scheme = SCHEME.find(trimmed)?.groupValues?.get(1)?.lowercase()
        return when {
            scheme == null -> ImageReference.RepoFile(repoPath(noteFolder, trimmed))
            scheme == "http" || scheme == "https" -> ImageReference.Remote(trimmed)
            // A Windows path such as `C:\…` lands here too: the scheme pattern reads `C` as one, and
            // the answer — the app cannot read it — is the same.
            else -> ImageReference.External(trimmed)
        }
    }

    /**
     * The reference a note in [noteFolder] should write to reach [target], given as a repo-relative
     * path.
     *
     * The shortest form that still resolves: a sibling is just its name, anything else walks up with
     * `..`. Relative rather than repo-absolute so the note renders correctly in any Markdown reader,
     * not only in this app.
     */
    fun relative(target: String, noteFolder: String): String {
        val from = noteFolder.split('/').filter { it.isNotEmpty() }
        val to = target.split('/').filter { it.isNotEmpty() }
        val shared = from.zip(to).takeWhile { (a, b) -> a == b }.size
        val up = List(from.size - shared) { ".." }
        return (up + to.drop(shared)).joinToString("/").replace(" ", ENCODED_SPACE)
    }

    /**
     * The repo-relative path a note's own reference points at, with `.` and `..` folded away.
     *
     * `..` above the repository root is dropped rather than clamped to a parent that does not exist,
     * so a reference can never address anything outside the folder the user granted.
     */
    private fun repoPath(noteFolder: String, reference: String): String {
        val parts = mutableListOf<String>()
        val base = if (reference.startsWith("/")) "" else noteFolder
        base.split('/').filterTo(parts) { it.isNotEmpty() }

        reference.trimStart('/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return parts.joinToString("/")
    }
}
