package com.cycling.mynote.ui.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Something another screen asked the library to do. */
enum class LibraryIntent {
    /** Open the "new folder" dialog, so the folder gets a name and lands in the repository root. */
    NEW_FOLDER,
}

/**
 * One-shot requests for the library, sent from screens that cannot show its dialogs.
 *
 * The command palette is the reason this exists: it offers "新建文件夹", and creating a folder there
 * used to mean inventing a fixed name — a folder literally called 「新建文件夹」, which collided with
 * itself the second time and had no way to be named. Sending the request to the library instead means
 * the palette and the drawer run the same flow: a folder is always named, and always created in the
 * repository root.
 *
 * A [MutableSharedFlow] rather than state, because this is an event: re-delivering it on
 * recomposition would reopen the dialog the user just dismissed.
 */
@Singleton
class LibraryIntents @Inject constructor() {

    private val _requests = MutableSharedFlow<LibraryIntent>(extraBufferCapacity = 1)
    val requests: SharedFlow<LibraryIntent> = _requests

    fun request(intent: LibraryIntent) {
        _requests.tryEmit(intent)
    }
}
