package com.cycling.mynote.core.error

/**
 * Failures a repository can raise that the UI has a distinct thing to say about.
 *
 * Anything else is a genuine bug and is allowed to propagate; only these are mapped to user-facing
 * copy, so a `catch (e: Throwable)` in a view model cannot quietly swallow a programming error.
 */
sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** No repository folder has been granted yet. */
    class RepoNotConfigured : DataError("尚未选择笔记仓库")

    /** The persisted tree permission was revoked or the folder was deleted. */
    class RepoUnavailable(cause: Throwable? = null) :
        DataError("无法访问笔记仓库，可能已被移动或撤销授权", cause)

    /** A specific document could not be found. */
    class NoteNotFound(val noteId: String) : DataError("找不到笔记：$noteId")

    /** Reading or writing failed. */
    class Io(message: String, cause: Throwable? = null) : DataError(message, cause)

    /** A name collides with something that already exists. */
    class NameConflict(val name: String) : DataError("已存在同名项目：$name")
}
