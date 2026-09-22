package com.cycling.mynote.data.share

import androidx.compose.runtime.Immutable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Text handed to the app by another app's share sheet. */
@Immutable
data class PendingShare(val text: String, val sourceLabel: String?)

/**
 * The hand-off between an incoming share and the capture screen.
 *
 * A singleton rather than a navigation argument: shared text can be long, and encoding it into a
 * route would put the user's content in a URI where it is both size-limited and likely to end up in
 * logs. The activity publishes here, the capture screen's view model consumes it, and nothing
 * persists it beyond the session.
 */
@Singleton
class ShareInbox @Inject constructor() {

    private val _pending = MutableStateFlow<PendingShare?>(null)
    val pending: StateFlow<PendingShare?> = _pending.asStateFlow()

    fun publish(text: String, sourceLabel: String?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _pending.value = PendingShare(text = trimmed, sourceLabel = sourceLabel)
    }

    /** Clears the pending share; call once the capture screen has taken it. */
    fun consume() {
        _pending.value = null
    }
}
