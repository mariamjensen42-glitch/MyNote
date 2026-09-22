package com.cycling.mynote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cycling.mynote.core.model.EditorSettings
import com.cycling.mynote.core.model.ThemeMode
import com.cycling.mynote.data.share.ShareInbox
import com.cycling.mynote.domain.repository.SettingsRepository
import com.cycling.mynote.ui.navigation.MyNoteNavHost
import com.cycling.mynote.ui.theme.MyNoteTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shareInbox: ShareInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeShareIntent(intent)
        setContent { MyNoteRoot() }
    }

    /**
     * A second share while the app is already running arrives here rather than in [onCreate].
     *
     * The text is published to [ShareInbox] rather than navigated from directly: the navigation host
     * observes that inbox, so the capture screen is reached by the same path whether the app was
     * cold-started or already open.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    private fun consumeShareIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)

            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

            else -> null
        } ?: return

        // Android does not expose the sending package for ACTION_SEND, so the sheet says
        // "其他应用" rather than guessing at a name it cannot know.
        shareInbox.publish(text, sourceLabel = null)
    }
}

/** Applies the user's theme and font choice, then hands off to navigation. */
@Composable
private fun MyNoteRoot(viewModel: ThemeViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MyNoteTheme(darkTheme = dark, fontKey = settings.font.key) {
        Box(modifier = Modifier.fillMaxSize().background(MyNoteTheme.colors.bg)) {
            MyNoteNavHost()
        }
    }
}

/** Reads the one preference the theme itself depends on. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _settings = MutableStateFlow(EditorSettings())
    val settings: StateFlow<EditorSettings> = _settings.asStateFlow()

    init {
        settingsRepository.observeSettings()
            .onEach { _settings.value = it }
            .launchIn(viewModelScope)
    }
}
