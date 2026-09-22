package com.cycling.mynote.core.model

import androidx.compose.runtime.Immutable

/**
 * The Markdown folder the user granted access to.
 *
 * [pathLabel] is a best-effort human readable path built from the SAF document id; SAF gives no
 * portable absolute path, so this is what the design's `内部存储 / Documents / Notes` row shows.
 */
@Immutable
data class RepoInfo(
    val treeUri: String,
    val name: String,
    val pathLabel: String,
    val noteCount: Int,
    val totalBytes: Long,
    val lastOpenedAt: Long,
)

/** Aggregate numbers for the settings screen's `128 篇 · 24.6 MB` row. */
@Immutable
data class RepoStats(val noteCount: Int, val folderCount: Int, val totalBytes: Long)

/**
 * A previously opened repository, kept after the current grant is released so the onboarding
 * screen can offer `继续上次的仓库 · <name>`.
 */
@Immutable
data class LastRepo(val treeUri: String, val displayName: String)

enum class IndexState { IDLE, BUILDING, FAILED }

/**
 * State of the disposable search index.
 *
 * The index is what the settings screen calls "可删除、可重建": it only ever mirrors the folder, so
 * a failed or missing index degrades search and nothing else.
 */
@Immutable
data class IndexStatus(
    val state: IndexState = IndexState.IDLE,
    val builtAt: Long? = null,
    val indexedNoteCount: Int = 0,
    val message: String? = null,
)

/** How the index was refreshed, so the UI can explain a no-op rebuild. */
enum class IndexRefreshKind { FULL, INCREMENTAL, SKIPPED }
